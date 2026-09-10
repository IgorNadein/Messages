package com.afkanerd.deku.attachments

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.MainActivity
import com.afkanerd.deku.attachments.crypto.AttachmentCrypto
import com.afkanerd.deku.attachments.crypto.MmsMediaContainer
import com.afkanerd.deku.attachments.crypto.CloudMediaContainer
import com.afkanerd.deku.attachments.cloud.CloudObjectDownloader
import com.afkanerd.deku.attachments.cloud.CloudObjectReference
import com.afkanerd.deku.attachments.cloud.CloudObjectStoreFactory
import com.afkanerd.deku.attachments.cloud.CloudProvider
import com.afkanerd.deku.attachments.cloud.CloudStorageConfigStore
import com.afkanerd.deku.attachments.cloud.CloudDownloadWorker
import com.afkanerd.deku.attachments.cloud.CloudCleanupWorker
import com.afkanerd.deku.attachments.cloud.CloudUploadWorker
import com.afkanerd.deku.attachments.protocol.AckWindow
import com.afkanerd.deku.attachments.protocol.AckWindowCodec
import com.afkanerd.deku.attachments.protocol.AttachmentContext
import com.afkanerd.deku.attachments.protocol.AttachmentContextCodec
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.AttachmentManifestCodec
import com.afkanerd.deku.attachments.protocol.AttachmentProtocolFlags
import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsFrameCodec
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.protocol.RemoteAttachmentSource
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
import com.afkanerd.deku.attachments.transport.CompatibleTextSmsTransport
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.deku.security.SecureSessionStatusResolver
import com.afkanerd.deku.security.SecureChannelId
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityKeyManager
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundDataSmsHandler
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.makeE16PhoneNumber
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getThreadId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.notify
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendMms
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.transport.INTERNAL_MEDIA_MIME_TYPE

class AttachmentManager private constructor(
    private val context: Context,
    private val transport: BinaryTransport,
    private val compatibleTextTransport: BinaryTransport,
) : InboundDataSmsHandler {
    private val database get() = Datastore.getDatastore(context)
    private val transfers get() = database.attachmentTransferDao()
    private val offers get() = database.attachmentOfferFragmentDao()
    private val keyManager = AttachmentKeyManager(context)
    private val files = AttachmentFileStore(context)
    private val cloudStorage = CloudStorageConfigStore(context)
    private val cloudDownloader = CloudObjectDownloader()

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
        protection: AttachmentProtection = AttachmentProtection.SECURE,
        wireTransport: AttachmentWireTransport = AttachmentWireTransport.DATA_SMS,
    ): AttachmentTransferEntity {
        val normalizedAddress = context.makeE16PhoneNumber(address).ifBlank { address.trim() }
        require(normalizedAddress.isNotBlank()) { "Attachment recipient is required" }
        if (protection == AttachmentProtection.SECURE) {
            check(SecureSessionStatusResolver.resolve(
                context,
                normalizedAddress,
                subscriptionId.toLong(),
            ) == SecureSessionStatus.SECURE_ESTABLISHED) {
                "A verified, established secure session is required for protected attachments"
            }
        }
        val identityFingerprint = if (protection == AttachmentProtection.SECURE) {
            currentIdentityFingerprint(normalizedAddress, subscriptionId)
        } else {
            ByteArray(0)
        }
        check(transfers.countActiveForAddress(normalizedAddress) < TransferLimits.MAX_ACTIVE_TRANSFERS_PER_CONTACT) {
            "Too many active transfers for this contact"
        }
        val transferId = TransferId.random()
        val outgoingDirectory = File(context.filesDir, "attachments/outgoing")
        check(outgoingDirectory.mkdirs() || outgoingDirectory.isDirectory)
        val staged = File(outgoingDirectory, "${transferId.toHex()}.bin")
        val maxTransferBytes = when(wireTransport) {
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS -> TransferLimits.MAX_TRANSFER_BYTES
            AttachmentWireTransport.MMS -> TransferLimits.MAX_MMS_TRANSFER_BYTES
            AttachmentWireTransport.CLOUD_STORAGE -> TransferLimits.MAX_MMS_TRANSFER_BYTES
        }
        var encodedSize = 0L
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open attachment" }
            FileOutputStream(staged).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    encodedSize += count
                    require(encodedSize <= maxTransferBytes) {
                        "Attachment exceeds the configured $maxTransferBytes-byte limit"
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(encodedSize > 0) { "Empty attachments are not supported" }
        return try {
            prepareStagedFile(
                normalizedAddress, identityFingerprint, subscriptionId, staged, transferId, mediaType, mimeType,
                filename, originalSize ?: encodedSize, metadata, protection, wireTransport,
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
        protection: AttachmentProtection,
        wireTransport: AttachmentWireTransport,
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
        val chunkPlaintextBytes = when(wireTransport) {
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS -> TransferLimits.CHUNK_PLAINTEXT_BYTES
            AttachmentWireTransport.MMS -> TransferLimits.MMS_PART_PLAINTEXT_BYTES
            AttachmentWireTransport.CLOUD_STORAGE -> TransferLimits.MMS_PART_PLAINTEXT_BYTES
        }
        val totalChunks = when(wireTransport) {
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS -> TransferLimits.chunkCount(staged.length())
            AttachmentWireTransport.MMS -> TransferLimits.mmsPartCount(staged.length())
            AttachmentWireTransport.CLOUD_STORAGE -> 1
        }
        val manifest = AttachmentManifest(
            transferId = transferId,
            mediaType = mediaType,
            mimeType = mimeType,
            filename = AttachmentManifestCodec.sanitizeFilename(filename),
            originalSize = originalSize,
            encodedSize = staged.length(),
            totalChunks = totalChunks,
            sha256 = digest.digest(),
            codec = metadata.codec,
            width = metadata.width,
            height = metadata.height,
            sampleRate = metadata.sampleRate,
            durationMs = metadata.durationMs,
        )
        val key = AttachmentCrypto.generateMasterKey()
        if(wireTransport == AttachmentWireTransport.CLOUD_STORAGE) {
            return prepareCloudTransfer(
                address = address,
                identityFingerprint = identityFingerprint,
                subscriptionId = subscriptionId,
                staged = staged,
                manifest = manifest,
                protection = protection,
                key = key,
            )
        }
        val contextBytes = try {
            AttachmentContextCodec.encode(
                AttachmentContext(
                    manifest,
                    key,
                    wireTransport,
                    chunkPlaintextBytes,
                    null,
                )
            )
        } catch(error: Exception) {
            key.fill(0)
            throw error
        }
        val offerBytes = try {
            if (protection == AttachmentProtection.SECURE) {
                EncryptionController.encryptBytes(
                    context,
                    SecureChannelId.storageAddress(address, subscriptionId.toLong()),
                    contextBytes,
                ) ?: error("Double Ratchet did not produce an attachment offer")
            } else {
                contextBytes.copyOf()
            }
        } catch(error: Exception) {
            key.fill(0)
            throw error
        } finally {
            contextBytes.fill(0)
        }
        if(offerBytes.size > TransferLimits.MAX_OFFER_FRAGMENTS * TransferLimits.FRAME_PAYLOAD_BYTES) {
            key.fill(0)
            error("Authenticated offer is too large")
        }
        val now = System.currentTimeMillis()
        val transfer = AttachmentTransferEntity(
            transferId = transferId.toHex(), address = address,
            identityFingerprint = identityFingerprint, subscriptionId = subscriptionId,
            outgoing = true, protection = protection.name,
            transport = wireTransport.name, chunkPlaintextBytes = chunkPlaintextBytes,
            mediaType = mediaType.name, mimeType = manifest.mimeType,
            filename = manifest.filename, originalSize = originalSize, encodedSize = staged.length(),
            totalChunks = manifest.totalChunks, sha256 = manifest.sha256, codec = manifest.codec,
            width = manifest.width, height = manifest.height, sampleRate = manifest.sampleRate,
            durationMs = manifest.durationMs, status = AttachmentTransferStatus.WAITING_ACCEPT.name,
            sourcePath = staged.absolutePath, ratchetOffer = offerBytes,
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

    private suspend fun prepareCloudTransfer(
        address: String,
        identityFingerprint: ByteArray,
        subscriptionId: Int,
        staged: File,
        manifest: AttachmentManifest,
        protection: AttachmentProtection,
        key: ByteArray,
    ): AttachmentTransferEntity {
        val now = System.currentTimeMillis()
        val transfer = AttachmentTransferEntity(
            transferId = manifest.transferId.toHex(),
            address = address,
            identityFingerprint = identityFingerprint,
            subscriptionId = subscriptionId,
            outgoing = true,
            protection = protection.name,
            transport = AttachmentWireTransport.CLOUD_STORAGE.name,
            chunkPlaintextBytes = TransferLimits.MMS_PART_PLAINTEXT_BYTES,
            mediaType = manifest.mediaType.name,
            mimeType = manifest.mimeType,
            filename = manifest.filename,
            originalSize = manifest.originalSize,
            encodedSize = manifest.encodedSize,
            totalChunks = 1,
            sha256 = manifest.sha256,
            codec = manifest.codec,
            width = manifest.width,
            height = manifest.height,
            sampleRate = manifest.sampleRate,
            durationMs = manifest.durationMs,
            status = AttachmentTransferStatus.PREPARING.name,
            sourcePath = staged.absolutePath,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + TRANSFER_TTL_MILLIS,
        )
        try {
            keyManager.save(manifest.transferId, key)
            transfers.insert(transfer)
        } catch(error: Exception) {
            runCatching { keyManager.delete(manifest.transferId) }
            throw error
        } finally {
            key.fill(0)
        }
        CloudUploadWorker.enqueue(context, transfer.transferId)
        return transfer
    }

    private suspend fun uploadCloudObject(
        transferId: TransferId,
        source: File,
        key: ByteArray,
        flags: Int,
    ): UploadedCloudSource {
        val credentials = requireNotNull(cloudStorage.credentials()) {
            "Internet storage is not configured"
        }
        val uploadDirectory = File(context.cacheDir, "cloud_uploads")
        check(uploadDirectory.mkdirs() || uploadDirectory.isDirectory)
        val encrypted = File(uploadDirectory, "${transferId.toHex()}.dkc")
        var uploadedReference: CloudObjectReference? = null
        return try {
            CloudMediaContainer.encrypt(key, flags, transferId, source, encrypted)
            val reference = CloudObjectStoreFactory.create(credentials).upload(
                encrypted,
                "${transferId.toHex()}.dkc",
            )
            uploadedReference = reference
            UploadedCloudSource(
                RemoteAttachmentSource(reference.provider.wireCode, reference.locator),
                reference.deleteLocator,
            )
        } catch(error: Exception) {
            uploadedReference?.deleteLocator?.let { locator ->
                runCatching { CloudObjectStoreFactory.create(credentials).delete(locator) }
            }
            throw error
        } finally {
            encrypted.delete()
        }
    }

    suspend fun resumeCloudUpload(transferIdHex: String): Boolean =
        lockFor(transferIdHex).withLock {
            var transfer = transfers.get(transferIdHex) ?: return@withLock true
            if(!transfer.outgoing || transfer.transport != AttachmentWireTransport.CLOUD_STORAGE.name ||
                transfer.status != AttachmentTransferStatus.PREPARING.name
            ) return@withLock true
            if(transfer.expiresAt <= System.currentTimeMillis()) {
                cancelInternal(transfer)
                return@withLock true
            }
            if(transfer.isSecure() && (!identityMatches(transfer) ||
                SecureSessionStatusResolver.resolve(
                    context,
                    transfer.address,
                    transfer.subscriptionId.toLong(),
                ) != SecureSessionStatus.SECURE_ESTABLISHED)
            ) {
                fail(transfer, "Secure identity or session changed")
                transfer.sourcePath?.let(::File)?.delete()
                if(transfer.remoteDeleteLocator != null) {
                    CloudCleanupWorker.enqueue(context, transfer.transferId)
                }
                keyManager.delete(TransferId.fromHex(transfer.transferId))
                return@withLock true
            }
            val source = transfer.sourcePath?.let(::File)
            if(source == null || !source.isFile || source.length() != transfer.encodedSize) {
                fail(transfer, "Attachment source is unavailable or changed")
                if(transfer.remoteDeleteLocator != null) {
                    CloudCleanupWorker.enqueue(context, transfer.transferId)
                }
                keyManager.delete(TransferId.fromHex(transfer.transferId))
                return@withLock true
            }
            val transferId = TransferId.fromHex(transfer.transferId)
            val key = try {
                keyManager.load(transferId)
            } catch(error: CancellationException) {
                throw error
            } catch(error: Exception) {
                fail(transfer, error.message ?: "Attachment key is missing")
                if(transfer.remoteDeleteLocator != null) {
                    CloudCleanupWorker.enqueue(context, transfer.transferId)
                }
                return@withLock true
            }
            try {
                val uploaded = if(transfer.remoteProvider != null && transfer.remoteLocator != null) {
                    val provider = CloudProvider.valueOf(transfer.remoteProvider)
                    UploadedCloudSource(
                        RemoteAttachmentSource(provider.wireCode, transfer.remoteLocator),
                        transfer.remoteDeleteLocator,
                    )
                } else {
                    uploadCloudObject(
                        transferId,
                        source,
                        key,
                        transfer.protocolFlags(),
                    ).also { result ->
                        transfer = transfer.copy(
                            remoteProvider = CloudProvider.fromWireCode(
                                result.remoteSource.providerCode
                            )?.name,
                            remoteLocator = result.remoteSource.locator,
                            remoteDeleteLocator = result.deleteLocator,
                            updatedAt = System.currentTimeMillis(),
                        )
                        transfers.update(transfer)
                    }
                }
                val contextBytes = AttachmentContextCodec.encode(
                    AttachmentContext(
                        manifest = transfer.toManifest(),
                        masterKey = key,
                        transport = AttachmentWireTransport.CLOUD_STORAGE,
                        chunkPlaintextBytes = transfer.chunkPlaintextBytes,
                        remoteSource = uploaded.remoteSource,
                    )
                )
                val offer = try {
                    if(transfer.isSecure()) {
                        EncryptionController.encryptBytes(
                            context,
                            SecureChannelId.storageAddress(
                                transfer.address,
                                transfer.subscriptionId.toLong(),
                            ),
                            contextBytes,
                        ) ?: error("Double Ratchet did not produce an attachment offer")
                    } else {
                        contextBytes.copyOf()
                    }
                } finally {
                    contextBytes.fill(0)
                }
                require(offer.size <=
                    TransferLimits.MAX_OFFER_FRAGMENTS * TransferLimits.FRAME_PAYLOAD_BYTES
                ) { "Authenticated offer is too large" }
                transfer = transfer.copy(
                    ratchetOffer = offer,
                    status = AttachmentTransferStatus.WAITING_ACCEPT.name,
                    retryCount = 0,
                    lastError = null,
                    updatedAt = System.currentTimeMillis(),
                )
                transfers.update(transfer)
                AttachmentSmsStatusReceiver.enqueue(context, transfer.transferId)
                true
            } catch(error: CancellationException) {
                throw error
            } catch(error: Exception) {
                val retry = transfer.retryCount + 1
                val exhausted = retry >= MAX_CLOUD_RETRIES
                transfer = transfer.copy(
                    status = if(exhausted) AttachmentTransferStatus.FAILED.name
                        else AttachmentTransferStatus.PREPARING.name,
                    retryCount = retry,
                    lastError = error.message ?: "Cloud attachment upload failed",
                    updatedAt = System.currentTimeMillis(),
                )
                transfers.update(transfer)
                if(exhausted) {
                    transfer.sourcePath?.let(::File)?.delete()
                    if(transfer.remoteDeleteLocator != null) {
                        CloudCleanupWorker.enqueue(context, transfer.transferId)
                    }
                    keyManager.delete(transferId)
                }
                exhausted
            } finally {
                key.fill(0)
            }
        }

    private data class UploadedCloudSource(
        val remoteSource: RemoteAttachmentSource,
        val deleteLocator: String?,
    )

    suspend fun sendNext(transferIdHex: String): BinarySendResult = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex)
            ?: return@withLock BinarySendResult.Failed("Unknown attachment transfer")
        val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
            ?: return@withLock BinarySendResult.Failed("Invalid attachment status")
        if (status.terminal || status == AttachmentTransferStatus.PAUSED || !transfer.outgoing) {
            return@withLock BinarySendResult.Dispatched
        }
        if (transfer.isSecure() && (!identityMatches(transfer) ||
            SecureSessionStatusResolver.resolve(
                context,
                transfer.address,
                transfer.subscriptionId.toLong(),
            ) != SecureSessionStatus.SECURE_ESTABLISHED)) {
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
            return@withLock transportFor(transfer).send(
                SmsFrame(
                    packetType = SmsPacketType.TRANSFER_OFFER, transferId = transferId,
                    flags = transfer.protocolFlags(), chunkIndex = index,
                    totalChunks = total, payload = payload,
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
        if(transfer.transport == AttachmentWireTransport.MMS.name) {
            return@withLock sendMmsPart(transfer, transferId, source, next)
        }
        if(transfer.transport == AttachmentWireTransport.CLOUD_STORAGE.name) {
            sent.mark(next)
            transfers.update(transfer.copy(
                sentBitmap = sent.serialize(),
                updatedAt = System.currentTimeMillis(),
            ))
            AttachmentAckTimeoutWorker.enqueue(context, transfer.transferId)
            return@withLock BinarySendResult.Dispatched
        }
        if(transfer.transport != AttachmentWireTransport.DATA_SMS.name &&
            transfer.transport != AttachmentWireTransport.STANDARD_SMS.name
        ) {
            return@withLock fail(transfer, "Unsupported attachment wire transport")
        }
        val offset = next.toLong() * transfer.chunkPlaintextBytes
        val length = minOf(transfer.chunkPlaintextBytes.toLong(), transfer.encodedSize - offset).toInt()
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
                flags = transfer.protocolFlags(),
            )
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
        transportFor(transfer).send(frame, route)
    }

    private suspend fun sendMmsPart(
        transfer: AttachmentTransferEntity,
        transferId: TransferId,
        source: File,
        index: Int,
    ): BinarySendResult {
        val offset = index.toLong() * transfer.chunkPlaintextBytes
        val length = minOf(
            transfer.chunkPlaintextBytes.toLong(),
            transfer.encodedSize - offset,
        ).toInt()
        if(length <= 0) return fail(transfer, "Invalid MMS attachment part length")
        val plaintext = ByteArray(length)
        RandomAccessFile(source, "r").use { input ->
            input.seek(offset)
            input.readFully(plaintext)
        }
        val key = keyManager.load(transferId)
        val container = try {
            MmsMediaContainer.encrypt(
                masterKey = key,
                flags = transfer.protocolFlags(),
                transferId = transferId,
                partIndex = index,
                totalParts = transfer.totalChunks,
                plaintext = plaintext,
            )
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
        val partDirectory = File(context.cacheDir, "mms_parts")
        check(partDirectory.mkdirs() || partDirectory.isDirectory)
        val partFile = File(partDirectory, "${transfer.transferId}-$index.dmm")
        require(partFile.parentFile?.canonicalFile == partDirectory.canonicalFile)
        return try {
            FileOutputStream(partFile).use { output ->
                output.write(container)
                output.fd.sync()
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                partFile,
            )
            val queued = context.sendMms(
                text = "",
                addresses = listOf(transfer.address),
                threadId = context.getThreadId(listOf(transfer.address)),
                subscriptionId = transfer.subscriptionId.toLong(),
                contentUri = contentUri,
                filename = "${transfer.transferId}-${index + 1}-of-${transfer.totalChunks}.dmm",
                mimeType = INTERNAL_MEDIA_MIME_TYPE,
                verifiedSecurePayload = transfer.isSecure(),
                internalTransport = true,
                internalTransferId = transfer.transferId,
                internalPartIndex = index,
            )
            if(queued == null) return fail(transfer, "MMS attachment part was not queued")
            // A modem callback, not queue admission, advances the bitmap. The
            // fallback makes a lost callback retry the same idempotent part.
            AttachmentSmsStatusReceiver.enqueue(context, transfer.transferId, MMS_CALLBACK_TIMEOUT_MILLIS)
            BinarySendResult.Dispatched
        } catch(error: CancellationException) {
            throw error
        } catch(error: Exception) {
            fail(transfer, error.message ?: "Unable to queue MMS attachment part")
        } finally {
            container.fill(0)
            partFile.delete()
        }
    }

    suspend fun onMmsPartSent(
        transferIdHex: String,
        partIndex: Int,
        successful: Boolean,
        resultCode: Int,
    ): Boolean = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock false
        if(!transfer.outgoing || transfer.transport != AttachmentWireTransport.MMS.name ||
            partIndex !in 0 until transfer.totalChunks
        ) return@withLock false
        val currentStatus = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
            ?: return@withLock true
        if(currentStatus.terminal) return@withLock true
        val now = System.currentTimeMillis()
        if(successful) {
            val sent = ChunkTracker.restore(transfer.totalChunks, transfer.sentBitmap)
            if(partIndex in sent) return@withLock true
            sent.mark(partIndex)
            transfers.update(transfer.copy(
                sentBitmap = sent.serialize(),
                smsSent = transfer.smsSent + 1,
                retryCount = 0,
                lastError = null,
                updatedAt = now,
            ))
            if(sent.count() < transfer.totalChunks) {
                AttachmentSmsStatusReceiver.enqueue(context, transferIdHex, MMS_PACING_MILLIS)
            } else {
                AttachmentAckTimeoutWorker.enqueue(context, transferIdHex)
            }
        } else {
            val retry = transfer.retryCount + 1
            transfers.update(transfer.copy(
                status = if(retry >= MAX_MMS_RETRIES) AttachmentTransferStatus.FAILED.name
                    else AttachmentTransferStatus.RETRYING.name,
                retryCount = retry,
                lastError = "MMS part send failed ($resultCode)",
                updatedAt = now,
            ))
            if(retry < MAX_MMS_RETRIES) {
                AttachmentSmsStatusReceiver.enqueue(
                    context,
                    transferIdHex,
                    minOf(15L * 60_000L, 30_000L shl minOf(retry - 1, 4)),
                )
            }
        }
        true
    }

    suspend fun onSmsTransportStatus(
        transferIdHex: String,
        packetType: SmsPacketType,
        packetIndex: Int,
        deliveredCallback: Boolean,
        successful: Boolean,
        resultCode: Int,
    ) = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock
        val now = System.currentTimeMillis()
        val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
            ?: return@withLock
        if(deliveredCallback) {
            if(successful) {
                transfers.incrementDelivered(transferIdHex, now)
            } else if(!status.terminal) {
                transfers.update(transfer.copy(
                    status = AttachmentTransferStatus.FAILED.name,
                    lastError = "SMS delivery failed ($resultCode)",
                    updatedAt = now,
                ))
            }
            return@withLock
        }
        if(status.terminal) {
            if(successful && packetType == SmsPacketType.TRANSFER_COMPLETE && transfer.outgoing) {
                keyManager.delete(TransferId.fromHex(transferIdHex))
            }
            return@withLock
        }
        if(packetType == SmsPacketType.TRANSFER_CHUNK &&
            packetIndex !in 0 until transfer.totalChunks
        ) return@withLock
        if(successful) {
            val updated = when(packetType) {
                SmsPacketType.TRANSFER_OFFER -> transfer.copy(
                    controlSequence = maxOf(transfer.controlSequence, packetIndex + 1),
                    smsSent = transfer.smsSent + 1,
                    retryCount = 0,
                    lastError = null,
                    updatedAt = now,
                )
                SmsPacketType.TRANSFER_CHUNK -> {
                    val sent = ChunkTracker.restore(transfer.totalChunks, transfer.sentBitmap)
                    sent.mark(packetIndex)
                    transfer.copy(
                        sentBitmap = sent.serialize(),
                        smsSent = transfer.smsSent + 1,
                        retryCount = 0,
                        lastError = null,
                        updatedAt = now,
                    )
                }
                else -> transfer.copy(
                    smsSent = transfer.smsSent + 1,
                    // Keep the receiver-acknowledgement attempt counter for NACK packets.
                    // Resetting it here caused an endless request every timeout period.
                    retryCount = if(packetType == SmsPacketType.NACK) {
                        transfer.retryCount
                    } else {
                        0
                    },
                    lastError = null,
                    updatedAt = now,
                )
            }
            transfers.update(updated)
            if(packetType == SmsPacketType.TRANSFER_COMPLETE && transfer.outgoing) {
                keyManager.delete(TransferId.fromHex(transferIdHex))
            }
            if(packetType == SmsPacketType.TRANSFER_OFFER ||
                packetType == SmsPacketType.TRANSFER_CHUNK
            ) {
                AttachmentSmsStatusReceiver.enqueue(context, transferIdHex, SMS_PACING_MILLIS)
            }
            if(packetType == SmsPacketType.TRANSFER_CHUNK || packetType == SmsPacketType.NACK) {
                AttachmentAckTimeoutWorker.enqueue(context, transferIdHex)
            }
        } else {
            val retry = transfer.retryCount + 1
            transfers.update(transfer.copy(
                status = if(retry >= MAX_SMS_RETRIES) AttachmentTransferStatus.FAILED.name
                    else AttachmentTransferStatus.RETRYING.name,
                retryCount = retry,
                lastError = smsError(resultCode),
                updatedAt = now,
            ))
            if(retry < MAX_SMS_RETRIES) {
                AttachmentSmsStatusReceiver.enqueue(context, transferIdHex, smsRetryDelay(retry))
            }
        }
    }

    override suspend fun consume(
        context: Context,
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
    ): Boolean = consumeSmsFrame(
        context = context,
        address = address,
        subscriptionId = subscriptionId,
        payload = payload,
        receivedTransport = null,
    )

    suspend fun consumeCompatibleText(
        context: Context,
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
    ): Boolean = consumeSmsFrame(
        context = context,
        address = address,
        subscriptionId = subscriptionId,
        payload = payload,
        receivedTransport = AttachmentWireTransport.STANDARD_SMS,
    )

    private suspend fun consumeSmsFrame(
        context: Context,
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
        receivedTransport: AttachmentWireTransport?,
    ): Boolean {
        if (payload.size < 2 || payload[0] != 0x44.toByte() || payload[1] != 0x4b.toByte()) return false
        val decoded = SmsFrameCodec.decode(payload)
        if (decoded !is SmsFrameCodec.DecodeResult.Success) return true
        val frame = decoded.frame
        val normalizedAddress = context.makeE16PhoneNumber(address)
        lockFor(frame.transferId.toHex()).withLock {
            if (frame.packetType == SmsPacketType.TRANSFER_OFFER) {
                receiveOffer(normalizedAddress, subscriptionId, frame, receivedTransport)
            } else {
                receiveAuthenticatedFrame(
                    normalizedAddress,
                    subscriptionId,
                    frame,
                    receivedTransport,
                )
            }
        }
        return true
    }

    suspend fun consumeMmsPart(
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
    ): Boolean {
        val header = MmsMediaContainer.inspect(payload) ?: return false
        val id = header.transferId.toHex()
        val normalizedAddress = context.makeE16PhoneNumber(address).ifBlank { address.trim() }
        lockFor(id).withLock {
            val transfer = transfers.get(id) ?: return true
            if(transfer.outgoing || transfer.transport != AttachmentWireTransport.MMS.name ||
                transfer.address != normalizedAddress || transfer.subscriptionId != subscriptionId ||
                header.flags != transfer.protocolFlags() ||
                header.totalParts != transfer.totalChunks
            ) return true
            val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
                ?: return true
            if(status != AttachmentTransferStatus.RECEIVING &&
                status != AttachmentTransferStatus.COMPLETED
            ) return true
            if(transfer.isSecure() && (!identityMatches(transfer) ||
                SecureSessionStatusResolver.resolve(
                    context,
                    normalizedAddress,
                    subscriptionId.toLong(),
                ) != SecureSessionStatus.SECURE_ESTABLISHED)
            ) {
                transfers.update(transfer.copy(
                    status = AttachmentTransferStatus.PAUSED.name,
                    lastError = "Secure identity or session changed",
                    updatedAt = System.currentTimeMillis(),
                ))
                return true
            }
            val key = try {
                keyManager.load(header.transferId)
            } catch (_: Exception) {
                return true
            }
            val decoded = try {
                MmsMediaContainer.decrypt(key, payload)
            } finally {
                key.fill(0)
            }
            val part = (decoded as? MmsMediaContainer.DecodeResult.Success)?.part ?: return true
            try {
                receiveChunk(
                    transfer = transfer,
                    chunkIndex = part.header.partIndex,
                    totalChunks = part.header.totalParts,
                    plaintext = part.plaintext,
                )
            } finally {
                part.plaintext.fill(0)
            }
        }
        return true
    }

    private suspend fun receiveOffer(
        address: String,
        subscriptionId: Int,
        frame: SmsFrame,
        receivedTransport: AttachmentWireTransport?,
    ) {
        val id = frame.transferId.toHex()
        if (!AttachmentProtocolFlags.isSupported(frame.flags)) return
        val existingTransfer = transfers.get(id)
        if(existingTransfer != null && !existingTransfer.outgoing) return
        offers.deleteExpired(System.currentTimeMillis() - OFFER_TTL_MILLIS)
        val current = offers.get(id)
        if (current.isEmpty() && offers.countContexts() >= TransferLimits.MAX_PENDING_TRANSFERS) return
        if (current.any { it.address != address || it.subscriptionId != subscriptionId ||
                it.flags != frame.flags || it.totalFragments != frame.totalChunks ||
                (it.fragmentIndex == frame.chunkIndex && !it.payload.contentEquals(frame.payload)) }) {
            offers.delete(id)
            return
        }
        offers.insert(AttachmentOfferFragmentEntity(
            transferId = id, fragmentIndex = frame.chunkIndex, totalFragments = frame.totalChunks,
            address = address, subscriptionId = subscriptionId, flags = frame.flags,
            payload = frame.payload,
            receivedAt = System.currentTimeMillis(),
        ))
        val fragments = offers.get(id)
        if (fragments.size != frame.totalChunks || fragments.indices.any { fragments[it].fragmentIndex != it }) return
        val size = fragments.sumOf { it.payload.size }
        if (size > TransferLimits.MAX_OFFER_FRAGMENTS * TransferLimits.FRAME_PAYLOAD_BYTES) {
            offers.delete(id)
            return
        }
        val protection = if (AttachmentProtocolFlags.isUnprotected(frame.flags)) {
            AttachmentProtection.UNPROTECTED
        } else {
            AttachmentProtection.SECURE
        }
        if (protection == AttachmentProtection.SECURE && SecureSessionStatusResolver.resolve(
                context,
                address,
                subscriptionId.toLong(),
            ) != SecureSessionStatus.SECURE_ESTABLISHED) {
            offers.delete(id)
            return
        }
        val assembledOffer = ByteArray(size)
        var offset = 0
        fragments.forEach {
            it.payload.copyInto(assembledOffer, offset)
            offset += it.payload.size
        }
        val attachmentContext = try {
            if (protection == AttachmentProtection.SECURE) {
                val plaintext = EncryptionController.decryptBytes(
                    context,
                    SecureChannelId.storageAddress(address, subscriptionId.toLong()),
                    assembledOffer,
                ) ?: return
                try { AttachmentContextCodec.decode(plaintext) } finally { plaintext.fill(0) }
            } else {
                AttachmentContextCodec.decode(assembledOffer)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Rejected invalid attachment offer", error)
            offers.delete(id)
            return
        } finally {
            assembledOffer.fill(0)
        }
        val expectedChunks = when(attachmentContext.transport) {
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS -> runCatching {
                TransferLimits.chunkCount(attachmentContext.manifest.encodedSize)
            }.getOrNull()
            AttachmentWireTransport.MMS -> runCatching {
                TransferLimits.partCount(
                    attachmentContext.manifest.encodedSize,
                    attachmentContext.chunkPlaintextBytes,
                    TransferLimits.MAX_MMS_TRANSFER_BYTES,
                )
            }.getOrNull()
            AttachmentWireTransport.CLOUD_STORAGE -> 1
        }
        val remoteSourceValid = if(attachmentContext.transport == AttachmentWireTransport.CLOUD_STORAGE) {
            attachmentContext.remoteSource?.let {
                CloudProvider.fromWireCode(it.providerCode) != null
            } == true
        } else {
            attachmentContext.remoteSource == null
        }
        if (attachmentContext.manifest.transferId != frame.transferId ||
            !transportFlagsMatch(attachmentContext.transport, frame.flags) ||
            expectedChunks != attachmentContext.manifest.totalChunks ||
            !remoteSourceValid) {
            attachmentContext.masterKey.fill(0)
            offers.delete(id)
            return
        }
        val effectiveContext = if(receivedTransport == AttachmentWireTransport.STANDARD_SMS &&
            attachmentContext.transport in setOf(
                AttachmentWireTransport.DATA_SMS,
                AttachmentWireTransport.STANDARD_SMS,
            )
        ) {
            // A transfer may fall back after its offer was created. The authenticated context
            // describes the payload format; the actual envelope decides how replies travel.
            attachmentContext.copy(transport = AttachmentWireTransport.STANDARD_SMS)
        } else {
            attachmentContext
        }
        if(existingTransfer != null) {
            val storedKey = runCatching { keyManager.load(frame.transferId) }.getOrNull()
            val keyMatches = storedKey?.let {
                try {
                    MessageDigest.isEqual(it, attachmentContext.masterKey)
                } finally {
                    it.fill(0)
                }
            } == true
            if(isMatchingSameDeviceLoopbackOffer(
                    existing = existingTransfer,
                    incomingAddress = address,
                    incomingSubscriptionId = subscriptionId,
                    attachmentContext = effectiveContext,
                    keyMatches = keyMatches,
                )) {
                val partial = existingTransfer.partialPath?.let(::File)
                    ?: files.createPartial(attachmentContext.manifest)
                transfers.update(existingTransfer.copy(
                    status = AttachmentTransferStatus.SENDING.name,
                    partialPath = partial.absolutePath,
                    receivedBitmap = ByteArray(0),
                    acknowledgedBitmap = ByteArray(0),
                    retryCount = 0,
                    lastError = null,
                    updatedAt = System.currentTimeMillis(),
                ))
                AttachmentSmsStatusReceiver.enqueue(context, existingTransfer.transferId)
            }
            attachmentContext.masterKey.fill(0)
            offers.delete(id)
            return
        }
        if(transfers.countActiveForAddress(address) >=
            TransferLimits.MAX_ACTIVE_TRANSFERS_PER_CONTACT ||
            transfers.countPendingOffers() >= TransferLimits.MAX_PENDING_TRANSFERS
        ) {
            attachmentContext.masterKey.fill(0)
            offers.delete(id)
            return
        }
        val manifest = attachmentContext.manifest
        val identityFingerprint = if (protection == AttachmentProtection.SECURE) {
            try {
                currentIdentityFingerprint(address, subscriptionId)
            } catch (_: Exception) {
                attachmentContext.masterKey.fill(0)
                offers.delete(id)
                return
            }
        } else {
            ByteArray(0)
        }
        val now = System.currentTimeMillis()
        val transfer = AttachmentTransferEntity(
            transferId = id, address = address, identityFingerprint = identityFingerprint,
            subscriptionId = subscriptionId, outgoing = false, protection = protection.name,
            transport = effectiveContext.transport.name,
            chunkPlaintextBytes = attachmentContext.chunkPlaintextBytes,
            remoteProvider = attachmentContext.remoteSource?.providerCode
                ?.let { CloudProvider.fromWireCode(it)?.name },
            remoteLocator = attachmentContext.remoteSource?.locator,
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

    private suspend fun receiveAuthenticatedFrame(
        address: String,
        subscriptionId: Int,
        frame: SmsFrame,
        receivedTransport: AttachmentWireTransport?,
    ) {
        var transfer = transfers.get(frame.transferId.toHex()) ?: return
        val sameDeviceLoopbackChunk = transfer.outgoing &&
            transfer.transport in setOf(
                AttachmentWireTransport.DATA_SMS.name,
                AttachmentWireTransport.STANDARD_SMS.name,
            ) &&
            transfer.partialPath != null &&
            frame.packetType == SmsPacketType.TRANSFER_CHUNK &&
            (transfer.address != address || transfer.subscriptionId != subscriptionId)
        if (!sameDeviceLoopbackChunk &&
            (transfer.address != address || transfer.subscriptionId != subscriptionId)
        ) return
        if (frame.flags != transfer.protocolFlags()) return
        if (transfer.isSecure() && (!identityMatches(transfer) ||
            SecureSessionStatusResolver.resolve(
                context,
                address,
                subscriptionId.toLong(),
            ) != SecureSessionStatus.SECURE_ESTABLISHED)) {
            transfers.update(transfer.copy(
                status = AttachmentTransferStatus.PAUSED.name,
                lastError = "Secure identity or session changed",
                updatedAt = System.currentTimeMillis(),
            ))
            return
        }
        val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull() ?: return
        if (status.terminal && !(status == AttachmentTransferStatus.COMPLETED &&
                !transfer.outgoing && (frame.packetType == SmsPacketType.NACK ||
                    frame.packetType == SmsPacketType.TRANSFER_COMPLETE))) return
        val key = try { keyManager.load(frame.transferId) } catch (_: Exception) { return }
        val direction = when {
            sameDeviceLoopbackChunk -> AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER
            transfer.outgoing -> AttachmentCrypto.Direction.RESPONDER_TO_INITIATOR
            else -> AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER
        }
        val decrypted = try { AttachmentCrypto.decrypt(key, frame, direction) } finally { key.fill(0) }
        val plaintext = (decrypted as? AttachmentCrypto.DecryptResult.Success)?.plaintext ?: return
        if(receivedTransport == AttachmentWireTransport.STANDARD_SMS &&
            !transfer.outgoing &&
            transfer.transport == AttachmentWireTransport.DATA_SMS.name
        ) {
            // Change the reply route only after authentication. Otherwise a forged text SMS
            // containing a known transfer id could downgrade/redirect an active transfer.
            transfer = transfer.copy(
                transport = AttachmentWireTransport.STANDARD_SMS.name,
                updatedAt = System.currentTimeMillis(),
            )
            transfers.update(transfer)
        }
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
                SmsPacketType.TRANSFER_CHUNK -> if(sameDeviceLoopbackChunk) {
                    receiveSameDeviceLoopbackChunk(
                        transfer,
                        address,
                        subscriptionId,
                        frame.chunkIndex,
                        frame.totalChunks,
                        plaintext,
                    )
                } else {
                    receiveChunk(
                        transfer,
                        frame.chunkIndex,
                        frame.totalChunks,
                        plaintext,
                    )
                }
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
        if(updated.transport == AttachmentWireTransport.CLOUD_STORAGE.name) {
            CloudDownloadWorker.enqueue(context, updated.transferId)
        }
    }

    suspend fun resumeCloudDownload(transferIdHex: String): Boolean =
        lockFor(transferIdHex).withLock {
            val transfer = transfers.get(transferIdHex) ?: return@withLock true
            val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }.getOrNull()
                ?: return@withLock true
            if(transfer.outgoing || transfer.transport != AttachmentWireTransport.CLOUD_STORAGE.name ||
                status !in setOf(AttachmentTransferStatus.RECEIVING, AttachmentTransferStatus.RETRYING)
            ) return@withLock true
            if(transfer.isSecure() && (!identityMatches(transfer) ||
                SecureSessionStatusResolver.resolve(
                    context,
                    transfer.address,
                    transfer.subscriptionId.toLong(),
                ) != SecureSessionStatus.SECURE_ESTABLISHED)
            ) {
                transfers.update(transfer.copy(
                    status = AttachmentTransferStatus.PAUSED.name,
                    lastError = "Secure identity or session changed",
                    updatedAt = System.currentTimeMillis(),
                ))
                return@withLock true
            }
            receiveCloudObject(transfer)
        }

    private suspend fun receiveCloudObject(transfer: AttachmentTransferEntity): Boolean {
        val provider = transfer.remoteProvider?.let {
            runCatching { CloudProvider.valueOf(it) }.getOrNull()
        } ?: return fail(transfer, "Cloud provider is missing").let { true }
        val locator = transfer.remoteLocator
            ?: return fail(transfer, "Cloud object location is missing").let { true }
        val encryptedDirectory = File(context.cacheDir, "cloud_downloads")
        check(encryptedDirectory.mkdirs() || encryptedDirectory.isDirectory)
        val encrypted = File(encryptedDirectory, "${transfer.transferId}.dkc")
        val partial = transfer.partialPath?.let(::File)
            ?: return fail(transfer, "Attachment destination is missing").let { true }
        try {
            cloudDownloader.download(
                CloudObjectReference(provider, locator),
                encrypted,
                CloudMediaContainer.MAX_ENCODED_BYTES,
            )
            val transferId = TransferId.fromHex(transfer.transferId)
            val key = keyManager.load(transferId)
            try {
                CloudMediaContainer.decrypt(
                    masterKey = key,
                    expectedTransferId = transferId,
                    expectedFlags = transfer.protocolFlags(),
                    source = encrypted,
                    destination = partial,
                )
            } finally {
                key.fill(0)
            }
            val completed = files.verifyAndCommit(transfer.toManifest())
            val updated = transfer.copy(
                receivedBitmap = ChunkTracker.empty(transfer.totalChunks).apply { mark(0) }.serialize(),
                status = AttachmentTransferStatus.COMPLETED.name,
                completedPath = completed.absolutePath,
                partialPath = null,
                updatedAt = System.currentTimeMillis(),
            )
            transfers.update(updated)
            postIncomingAttachmentNotification(updated)
            // The file is already atomically committed. A lost COMPLETE control
            // packet is recoverable: the sender will NACK and the completed
            // receiver answers again without downloading the object twice.
            runCatching { sendControl(updated, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0)) }
            return true
        } catch(error: CancellationException) {
            throw error
        } catch(error: Exception) {
            Log.w(TAG, "Cloud attachment download failed", error)
            val retry = transfer.retryCount + 1
            val exhausted = retry >= MAX_CLOUD_RETRIES
            val failedAttempt = transfer.copy(
                status = if(exhausted) AttachmentTransferStatus.FAILED.name
                    else AttachmentTransferStatus.RETRYING.name,
                retryCount = retry,
                lastError = error.message ?: "Cloud attachment download failed",
                updatedAt = System.currentTimeMillis(),
            )
            transfers.update(failedAttempt)
            if(exhausted) {
                runCatching {
                    sendControl(
                        failedAttempt,
                        SmsPacketType.ERROR,
                        (error.message ?: "Cloud download failed")
                            .toByteArray(Charsets.UTF_8)
                            .take(TransferLimits.CHUNK_PLAINTEXT_BYTES)
                            .toByteArray(),
                    )
                }
            }
            return exhausted
        } finally {
            encrypted.delete()
        }
    }

    suspend fun reject(transferIdHex: String) {
        CloudDownloadWorker.cancel(context, transferIdHex)
        rejectLocked(transferIdHex)
    }

    private suspend fun rejectLocked(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        if (transfer.status == AttachmentTransferStatus.OFFERED.name) {
            sendControl(transfer, SmsPacketType.TRANSFER_REJECT, ByteArray(0), fixedIndex = 0)
        }
        cancelInternal(transfer)
    }

    suspend fun cancel(transferIdHex: String) {
        CloudUploadWorker.cancel(context, transferIdHex)
        CloudDownloadWorker.cancel(context, transferIdHex)
        cancelLocked(transferIdHex)
    }

    /**
     * Restarts a recoverable transfer without clearing its chunk bitmaps. This is deliberately
     * different from creating a new transfer: successfully acknowledged parts keep their indexes,
     * so a manual continuation cannot restart a large Data-SMS attachment from part zero.
     */
    suspend fun continueTransfer(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        val status = AttachmentTransferStatus.valueOf(transfer.status)
        require(status == AttachmentTransferStatus.FAILED ||
            status == AttachmentTransferStatus.RETRYING ||
            status == AttachmentTransferStatus.PAUSED
        ) { "Transfer cannot be continued from ${transfer.status}" }
        require(transfer.expiresAt > System.currentTimeMillis()) { "Transfer expired" }

        if(transfer.isSecure()) {
            require(identityMatches(transfer) && SecureSessionStatusResolver.resolve(
                context,
                transfer.address,
                transfer.subscriptionId.toLong(),
            ) == SecureSessionStatus.SECURE_ESTABLISHED) {
                "Secure identity or session changed"
            }
        }

        val resumedStatus = when {
            transfer.transport == AttachmentWireTransport.CLOUD_STORAGE.name &&
                transfer.outgoing && transfer.remoteLocator == null -> AttachmentTransferStatus.PREPARING
            transfer.transport == AttachmentWireTransport.CLOUD_STORAGE.name &&
                !transfer.outgoing -> AttachmentTransferStatus.RECEIVING
            else -> AttachmentTransferStatus.RETRYING
        }
        transfers.update(transfer.copy(
            status = resumedStatus.name,
            // A manual Data-SMS continuation is one deliberate attempt. Resetting this to zero
            // used to restart the whole eight-attempt background cycle when the carrier had
            // already rejected the same packet. A successful callback resets the counter and
            // resumes normal pacing for following packets.
            retryCount = manualContinuationRetryCount(
                transport = runCatching { AttachmentWireTransport.valueOf(transfer.transport) }
                    .getOrDefault(AttachmentWireTransport.DATA_SMS),
                maxSmsRetries = MAX_SMS_RETRIES,
            ),
            lastError = null,
            updatedAt = System.currentTimeMillis(),
        ))
        when {
            resumedStatus == AttachmentTransferStatus.PREPARING ->
                CloudUploadWorker.enqueue(context, transferIdHex)
            resumedStatus == AttachmentTransferStatus.RECEIVING ->
                CloudDownloadWorker.enqueue(context, transferIdHex)
            else -> AttachmentSmsStatusReceiver.enqueue(context, transferIdHex)
        }
    }

    /** Keeps the transfer id, key and chunk bitmaps, changing only the carrier envelope. */
    suspend fun continueWithStandardSms(transferIdHex: String) =
        lockFor(transferIdHex).withLock {
            val transfer = requireNotNull(transfers.get(transferIdHex))
            val status = AttachmentTransferStatus.valueOf(transfer.status)
            require(transfer.outgoing) { "Only outgoing transfers can change SMS envelope" }
            require(transfer.transport == AttachmentWireTransport.DATA_SMS.name) {
                "Only Data-SMS transfers can continue as ordinary SMS"
            }
            require(status == AttachmentTransferStatus.FAILED ||
                status == AttachmentTransferStatus.RETRYING ||
                status == AttachmentTransferStatus.PAUSED
            ) { "Transfer cannot be continued from ${transfer.status}" }
            require(transfer.expiresAt > System.currentTimeMillis()) { "Transfer expired" }
            if(transfer.isSecure()) {
                require(identityMatches(transfer) && SecureSessionStatusResolver.resolve(
                    context,
                    transfer.address,
                    transfer.subscriptionId.toLong(),
                ) == SecureSessionStatus.SECURE_ESTABLISHED) {
                    "Secure identity or session changed"
                }
            }
            transfers.update(transfer.copy(
                transport = AttachmentWireTransport.STANDARD_SMS.name,
                status = AttachmentTransferStatus.RETRYING.name,
                retryCount = 0,
                lastError = null,
                updatedAt = System.currentTimeMillis(),
            ))
            AttachmentSmsStatusReceiver.enqueue(context, transferIdHex)
        }

    private suspend fun cancelLocked(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = requireNotNull(transfers.get(transferIdHex))
        val status = AttachmentTransferStatus.valueOf(transfer.status)
        if (!status.terminal) {
            if(status != AttachmentTransferStatus.PREPARING) {
                sendControl(transfer, SmsPacketType.CANCEL, ByteArray(0))
            }
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
        transfers.getByStatuses(listOf(AttachmentTransferStatus.PREPARING.name))
            .filter { it.outgoing && it.transport == AttachmentWireTransport.CLOUD_STORAGE.name }
            .forEach { CloudUploadWorker.enqueue(context, it.transferId) }
        transfers.getByStatuses(listOf(AttachmentTransferStatus.RECEIVING.name))
            .plus(transfers.getByStatuses(listOf(AttachmentTransferStatus.RETRYING.name)))
            .filter { !it.outgoing && it.transport == AttachmentWireTransport.CLOUD_STORAGE.name }
            .distinctBy(AttachmentTransferEntity::transferId)
            .forEach { CloudDownloadWorker.enqueue(context, it.transferId) }
        transfers.getPendingCloudCleanup().forEach {
            CloudCleanupWorker.enqueue(context, it.transferId)
        }
    }

    private suspend fun cleanupExpired() {
        transfers.getExpired(System.currentTimeMillis()).forEach { transfer ->
            runCatching { files.deletePartial(TransferId.fromHex(transfer.transferId)) }
            runCatching { transfer.sourcePath?.let(::File)?.delete() }
            if(transfer.remoteDeleteLocator != null) {
                CloudCleanupWorker.enqueue(context, transfer.transferId)
            }
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
        if(transfer.expiresAt <= System.currentTimeMillis()) {
            cancelInternal(transfer)
            return@withLock
        }
        val acknowledged = ChunkTracker.restore(transfer.totalChunks, transfer.acknowledgedBitmap)
        val nextAckRequest = nextAttachmentAckRequestCount(
            currentCount = transfer.retryCount,
            maxRequests = MAX_ACK_REQUESTS,
        )
        if(nextAckRequest == null) {
            fail(transfer, "Receiver did not acknowledge attachment")
            return@withLock
        }
        val firstUnknown = (0 until transfer.totalChunks).firstOrNull { it !in acknowledged } ?: 0
        val base = (firstUnknown / TransferLimits.ACK_WINDOW_BITS) * TransferLimits.ACK_WINDOW_BITS
        val retrying = transfer.copy(
            retryCount = nextAckRequest,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(retrying)
        sendControl(
            retrying,
            SmsPacketType.NACK,
            byteArrayOf((base ushr 8).toByte(), base.toByte()),
        )
        AttachmentAckTimeoutWorker.enqueue(
            context,
            transferIdHex,
            ACK_RETRY_SECONDS,
        )
    }

    suspend fun finalizeSentCompletion(transferIdHex: String) = lockFor(transferIdHex).withLock {
        val transfer = transfers.get(transferIdHex) ?: return@withLock
        if (transfer.outgoing && transfer.status == AttachmentTransferStatus.COMPLETED.name) {
            keyManager.delete(TransferId.fromHex(transferIdHex))
        }
    }

    private suspend fun receiveChunk(
        transfer: AttachmentTransferEntity,
        chunkIndex: Int,
        totalChunks: Int,
        plaintext: ByteArray,
    ) {
        if (transfer.outgoing || transfer.status != AttachmentTransferStatus.RECEIVING.name ||
            totalChunks != transfer.totalChunks) return
        val tracker = ChunkTracker.restore(transfer.totalChunks, transfer.receivedBitmap)
        if (chunkIndex !in tracker) {
            val partial = transfer.partialPath?.let(::File) ?: return
            files.writeVerifiedChunk(
                partial,
                transfer.toManifest(),
                chunkIndex,
                plaintext,
                transfer.chunkPlaintextBytes,
            )
            tracker.mark(chunkIndex)
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
                postIncomingAttachmentNotification(updated)
                sendControl(updated, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0))
            } catch (error: Exception) {
                fail(updated, "Final attachment verification failed")
            }
        } else if (tracker.count() % ACK_EVERY_CHUNKS == 0) {
            sendAck(updated, tracker)
        }
        if (!tracker.isComplete()) AttachmentAckWorker.enqueue(context, transfer.transferId)
    }

    private suspend fun receiveSameDeviceLoopbackChunk(
        transfer: AttachmentTransferEntity,
        incomingAddress: String,
        incomingSubscriptionId: Int,
        chunkIndex: Int,
        totalChunks: Int,
        plaintext: ByteArray,
    ) {
        if(!transfer.outgoing || transfer.status != AttachmentTransferStatus.SENDING.name ||
            transfer.partialPath == null || totalChunks != transfer.totalChunks
        ) return
        val received = ChunkTracker.restore(transfer.totalChunks, transfer.receivedBitmap)
        val acknowledged = ChunkTracker.restore(
            transfer.totalChunks,
            transfer.acknowledgedBitmap,
        )
        if(chunkIndex !in received) {
            files.writeVerifiedChunk(
                File(transfer.partialPath),
                transfer.toManifest(),
                chunkIndex,
                plaintext,
                transfer.chunkPlaintextBytes,
            )
            received.mark(chunkIndex)
        }
        acknowledged.mark(chunkIndex)
        var updated = transfer.copy(
            receivedBitmap = received.serialize(),
            acknowledgedBitmap = acknowledged.serialize(),
            smsReceived = transfer.smsReceived + 1,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(updated)
        if(!received.isComplete()) return
        updated = updated.copy(
            status = AttachmentTransferStatus.VERIFYING.name,
            updatedAt = System.currentTimeMillis(),
        )
        transfers.update(updated)
        try {
            val completed = files.verifyAndCommit(updated.toManifest())
            updated = updated.copy(
                status = AttachmentTransferStatus.COMPLETED.name,
                completedPath = completed.absolutePath,
                partialPath = null,
                updatedAt = System.currentTimeMillis(),
            )
            val mirror = persistSameDeviceLoopbackCompletion(
                outgoing = updated,
                incomingAddress = incomingAddress,
                incomingSubscriptionId = incomingSubscriptionId,
                completedPath = completed.absolutePath,
            )
            mirror?.let(::postIncomingAttachmentNotification)
            updated.sourcePath?.let(::File)?.delete()
            keyManager.delete(TransferId.fromHex(updated.transferId))
        } catch(error: Exception) {
            fail(updated, "Loopback attachment verification failed")
        }
    }

    private suspend fun persistSameDeviceLoopbackCompletion(
        outgoing: AttachmentTransferEntity,
        incomingAddress: String,
        incomingSubscriptionId: Int,
        completedPath: String,
    ): AttachmentTransferEntity? {
        val identityFingerprint = if(outgoing.isSecure()) {
            runCatching {
                currentIdentityFingerprint(incomingAddress, incomingSubscriptionId)
            }.getOrDefault(outgoing.identityFingerprint)
        } else {
            ByteArray(0)
        }
        val mirror = sameDeviceLoopbackMirror(
            outgoing = outgoing,
            incomingAddress = incomingAddress,
            incomingSubscriptionId = incomingSubscriptionId,
            incomingIdentityFingerprint = identityFingerprint,
            completedPath = completedPath,
            now = System.currentTimeMillis(),
        )
        val inserted = database.withTransaction {
            transfers.update(outgoing)
            if(transfers.get(mirror.transferId) == null) {
                transfers.insert(mirror)
                true
            } else {
                false
            }
        }
        return mirror.takeIf { inserted }
    }

    private fun postIncomingAttachmentNotification(transfer: AttachmentTransferEntity) {
        if(!shouldPostIncomingAttachmentNotification(
                outgoing = transfer.outgoing,
                status = transfer.status,
                isMuted = false,
            )
        ) return
        runCatching {
            val threadId = context.getThreadId(transfer.address)
            val isMuted = context.getDatabase().threadsDao()?.get(threadId)?.isMute == true
            if(!shouldPostIncomingAttachmentNotification(
                    outgoing = transfer.outgoing,
                    status = transfer.status,
                    isMuted = isMuted,
                )
            ) return
            val label = when(runCatching {
                AttachmentManifest.MediaType.valueOf(transfer.mediaType)
            }.getOrNull()) {
                AttachmentManifest.MediaType.PHOTO ->
                    context.getString(com.afkanerd.deku.DefaultSMS.R.string.attachment_photo)
                AttachmentManifest.MediaType.VOICE ->
                    context.getString(com.afkanerd.deku.DefaultSMS.R.string.attachment_voice)
                else -> context.getString(com.afkanerd.deku.DefaultSMS.R.string.attachment_file)
            }
            val conversation = Conversations(
                sms = SmsMmsNatives.Sms(
                    thread_id = threadId,
                    address = transfer.address,
                    date = transfer.updatedAt,
                    date_sent = transfer.updatedAt,
                    read = 0,
                    status = Telephony.Sms.STATUS_NONE,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    body = label,
                    sub_id = transfer.subscriptionId.toLong(),
                ),
            )
            context.notify(
                conversation = conversation,
                cls = MainActivity::class.java,
                actions = false,
                text = label,
            )
        }.onFailure { error ->
            Log.w(TAG, "Incoming attachment notification could not be shown", error)
        }
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
            retryCount = 0,
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
                flags = transfer.protocolFlags(),
            )
        } finally { key.fill(0) }
        return transportFor(transfer).send(frame, BinaryRoute(transfer.address, transfer.subscriptionId))
    }

    private suspend fun completeSender(transfer: AttachmentTransferEntity) {
        sendControl(transfer, SmsPacketType.TRANSFER_COMPLETE, ByteArray(0))
        val current = transfers.get(transfer.transferId) ?: transfer
        transfers.update(current.copy(
            status = AttachmentTransferStatus.COMPLETED.name,
            smsReceived = current.smsReceived + 1,
            updatedAt = System.currentTimeMillis(),
        ))
        current.sourcePath?.let(::File)?.delete()
        if(current.remoteDeleteLocator != null) {
            CloudCleanupWorker.enqueue(context, current.transferId)
        }
    }

    private suspend fun finalizeReceiver(transfer: AttachmentTransferEntity) {
        if (transfer.status != AttachmentTransferStatus.COMPLETED.name) return
        keyManager.delete(TransferId.fromHex(transfer.transferId))
    }

    private suspend fun cancelInternal(transfer: AttachmentTransferEntity) {
        val current = transfers.get(transfer.transferId) ?: transfer
        transfers.update(current.copy(
            status = AttachmentTransferStatus.CANCELLED.name,
            updatedAt = System.currentTimeMillis(),
        ))
        files.deletePartial(TransferId.fromHex(current.transferId))
        current.sourcePath?.let(::File)?.delete()
        keyManager.delete(TransferId.fromHex(current.transferId))
        if(current.remoteDeleteLocator != null) {
            CloudCleanupWorker.enqueue(context, current.transferId)
        }
    }

    suspend fun resumeCloudCleanup(transferIdHex: String): Boolean =
        lockFor(transferIdHex).withLock {
            val transfer = transfers.get(transferIdHex) ?: return@withLock true
            if(transfer.remoteDeleteLocator == null) return@withLock true
            runCatching { deleteCloudObject(transfer) }.fold(
                onSuccess = {
                    transfers.update(transfer.copy(
                        remoteDeleteLocator = null,
                        lastError = null,
                        updatedAt = System.currentTimeMillis(),
                    ))
                    true
                },
                onFailure = { error ->
                    transfers.update(transfer.copy(
                        lastError = error.message ?: "Cloud object cleanup failed",
                        updatedAt = System.currentTimeMillis(),
                    ))
                    false
                },
            )
        }

    private suspend fun deleteCloudObject(transfer: AttachmentTransferEntity) {
        val deleteLocator = transfer.remoteDeleteLocator ?: return
        val configured = requireNotNull(cloudStorage.credentials()) {
            "Cloud object cleanup requires the original connected account"
        }
        val provider = requireNotNull(transfer.remoteProvider?.let {
            runCatching { CloudProvider.valueOf(it) }.getOrNull()
        }) { "Cloud provider is missing" }
        require(configured.profile.provider == provider) {
            "Cloud object cleanup requires the original provider"
        }
        CloudObjectStoreFactory.create(configured).delete(deleteLocator)
    }

    private suspend fun fail(transfer: AttachmentTransferEntity, reason: String): BinarySendResult.Failed {
        val current = transfers.get(transfer.transferId) ?: transfer
        transfers.update(current.copy(
            status = AttachmentTransferStatus.FAILED.name,
            lastError = reason,
            updatedAt = System.currentTimeMillis(),
        ))
        return BinarySendResult.Failed(reason)
    }

    private fun smsError(code: Int): String = when(code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Mobile radio is off"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS rate limit exceeded"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Destination rejected by SMS policy"
        else -> "SMS send failed ($code)"
    }

    private fun smsRetryDelay(retry: Int): Long = minOf(
        15L * 60_000L,
        15_000L shl minOf(retry - 1, 5),
    )

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

    private suspend fun currentIdentityFingerprint(
        address: String,
        subscriptionId: Int,
    ): ByteArray {
        val identity = IdentityKeyManager.getContactIdentity(
            context,
            SecureChannelId.storageAddress(address, subscriptionId.toLong()),
        )
        val publicKey = requireNotNull(identity.publicKey) {
            "Attachments require a signed identity; renew this legacy secure session"
        }
        return MessageDigest.getInstance("SHA-256").digest(publicKey)
    }

    private suspend fun identityMatches(transfer: AttachmentTransferEntity): Boolean = try {
        MessageDigest.isEqual(
            transfer.identityFingerprint,
            currentIdentityFingerprint(transfer.address, transfer.subscriptionId),
        )
    } catch (_: Exception) {
        false
    }

    private fun AttachmentTransferEntity.isSecure(): Boolean =
        protection == AttachmentProtection.SECURE.name

    private fun AttachmentTransferEntity.protocolFlags(): Int = protocolFlags(
        if(isSecure()) AttachmentProtection.SECURE else AttachmentProtection.UNPROTECTED,
        runCatching { AttachmentWireTransport.valueOf(transport) }
            .getOrDefault(AttachmentWireTransport.DATA_SMS),
    )

    private fun transportFor(transfer: AttachmentTransferEntity): BinaryTransport =
        if(transfer.transport == AttachmentWireTransport.STANDARD_SMS.name) {
            compatibleTextTransport
        } else {
            transport
        }

    private fun protocolFlags(
        protection: AttachmentProtection,
        wireTransport: AttachmentWireTransport,
    ): Int = (if(protection == AttachmentProtection.SECURE) {
        AttachmentProtocolFlags.NONE
    } else {
        AttachmentProtocolFlags.UNPROTECTED
    }) or when(wireTransport) {
        AttachmentWireTransport.MMS -> AttachmentProtocolFlags.MMS_PAYLOAD
        AttachmentWireTransport.CLOUD_STORAGE -> AttachmentProtocolFlags.CLOUD_PAYLOAD
        AttachmentWireTransport.DATA_SMS,
        AttachmentWireTransport.STANDARD_SMS -> AttachmentProtocolFlags.NONE
    }

    private fun transportFlagsMatch(transport: AttachmentWireTransport, flags: Int): Boolean =
        when(transport) {
            AttachmentWireTransport.MMS -> AttachmentProtocolFlags.usesMmsPayload(flags)
            AttachmentWireTransport.CLOUD_STORAGE -> AttachmentProtocolFlags.usesCloudPayload(flags)
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS ->
                !AttachmentProtocolFlags.usesMmsPayload(flags) &&
                    !AttachmentProtocolFlags.usesCloudPayload(flags)
        }

    companion object {
        private const val TAG = "AttachmentManager"
        private const val ACK_EVERY_CHUNKS = 16
        private const val MMS_PACING_MILLIS = 5_000L
        private const val MMS_CALLBACK_TIMEOUT_MILLIS = 2 * 60_000L
        private const val MAX_MMS_RETRIES = 5
        private const val MAX_CLOUD_RETRIES = 6
        private const val MAX_SMS_RETRIES = 8
        private const val MAX_ACK_REQUESTS = 3
        private const val SMS_PACING_MILLIS = 1_500L
        private const val ACK_RETRY_SECONDS = 90L
        private const val OFFER_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val TRANSFER_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private val locks = ConcurrentHashMap<String, Mutex>()
        @Volatile private var instance: AttachmentManager? = null

        fun get(context: Context): AttachmentManager = instance ?: synchronized(this) {
            instance ?: AttachmentManager(
                context.applicationContext,
                SmsBinaryTransport(context.applicationContext),
                CompatibleTextSmsTransport(context.applicationContext),
            ).also { instance = it }
        }
    }
}

internal fun manualContinuationRetryCount(
    transport: AttachmentWireTransport,
    maxSmsRetries: Int,
): Int {
    require(maxSmsRetries > 0)
    return if(transport == AttachmentWireTransport.DATA_SMS) maxSmsRetries - 1 else 0
}

internal fun nextAttachmentAckRequestCount(currentCount: Int, maxRequests: Int): Int? {
    require(currentCount >= 0)
    require(maxRequests > 0)
    return if(currentCount >= maxRequests) null else currentCount + 1
}

internal fun shouldPostIncomingAttachmentNotification(
    outgoing: Boolean,
    status: String,
    isMuted: Boolean,
): Boolean = !outgoing && !isMuted && status == AttachmentTransferStatus.COMPLETED.name
