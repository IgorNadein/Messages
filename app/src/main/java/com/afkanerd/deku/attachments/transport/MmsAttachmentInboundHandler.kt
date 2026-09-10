package com.afkanerd.deku.attachments.transport

import android.content.Context
import android.net.Uri
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.deku.attachments.crypto.MmsMediaContainer
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsParser
import com.afkanerd.smswithoutborders_libsmsmms.transport.INTERNAL_MEDIA_MIME_TYPE
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundMmsHandler
import com.afkanerd.smswithoutborders_libsmsmms.transport.internalMmsTransferIdFromFilename
import java.io.ByteArrayOutputStream

class MmsAttachmentInboundHandler(
    private val attachmentManager: AttachmentManager,
) : InboundMmsHandler {
    override suspend fun consume(context: Context, contentUri: Uri): Boolean {
        val conversation = context.contentResolver.query(
            contentUri,
            null,
            null,
            null,
            null,
        )?.use { cursor ->
            if(cursor.moveToFirst()) {
                MmsParser.parse(context, cursor, includeInternalTransport = true)
            } else {
                null
            }
        } ?: return false
        val declaredInternal = conversation.mms_mimetype == INTERNAL_MEDIA_MIME_TYPE
        val filenameTransferId = internalMmsTransferIdFromFilename(conversation.mms_filename)
        if(!declaredInternal && filenameTransferId == null) return false
        val payloadUri = conversation.mms_content_uri?.let(Uri::parse) ?: return declaredInternal
        val payload = context.contentResolver.openInputStream(payloadUri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while(true) {
                val count = input.read(buffer)
                if(count < 0) break
                total += count
                if(total > MmsMediaContainer.MAX_ENCODED_PART_BYTES) return@use null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: return declaredInternal
        val inspected = MmsMediaContainer.inspect(payload)
        if(!declaredInternal && inspected?.transferId?.toHex() != filenameTransferId) {
            payload.fill(0)
            return false
        }
        val sender = conversation.sender_address
            ?.takeIf(String::isNotBlank)
            ?: conversation.participantAddresses.singleOrNull()
            ?: return true
        try {
            attachmentManager.consumeMmsPart(
                address = sender,
                subscriptionId = conversation.sms?.sub_id?.toInt() ?: -1,
                payload = payload,
            )
        } finally {
            payload.fill(0)
        }
        // A malformed internal part is still internal; never surface its bytes
        // as a regular attachment merely because protocol validation rejected it.
        return true
    }

}
