package com.afkanerd.deku.security

import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsDecision

/** Pure policy core kept separate so every send-path invariant can be unit tested. */
object SecureOutboundDecisionEngine {
    suspend fun decide(
        status: SecureSessionStatus,
        message: OutboundSms,
        trustedControlMessage: Boolean = false,
        encrypt: suspend () -> String?,
    ): OutboundSmsDecision {
        if(trustedControlMessage) {
            return OutboundSmsDecision.Allow(message)
        }

        return when(status) {
            SecureSessionStatus.PLAIN -> OutboundSmsDecision.Allow(message)
            SecureSessionStatus.SECURE_PENDING -> block("Secure session setup is not complete")
            SecureSessionStatus.SECURE_BROKEN -> block("Secure session is corrupted")
            SecureSessionStatus.SECURE_ESTABLISHED -> {
                if(message.transportData != null) {
                    return block("Binary SMS cannot bypass an established secure session")
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
