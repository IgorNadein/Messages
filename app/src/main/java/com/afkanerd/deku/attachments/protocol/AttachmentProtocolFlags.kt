package com.afkanerd.deku.attachments.protocol

object AttachmentProtocolFlags {
    const val NONE: Int = 0
    const val UNPROTECTED: Int = 1
    const val MMS_PAYLOAD: Int = 1 shl 1
    const val CLOUD_PAYLOAD: Int = 1 shl 2
    const val SUPPORTED_MASK: Int = UNPROTECTED or MMS_PAYLOAD or CLOUD_PAYLOAD

    fun isSupported(flags: Int): Boolean = (flags and SUPPORTED_MASK.inv()) == 0 &&
        !((flags and MMS_PAYLOAD) != 0 && (flags and CLOUD_PAYLOAD) != 0)
    fun isUnprotected(flags: Int): Boolean = (flags and UNPROTECTED) != 0
    fun usesMmsPayload(flags: Int): Boolean = (flags and MMS_PAYLOAD) != 0
    fun usesCloudPayload(flags: Int): Boolean = (flags and CLOUD_PAYLOAD) != 0
}
