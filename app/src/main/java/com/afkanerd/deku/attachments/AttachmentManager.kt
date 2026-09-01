package com.afkanerd.deku.attachments

import android.content.Context
import android.net.Uri
import android.util.Log
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.attachments.crypto.AttachmentCrypto
import com.afkanerd.deku.attachments.protocol.AckWindow
import com.afkanerd.deku.attachments.protocol.AckWindowCodec
import com.afkanerd.deku.attachments.protocol.AttachmentContext
import com.afkanerd.deku.attachments.protocol.AttachmentContextCodec
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.AttachmentManifestCodec
import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsFrameCodec
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.storage.AttachmentFileStore
import com.afkanerd.deku.attachments.storage.AttachmentKeyManager
import com.afkanerd.deku.attachments.storage.AttachmentOfferFragmentEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import com.afkanerd.deku.attachments.reliability.AttachmentAckWorker
import com.afkanerd.deku.attachments.reliability.AttachmentAckTimeoutWorker
import com.afkanerd.deku.attachments.transport.AttachmentSmsStatusReceiver
import com.afkanerd.deku.attachments.transport.BinaryRoute
import com.afkanerd.deku.attachments.transport.BinarySendResult
import com.afkanerd.deku.attachments.transport.BinaryTransport
import com.afkanerd.deku.attachments.transport.SmsBinaryTransport
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.deku.security.SecureSessionStatusResolver
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityKeyManager
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundDataSmsHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AttachmentManager private constructor(
    private val context: Context,
    private val transport: BinaryTransport,
) : InboundDataSmsHandler {
    private val database get() = Datastore.getDatastore(context)
    private val transfers get() = database.attachmentTransferDao()
    private val offers get() = database.attachmentOfferFragmentDao()
    private val keyManager = AttachmentKeyManager(context)
    private val files = AttachmentFileStore(context)

    data class MediaMetadata(
        val codec: String = "",
        val width: Int = 0,
        val height: Int = 0,
        val sampleRate: Int = 0,
        val durationMs: Long = 0,
    )

    suspend fun prepareFile(
        address: String,
        subscriptionId: Int,
        source: Uri,
        mediaType: AttachmentManifest.MediaType,
        mimeType: String,
        filename: String,
        originalSize: Long? = null,
        metadata: MediaMetadata = MediaMetadata(),
    ): AttachmentTransferEntity {
        check(SecureSessionStatusResolver.resolve(context, address) == SecureSessionStatus.SECURE_ESTABLISHED) {
            "A verified, established secure session is required for attachments"
        }
        val identityFingerprint = currentIdentityFingerprint(address)
        check(transfers.countActiveForAddress(address) < TransferLimits.MAX_ACTIVE_TRANSFERS_PER_CONTACT) {
            "Too many active transfers for this contact"
        }
        val transferId = TransferId.random()
        val outgoingDirectory = File(context.filesDir, "attachments/outgoing")
        check(outgoingDirectory.mkdirs() || outgoingDirectory.isDirectory)
        val staged = File(outgoingDirectory, "${transferId.toHex()}.bin")
        var encodedSize = 0L
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open attachment" }
            FileOutputStream(staged).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    encodedSize += count
                    require(encodedSize <= TransferLimits.MAX_TRANSFER_BYTES) {
                        "Attachment exceeds the configured ${TransferLimits.MAX_TRANSFER_BYTES}-byte limit"
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(encodedSize > 0) { "Empty attachments are not supported" }
        return try {
            prepareStagedFile(
                address, identityFingerprint, subscriptionId, staged, transferId, mediaType, mimeType,
                filename, originalSize ?: encodedSize, metadata,
            )
        } catch (error: Exception) {
            staged.delete()
            throw error
        }
    }

    private suspend fun prepareStagedFile(
        address: String,
        identityFingerprint: ByteArray,
        subscriptionId: Int,
        staged: File,
        transferId: TransferId,
        mediaType: AttachmentManifest.MediaType,
        mimeType: String,
        filename: String,
        originalSize: Long,
        metadata: MediaMetadata,
    ): AttachmentTransferEntity {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(staged).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val manifest = AttachmentManifest(
            transferId = transferId,
            mediaType = mediaType,
            mimeType = mimeType,
            filename = AttachmentManifestCodec.sanitizeFilename(filename),
            originalSize = originalSize,
            encodedSize = staged.length(),
            totalChunks = TransferLimits.chunkCount(staged.length()),
            sha256 = digest.digest(),
            codec = metadata.codec,
            width = metadata.width,
            height = metadata.height,
            sampleRate = metadata.sampleRate,
            durationMs = metadata.durationMs,
        )
        val key = AttachmentCrypto.generateMasterKey()
        val contextBytes = AttachmentContextCodec.encode(AttachmentContext(manifest, key))
        val ratchetOffer = try {
            EncryptionController.encryptBytes(context, address, contextBytes)
                ?: error("Double Ratchet did not produce an attachment offer")
        } finally {
            contextBytes.fill(0)
        }
        require(ratchetOffer.size <= TransferLimits.MAX_OFFER_FRAGMENTS * TransferLimits.FRAME_PAYLOAD_BYTES) {
            "Authenticated offer is too large"
        }
        val now = System.currentTimeMillis()
        val transfer = AttachmentTransferEntity(
            transferId = transferId.toHex(), address = address,
            identityFingerprint = identityFingerprint, subscriptionId = subscriptionId,
            outgoing = true, mediaType = mediaType.name, mimeType = manifest.mimeType,
            filename = manifest.filename, originalSize = originalSize, encodedSize = staged.length(),
            totalChunks = manifest.totalChunks, sha256 = manifest.sha256, codec = manifest.codec,
            width = manifest.width, height = manifest.height, sampleRate = manifest.sampleRate,
            durationMs = manifest.durationMs, status = AttachmentTransferStatus.WAITING_ACCEPT.name,
            sourcePath = staged.absolutePath, ratchetOffer = ratchetOffer,
            createdAt = now, updatedAt = now, expiresAt = now + TRANSFER_TTL_MILLIS,
        )
        try {
            keyManager.save(transferId, key)
            transfers.insert(transfer)
        } catch (error: Exception) {
            runCatching { keyManager.delete(transferId) }
            throw error
        } finally {
            key.fill(0)
        }
        AttachmentSmsStatusReceiver.enqueue(context, transfer.transferId)
        return transfer
    }

    suspend fun sendNext(transferIdHex: String): BinarySendResult = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex)
            ?: return@withLock BinarySendResult.Failed("Unknown attachment transfer")
        val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
            ?: return@withLock BinarySendResult.Failed("Invalid attachment status")
        if (status.terminal || status == AttachmentTransferStatus.PAUSED || !transfer.outgoing) {
            return@withLock BinarySendResult.Dispatched
        }
        if (!identityMatches(transfer) ||
            SecureSessionStatusResolver.resolve(context, transfer.address) != SecureSessionStatus.SECURE_ESTABLISHED) {
            transfers.update(transfer.copy(
                status = AttachmentTransferStatus.PAUSED.name,
                lastError = "Secure identity or session changed",
                updatedAt = System.currentTimeMillis(),
            ))
            return@withLock BinarySendResult.Failed("Secure identity or session changed")
        }
        val transferId = TransferId.fromHex(transfer.transferId)
        val route = BinaryRoute(transfer.address, transfer.subscriptionId)
        val offer = transfer.ratchetOffer
        if (status == AttachmentTransferStatus.WAITING_ACCEPT ||
            (status == AttachmentTransferStatus.RETRYING && offer != null &&
                transfer.controlSequence < offerFragmentCount(offer.size))
        ) {
            requireNotNull(offer)
            val total = offerFragmentCount(offer.size)
            val index = transfer.controlSequence
            if (index >= total) return@withLock BinarySendResult.Dispatched
            val start = index * TransferLimits.FRAME_PAYLOAD_BYTES
            val payload = offer.copyOfRange(start, minOf(offer.size, start + TransferLimits.FRAME_PAYLOAD_BYTES))
            return@withLock transport.send(
                SmsFrame(
                    packetType = SmsPacketType.TRANSFER_OFFER, transferId = transferId,
                    chunkIndex = index, totalChunks = total, payload = payload,
                ),
                route,
            )
        }
        if (status != AttachmentTransferStatus.SENDING && status != AttachmentTransferStatus.RETRYING) {
            return@withLock BinarySendResult.Dispatched
        }
        val sent = ChunkTracker.restore(transfer.totalChunks, transfer.sentBitmap)
        val acknowledged = ChunkTracker.restore(transfer.totalChunks, transfer.acknowledgedBitmap)
        val next = (0 until transfer.totalChunks).firstOrNull { it !in acknowledged && it !in sent }
            ?: return@withLock BinarySendResult.Dispatched
        val source = transfer.sourcePath?.let(::File)
            ?: return@withLock fail(transfer, "Attachment source path is missing")
        if (!source.isFile || source.length() != transfer.encodedSize) {
            return@withLock fail(transfer, "Attachment source is unavailable or changed")
        }
        val offset = next.toLong() * TransferLimits.CHUNK_PLAINTEXT_BYTES
        val length = minOf(TransferLimits.CHUNK_PLAINTEXT_BYTES.toLong(), transfer.encodedSize - offset).toInt()
        val plaintext = ByteArray(length)
        RandomAccessFile(source, "r").use { input ->
            input.seek(offset)
            input.readFully(plaintext)
        }
        val key = keyManager.load(transferId)
        val frame = try {
            AttachmentCrypto.encrypt(
                key, SmsPacketType.TRANSFER_CHUNK, transferId, next, transfer.totalChunks,
                plaintext, AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER,
            )
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
        transport.send(frame, route)
    }

    override suspend fun consume(
        context: Context,
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
    ): Boolean {
        if (payload.size < 2 || payload[0] != 0x44.toByte() || payload[1] != 0x4b.toByte()) return false
        val decoded = SmsFrameCodec.decode(payload)
        if (decoded !is SmsFrameCodec.DecodeResult.Success) return true
        val frame = decoded.frame
        lockFor(frame.transferId.toHex()).withLock {
            if (frame.packetType == SmsPacketType.TRANSFER_OFFER) {
                receiveOffer(address, subscriptionId, frame)
            } else {
                receiveAuthenticatedFrame(address, subscriptionId, frame)
            }
        }
        return true
    }

    private suspend fun receiveOffer(address: String, subscriptionId: Int, frame: SmsFrame) {
        val id = frame.transferId.toHex()
        if (transfers.get(id) != null) return
        offers.deleteExpired(System.currentTimeMillis() - OFFER_TTL_MILLIS)
        val current = offers.get(id)
        if (current.isEmpty() && offers.countContexts() >= TransferLimits.MAX_PENDING_TRANSFERS) return
        if (current.any { it.address != address || it.totalFragments != frame.totalChunks ||
                (it.fragmentIndex == frame.chunkIndex && !it.payload.contentEquals(frame.payload)) }) {
            offers.delete(id)
            return
        }
        offers.insert(AttachmentOfferFragmentEntity(
            transferId = id, fragmentIndex = frame.chunkIndex, totalFragments = frame.totalChunks,
            address = address, subscriptionId = subscriptionId, payload = frame.payload,
            receivedAt = System.currentTimeMillis(),
        ))
        val fragments = offers.get(id)
        if (fragments.size != frame.totalChunks || fragments.indices.any { fragments[it].fragmentIndex != it }) return
        val size = fragments.sumOf { it.payload.size }
        if (size > TransferLimits.MAX_OFFER_FRAGMENTS * TransferLimits.FRAME_PAYLOAD_BYTES) {
            offers.delete(id)
            return
        }
        if (SecureSessionStatusResolver.resolve(context, address) != SecureSessionStatus.SECURE_ESTABLISHED) {
            offers.delete(id)
            return
        }
        val encrypted = ByteArray(size)
        var offset = 0
        fragments.forEach {
            it.payload.copyInto(encrypted, offset)
            offset += it.payload.size
        }
        val attachmentContext = try {
            val plaintext = EncryptionController.decryptBytes(context, address, encrypted) ?: return
            try { AttachmentContextCodec.decode(plaintext) } finally { plaintext.fill(0) }
        } catch (error: Exception) {
            Log.w(TAG, "Rejected unauthenticated attachment offer", error)
            offers.delete(id)
            return
        } finally {
            encrypted.fill(0)
        }
        if (attachmentContext.manifest.transferId != frame.transferId ||
            transfers.countActiveForAddress(address) >= TransferLimits.MAX_ACTIVE_TRANSFERS_PER_CONTACT ||
            transfers.countPendingOffers() >= TransferLimits.MAX_PENDING_TRANSFERS) {
            attachmentContext.masterKey.fill(0)
            offers.delete(id)
            return
        }
        val manifest = attachmentContext.manifest
        val identityFingerprint = try { currentIdentityFingerprint(address) } catch (_: Exception) {
            attachmentContext.masterKey.fill(0)
            offers.delete(id)
            return
        }
        val now = System.currentTimeMillis()
        val transfer = AttachmentTransferEntity(
            transferId = id, address = address, identityFingerprint = identityFingerprint,
            subscriptionId = subscriptionId, outgoing = false,
            mediaType = manifest.mediaType.name, mimeType = manifest.mimeType, filename = manifest.filename,
            originalSize = manifest.originalSize, encodedSize = manifest.encodedSize,
            totalChunks = manifest.totalChunks, sha256 = manifest.sha256, codec = manifest.codec,
            width = manifest.width, height = manifest.height, sampleRate = manifest.sampleRate,
            durationMs = manifest.durationMs, status = AttachmentTransferStatus.OFFERED.name,
            smsReceived = frame.totalChunks, createdAt = now, updatedAt = now,
            expiresAt = now + TRANSFER_TTL_MILLIS,
        )
        try {
            keyManager.save(frame.transferId, attachmentContext.masterKey)
            transfers.insert(transfer)
            offers.delete(id)
        } catch (error: Exception) {
            runCatching { keyManager.delete(frame.transferId) }
            throw error
        } finally {
            attachmentContext.masterKey.fill(0)
        }
    }

    private suspend fun receiveAuthenticatedFrame(address: String, subscriptionId: Int, frame: SmsFrame) {
        val transfer = transfers.get(frame.transferId.toHex()) ?: return
        if (transfer.address != address || transfer.subscriptionId != subscriptionId) return
        if (!identityMatches(transfer) ||
            SecureSessionStatusResolver.resolve(context, address) != SecureSessionStatus.SECURE_ESTABLISHED) return
        val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull() ?: return
        if (status.terminal && !(status == AttachmentTransferStatus.COMPLETED &&
                !transfer.outgoing && (frame.packetType == SmsPacketType.NACK ||
                    frame.packetType == SmsPacketType.TRANSFER_COMPLETE))) return
        val key = try { keyManager.load(frame.transferId) } catch (_: Exception) { return }
        val direction = if (transfer.outgoing) AttachmentCrypto.Direction.RESPONDER_TO_INITIATOR
            else AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER
        val decrypted = try { AttachmentCrypto.decrypt(key, frame, direction) } finally { key.fill(0) }
        val plaintext = (decrypted as? AttachmentCrypto.DecryptResult.Success)?.plaintext ?: return
        try {
            when (frame.packetType) {
                SmsPacketType.TRANSFER_ACCEPT -> if (transfer.outgoing && status == AttachmentTransferStatus.WAITING_ACCEPT) {
                    require(plaintext.isEmpty())
                    transfers.update(transfer.copy(
                        status = AttachmentTransferStatus.SENDING.name,
                        updatedAt = System.currentTimeMillis(), smsReceived = transfer.smsReceived + 1,
                    ))
                    AttachmentSmsStatusReceiver.enqueue(context, transfer.transferId)
                }
                SmsPacketType.TRANSFER_REJECT, SmsPacketType.CANCEL -> cancelInternal(transfer)
                SmsPacketType.TRANSFER_CHUNK -> receiveChunk(transfer, frame, plaintext)
                SmsPacketType.ACK -> if (transfer.outgoing) receiveAck(transfer, plaintext)
                SmsPacketType.NACK -> if (transfer.outgoing) receiveAck(transfer, plaintext)
                    else receiveAckRequest(transfer, plaintext)
                SmsPacketType.TRANSFER_COMPLETE -> if (transfer.outgoing) completeSender(transfer)
                    else finalizeReceiver(transfer)
                SmsPacketType.ERROR -> fail(transfer, "Remote endpoint reported a transfer error")
                SmsPacketType.TRANSFER_OFFER -> Unit
            }
        } catch (error: Exception) {
            Log.w(TAG, "Authenticated attachment frame was invalid", error)
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun accept(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        check(!transfer.outgoing && transfer.status == AttachmentTransferStatus.OFFERED.name)
        val manifest = transfer.toManifest()
        val partial = files.createPartial(manifest)
        val updated = transfer.copy(
            status = AttachmentTransferStatus.RECEIVING.name,
            partialPath = partial.absolutePath,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(updated)
        sendControl(updated, SmsPacketType.TRANSFER_ACCEPT, ByteArray(0), fixedIndex = 0)
    }

    suspend fun reject(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        if (transfer.status == AttachmentTransferStatus.OFFERED.name) {
            sendControl(transfer, SmsPacketType.TRANSFER_REJECT, ByteArray(0), fixedIndex = 0)
        }
        cancelInternal(transfer)
    }

    suspend fun cancel(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        if (!AttachmentTransferStatus.valueOf(transfer.status).terminal) {
            sendControl(transfer, SmsPacketType.CANCEL, ByteArray(0))
            cancelInternal(transfer)
        }
    }

    suspend fun resumePending() {
        cleanupExpired()
        val resumable = listOf(
            AttachmentTransferStatus.WAITING_ACCEPT.name,
            AttachmentTransferStatus.SENDING.name,
            AttachmentTransferStatus.RETRYING.name,
        )
        transfers.getByStatuses(resumable).filter { it.outgoing }.forEach {
            AttachmentSmsStatusReceiver.enqueue(context, it.transferId)
        }
    }

    private suspend fun cleanupExpired() {
        transfers.getExpired(System.currentTimeMillis()).forEach { transfer ->
            runCatching { files.deletePartial(TransferId.fromHex(transfer.transferId)) }
            runCatching { transfer.sourcePath?.let(::File)?.delete() }
            runCatching { keyManager.delete(TransferId.fromHex(transfer.transferId)) }
            if (!AttachmentTransferStatus.valueOf(transfer.status).terminal) {
                transfers.update(transfer.copy(
                    status = AttachmentTransferStatus.CANCELLED.name,
                    lastError = "Transfer expired",
                    updatedAt = System.currentTimeMillis(),
                ))
            }
        }
    }

    suspend fun sendPendingAcknowledgement(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock
        if (transfer.outgoing || transfer.status != AttachmentTransferStatus.RECEIVING.name) return@withLock
        val tracker = ChunkTracker.restore(transfer.totalChunks, transfer.receivedBitmap)
        val highest = (0 until transfer.totalChunks).lastOrNull { it in tracker } ?: return@withLock
        sendAckWindow(transfer, tracker, (highest / TransferLimits.ACK_WINDOW_BITS) * TransferLimits.ACK_WINDOW_BITS)
    }

    suspend fun requestAcknowledgement(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock
        if (!transfer.outgoing || AttachmentTransferStatus.valueOf(transfer.status).terminal) return@withLock
        val acknowledged = ChunkTracker.restore(transfer.totalChunks, transfer.acknowledgedBitmap)
        val firstUnknown = (0 until transfer.totalChunks).firstOrNull { it !in acknowledged } ?: 0
        val base = (firstUnknown / TransferLimits.ACK_WINDOW_BITS) * TransferLimits.ACK_WINDOW_BITS
        sendControl(transfer, SmsPacketType.NACK, byteArrayOf((base ushr 8).toByte(), base.toByte()))
    }

    suspend fun finalizeSentCompletion(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock
        if (transfer.outgoing && transfer.status == AttachmentTransferStatus.COMPLETED.name) {
            keyManager.delete(TransferId.fromHex(transferIdHex))
        }
    }

    private suspend fun receiveChunk(transfer: AttachmentTransferEntity, frame: SmsFrame, plaintext: ByteArray) {
        if (transfer.outgoing || transfer.status != AttachmentTransferStatus.RECEIVING.name ||
            frame.totalChunks != transfer.totalChunks) return
        val tracker = ChunkTracker.restore(transfer.totalChunks, transfer.receivedBitmap)
        if (frame.chunkIndex !in tracker) {
            val partial = transfer.partialPath?.let(::File) ?: return
            files.writeVerifiedChunk(partial, transfer.toManifest(), frame.chunkIndex, plaintext)
            tracker.mark(frame.chunkIndex)
        }
        var updated = transfer.copy(
            receivedBitmap = tracker.serialize(), smsReceived = transfer.smsReceived + 1,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(updated)
        if (tracker.isComplete()) {
            updated = updated.copy(status = AttachmentTransferStatus.VERIFYING.name)
            transfers.update(updated)
            try {
                val completed = files.verifyAndCommit(updated.toManifest())
                updated = updated.copy(
                    status = AttachmentTransferStatus.COMPLETED.name,
                    completedPath = completed.absolutePath,
                    partialPath = null,
                    updatedAt = System.currentTimeMillis(),
                )
                transfers.update(updated)
                sendControl(updated, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0))
            } catch (error: Exception) {
                fail(updated, "Final attachment verification failed")
            }
        } else if (tracker.count() % ACK_EVERY_CHUNKS == 0) {
            sendAck(updated, tracker)
        }
        if (!tracker.isComplete()) AttachmentAckWorker.enqueue(context, transfer.transferId)
    }

    private suspend fun sendAck(transfer: AttachmentTransferEntity, tracker: ChunkTracker) {
        val highest = (0 until transfer.totalChunks).lastOrNull { it in tracker } ?: return
        val base = (highest / TransferLimits.ACK_WINDOW_BITS) * TransferLimits.ACK_WINDOW_BITS
        sendAckWindow(transfer, tracker, base)
    }

    private suspend fun sendAckWindow(
        transfer: AttachmentTransferEntity,
        tracker: ChunkTracker,
        base: Int,
    ) {
        if (base !in 0 until transfer.totalChunks) return
        val count = minOf(TransferLimits.ACK_WINDOW_BITS, transfer.totalChunks - base)
        sendControl(transfer, SmsPacketType.ACK, AckWindowCodec.encode(AckWindow(base, count, tracker.window(base, count))))
    }

    private suspend fun receiveAckRequest(transfer: AttachmentTransferEntity, plaintext: ByteArray) {
        if (plaintext.size != 2) return
        if (transfer.status == AttachmentTransferStatus.COMPLETED.name) {
            sendControl(transfer, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0))
            return
        }
        if (transfer.status != AttachmentTransferStatus.RECEIVING.name) return
        val base = ((plaintext[0].toInt() and 0xff) shl 8) or (plaintext[1].toInt() and 0xff)
        val tracker = ChunkTracker.restore(transfer.totalChunks, transfer.receivedBitmap)
        sendAckWindow(transfer, tracker, base)
    }

    private suspend fun receiveAck(transfer: AttachmentTransferEntity, plaintext: ByteArray) {
        val window = AckWindowCodec.decode(plaintext)
        if (window.baseIndex + window.bitCount > transfer.totalChunks) return
        val acked = ChunkTracker.restore(transfer.totalChunks, transfer.acknowledgedBitmap)
        val sent = ChunkTracker.restore(transfer.totalChunks, transfer.sentBitmap)
        repeat(window.bitCount) { relative ->
            val index = window.baseIndex + relative
            if (window.received[relative]) acked.mark(index) else sent.clear(index)
        }
        val updated = transfer.copy(
            acknowledgedBitmap = acked.serialize(),
            sentBitmap = sent.serialize(),
            smsReceived = transfer.smsReceived + 1,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(updated)
        if (!acked.isComplete()) AttachmentSmsStatusReceiver.enqueue(context, transfer.transferId)
        AttachmentAckTimeoutWorker.enqueue(context, transfer.transferId)
    }

    private suspend fun sendControl(
        transfer: AttachmentTransferEntity,
        type: SmsPacketType,
        plaintext: ByteArray,
        fixedIndex: Int? = null,
    ): BinarySendResult {
        val id = TransferId.fromHex(transfer.transferId)
        val index = fixedIndex ?: transfer.controlSequence
        if (fixedIndex == null) {
            transfers.update(transfer.copy(controlSequence = index + 1, updatedAt = System.currentTimeMillis()))
        }
        val key = keyManager.load(id)
        val direction = if (transfer.outgoing) AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER
            else AttachmentCrypto.Direction.RESPONDER_TO_INITIATOR
        val frame = try {
            AttachmentCrypto.encrypt(
                key, type, id, index, transfer.totalChunks, plaintext, direction,
            )
        } finally { key.fill(0) }
        return transport.send(frame, BinaryRoute(transfer.address, transfer.subscriptionId))
    }

    private suspend fun completeSender(transfer: AttachmentTransferEntity) {
        sendControl(transfer, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0))
        transfers.update(transfer.copy(
            status = AttachmentTransferStatus.COMPLETED.name,
            smsReceived = transfer.smsReceived + 1,
            updatedAt = System.currentTimeMillis(),
        ))
        transfer.sourcePath?.let(::File)?.delete()
    }

    private suspend fun finalizeReceiver(transfer: AttachmentTransferEntity) {
        if (transfer.status != AttachmentTransferStatus.COMPLETED.name) return
        keyManager.delete(TransferId.fromHex(transfer.transferId))
    }

    private suspend fun cancelInternal(transfer: AttachmentTransferEntity) {
        transfers.update(transfer.copy(
            status = AttachmentTransferStatus.CANCELLED.name,
            updatedAt = System.currentTimeMillis(),
        ))
        files.deletePartial(TransferId.fromHex(transfer.transferId))
        transfer.sourcePath?.let(::File)?.delete()
        keyManager.delete(TransferId.fromHex(transfer.transferId))
    }

    private suspend fun fail(transfer: AttachmentTransferEntity, reason: String): BinarySendResult.Failed {
        transfers.update(transfer.copy(
            status = AttachmentTransferStatus.FAILED.name,
            lastError = reason,
            updatedAt = System.currentTimeMillis(),
        ))
        return BinarySendResult.Failed(reason)
    }

    private fun AttachmentTransferEntity.toManifest(): AttachmentManifest = AttachmentManifest(
        transferId = TransferId.fromHex(transferId),
        mediaType = AttachmentManifest.MediaType.valueOf(mediaType),
        mimeType = mimeType,
        filename = filename,
        originalSize = originalSize,
        encodedSize = encodedSize,
        totalChunks = totalChunks,
        sha256 = sha256,
        codec = codec,
        width = width,
        height = height,
        sampleRate = sampleRate,
        durationMs = durationMs,
    )

    private fun offerFragmentCount(size: Int): Int =
        (size + TransferLimits.FRAME_PAYLOAD_BYTES - 1) / TransferLimits.FRAME_PAYLOAD_BYTES

    private fun lockFor(transferId: String): Mutex = locks.computeIfAbsent(transferId) { Mutex() }

    private suspend fun currentIdentityFingerprint(address: String): ByteArray {
        val identity = IdentityKeyManager.getContactIdentity(context, address)
        val publicKey = requireNotNull(identity.publicKey) {
            "Attachments require a signed identity; renew this legacy secure session"
        }
        return MessageDigest.getInstance("SHA-256").digest(publicKey)
    }

    private suspend fun identityMatches(transfer: AttachmentTransferEntity): Boolean = try {
        MessageDigest.isEqual(transfer.identityFingerprint, currentIdentityFingerprint(transfer.address))
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val TAG = "AttachmentManager"
        private const val ACK_EVERY_CHUNKS = 16
        private const val OFFER_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val TRANSFER_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private val locks = ConcurrentHashMap<String, Mutex>()
        @Volatile private var instance: AttachmentManager? = null

        fun get(context: Context): AttachmentManager = instance ?: synchronized(this) {
            instance ?: AttachmentManager(
                context.applicationContext,
                SmsBinaryTransport(context.applicationContext),
            ).also { instance = it }
        }
    }
}
