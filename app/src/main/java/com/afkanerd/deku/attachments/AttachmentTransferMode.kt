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

enum class AttachmentWireTransport {
    DATA_SMS,
    MMS,
    CLOUD_STORAGE,
}
