package com.afkanerd.deku.security

import android.content.Context
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsDecision
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsPolicy

/** Enforces the secure-session decision at the transport boundary. */
class SecureOutboundSmsPolicy : OutboundSmsPolicy {
    override suspend fun evaluate(
        context: Context,
        message: OutboundSms,
    ): OutboundSmsDecision {
        if(message.forcePlainText) {
            return OutboundSmsDecision.Allow(
                message.copy(transportText = message.displayText, retryTransportText = null)
            )
        }
        if(!SecureSendPreference.isEnabled(context, message.address)) {
            return OutboundSmsDecision.Allow(message)
        }
        val status = SecureSessionStatusResolver.resolve(context, message.address)
        val trustedControlMessage = message.transportData?.let {
            EncryptionController.isLocallySignedKeyExchange(context, it)
        } ?: false

        return SecureOutboundDecisionEngine.decide(
            status,
            message,
            trustedControlMessage,
        ) {
            EncryptionController.encrypt(
                context = context,
                address = message.address,
                text = message.displayText,
                retryTransportText = message.retryTransportText,
            )
        }
    }
}
