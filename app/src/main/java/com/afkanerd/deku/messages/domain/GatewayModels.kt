package com.afkanerd.deku.messages.domain

enum class GatewayProtocol {
    HTTPS,
    SMTP,
    FTP,
}

enum class GatewayPayloadFormat {
    ALL,
    BASE64_ONLY,
}

/** Passwords are intentionally absent from the continuously observed list model. */
data class GatewaySummary(
    val id: Long,
    val protocol: GatewayProtocol,
    val endpoint: String,
    val tag: String,
    val payloadFormat: GatewayPayloadFormat,
    val updatedAtMillis: Long?,
)

/** Mutable form data is loaded only while its editor is open. */
data class GatewayDraft(
    val protocol: GatewayProtocol,
    val url: String = "",
    val tag: String = "",
    val payloadFormat: GatewayPayloadFormat = GatewayPayloadFormat.ALL,
    val smtpHost: String = "",
    val smtpUsername: String = "",
    val smtpPassword: String = "",
    val smtpRecipient: String = "",
    val smtpFrom: String = "",
    val smtpSubject: String = "",
    val smtpPort: String = DEFAULT_SMTP_PORT.toString(),
) {
    fun isValid(): Boolean = when(protocol) {
        GatewayProtocol.HTTPS -> url.isNotBlank()
        GatewayProtocol.SMTP -> smtpHost.isNotBlank() &&
            smtpUsername.isNotBlank() &&
            smtpPassword.isNotBlank() &&
            smtpRecipient.isNotBlank() &&
            smtpFrom.isNotBlank() &&
            smtpPort.toIntOrNull() in 1..65535
        GatewayProtocol.FTP -> false
    }

    companion object {
        const val DEFAULT_SMTP_PORT = 587
    }
}
