package com.afkanerd.deku.attachments.storage

import android.content.Context
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.TransferId
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

class AttachmentFileStore(context: Context) {
    private val partialDirectory = File(context.filesDir, "attachments/partial")
    private val completedDirectory = File(context.filesDir, "attachments/completed")

    fun createPartial(manifest: AttachmentManifest): File {
        check(partialDirectory.mkdirs() || partialDirectory.isDirectory)
        val file = partialFile(manifest.transferId)
        RandomAccessFile(file, "rw").use { it.setLength(manifest.encodedSize) }
        return file
    }

    fun writeVerifiedChunk(
        file: File,
        manifest: AttachmentManifest,
        index: Int,
        plaintext: ByteArray,
        chunkPlaintextBytes: Int = com.afkanerd.deku.attachments.protocol.TransferLimits.CHUNK_PLAINTEXT_BYTES,
    ) {
        require(file.parentFile?.canonicalFile == partialDirectory.canonicalFile) { "Invalid partial path" }
        require(index in 0 until manifest.totalChunks)
        val expected = if (index == manifest.totalChunks - 1) {
            (manifest.encodedSize - index.toLong() * chunkPlaintextBytes).toInt()
        } else chunkPlaintextBytes
        require(plaintext.size == expected) { "Unexpected chunk length" }
        RandomAccessFile(file, "rw").use {
            it.seek(index.toLong() * chunkPlaintextBytes)
            it.write(plaintext)
            it.fd.sync()
        }
    }

    fun verifyAndCommit(manifest: AttachmentManifest): File {
        val partial = partialFile(manifest.transferId)
        require(partial.isFile && partial.length() == manifest.encodedSize) { "Partial file size mismatch" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(partial).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(MessageDigest.isEqual(digest.digest(), manifest.sha256)) { "Attachment digest mismatch" }
        check(completedDirectory.mkdirs() || completedDirectory.isDirectory)
        val safeName = "${manifest.transferId.toHex()}_${manifest.filename}"
        val completed = File(completedDirectory, safeName)
        require(completed.parentFile?.canonicalFile == completedDirectory.canonicalFile)
        check(partial.renameTo(completed)) { "Unable to atomically commit attachment" }
        return completed
    }

    fun deletePartial(transferId: TransferId) {
        partialFile(transferId).takeIf(File::exists)?.delete()
    }

    private fun partialFile(transferId: TransferId): File =
        File(partialDirectory, "${transferId.toHex()}.part")
}
