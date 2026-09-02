package com.afkanerd.deku.attachments.protocol

object AttachmentProtocolFlags {
    const val NONE: Int = 0
    const val UNPROTECTED: Int = 1
    const val SUPPORTED_MASK: Int = UNPROTECTED

    fun isSupported(flags: Int): Boolean = flags and SUPPORTED_MASK.inv() == 0
    fun isUnprotected(flags: Int): Boolean = flags and UNPROTECTED != 0
}
