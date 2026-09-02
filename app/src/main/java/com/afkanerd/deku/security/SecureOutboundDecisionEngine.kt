package com.afkanerd.deku.security

import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsDecision

/** Pure policy core kept separate so every send-path invariant can be unit tested. */
object SecureOutboundDecisionEngine {
    suspend fun decide(
        status: SecureSessionStatus,
        message: OutboundSms,
        trustedControlMessage: Boolean = false,
        forcePlainText: Boolean = false,
        encrypt: suspend () -> String?,
    ): OutboundSmsDecision {
        if(forcePlainText) {
            return OutboundSmsDecision.Allow(
                message.copy(transportText = message.displayText, retryTransportText = null)
            )
        }
        if(trustedControlMessage) {
            return OutboundSmsDecision.Allow(message)
        }

        return when(status) {
            SecureSessionStatus.PLAIN -> block("Secure session is not established")
            SecureSessionStatus.SECURE_PENDING,
            SecureSessionStatus.SECURE_BROKEN -> block("Secure session is not ready")
            SecureSessionStatus.SECURE_ESTABLISHED -> {
                if(message.transportData != null) {
                    return OutboundSmsDecision.Allow(message)
                }

                val cipherText = try {
                    encrypt()
                } catch (error: Exception) {
                    return block("Secure message encryption failed", error)
                }

                if(cipherText.isNullOrEmpty() ||
                    SecureMessageCodec.decodeTextOrNull(cipherText) == null
                ) {
                    block("Encryption did not produce a valid secure payload")
                } else {
                    OutboundSmsDecision.Allow(message.copy(transportText = cipherText))
                }
            }
        }
    }

    private fun block(reason: String, cause: Throwable? = null) =
        OutboundSmsDecision.Block(reason, cause)
}
