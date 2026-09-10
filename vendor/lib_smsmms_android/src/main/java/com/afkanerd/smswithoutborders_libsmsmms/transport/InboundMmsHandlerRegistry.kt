package com.afkanerd.smswithoutborders_libsmsmms.transport

import android.content.Context
import android.net.Uri

fun interface InboundMmsHandler {
    /** Return true when this is an app-internal MMS part that must not enter normal MMS storage. */
    suspend fun consume(context: Context, contentUri: Uri): Boolean
}

object InboundMmsHandlerRegistry {
    @Volatile
    var handler: InboundMmsHandler? = null

    suspend fun consume(context: Context, contentUri: Uri): Boolean =
        handler?.consume(context, contentUri) == true
}

fun interface InternalMmsSentHandler {
    /** Return true when the callback belongs to an app-internal MMS transport. */
    suspend fun consume(
        context: Context,
        transferId: String,
        partIndex: Int,
        successful: Boolean,
        resultCode: Int,
    ): Boolean
}

object InternalMmsSentHandlerRegistry {
    @Volatile
    var handler: InternalMmsSentHandler? = null

    suspend fun consume(
        context: Context,
        transferId: String?,
        partIndex: Int,
        successful: Boolean,
        resultCode: Int,
    ): Boolean = transferId != null && handler?.consume(
        context,
        transferId,
        partIndex,
        successful,
        resultCode,
    ) == true
}

const val INTERNAL_MEDIA_MIME_TYPE = "application/vnd.deku.media-part"
const val INTERNAL_MMS_TRANSFER_ID_EXTRA = "deku_internal_mms_transfer_id"
const val INTERNAL_MMS_PART_INDEX_EXTRA = "deku_internal_mms_part_index"

private val INTERNAL_MMS_FILENAME_REGEX =
    Regex("^([0-9a-f]{32})-[1-9][0-9]*-of-[1-9][0-9]*\\.dmm$")

fun internalMmsTransferIdFromFilename(filename: String?): String? = filename
    ?.let(INTERNAL_MMS_FILENAME_REGEX::matchEntire)
    ?.groupValues
    ?.getOrNull(1)

fun isInternalMmsTransportPart(mimeType: String?, filename: String?): Boolean =
    mimeType == INTERNAL_MEDIA_MIME_TYPE || internalMmsTransferIdFromFilename(filename) != null
