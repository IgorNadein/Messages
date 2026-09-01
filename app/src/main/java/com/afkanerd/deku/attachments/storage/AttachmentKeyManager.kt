package com.afkanerd.deku.attachments.storage

import android.content.Context
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.removeEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.saveBinaryDataEncrypted

class AttachmentKeyManager(private val context: Context) {
    suspend fun save(transferId: TransferId, key: ByteArray) {
        require(key.size == 32)
        check(context.saveBinaryDataEncrypted(alias(transferId), key.copyOf())) {
            "Unable to persist attachment key"
        }
    }

    suspend fun load(transferId: TransferId): ByteArray =
        requireNotNull(context.getEncryptedBinaryData(alias(transferId))) { "Attachment key is missing" }
            .also { require(it.size == 32) { "Invalid attachment key" } }

    suspend fun delete(transferId: TransferId) {
        context.removeEncryptedBinaryData(alias(transferId))
    }

    private fun alias(transferId: TransferId): String = "deku_attachment_${transferId.toHex()}"
}
