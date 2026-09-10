package com.afkanerd.deku.attachments

/**
 * Protection is deliberately independent from the carrier transport.
 * UNPROTECTED frames keep integrity checks, but their transfer key is sent in the clear and
 * therefore provide no confidentiality or peer authentication.
 */
enum class AttachmentProtection {
    SECURE,
    UNPROTECTED,
}

enum class AttachmentWireTransport(val wireCode: Int) {
    DATA_SMS(0),
    MMS(1),
    CLOUD_STORAGE(2),
    /** Protocol frames encoded as ordinary multipart text SMS. */
    STANDARD_SMS(3),
    ;

    companion object {
        fun fromWireCode(code: Int): AttachmentWireTransport? =
            entries.firstOrNull { it.wireCode == code }
    }
}
