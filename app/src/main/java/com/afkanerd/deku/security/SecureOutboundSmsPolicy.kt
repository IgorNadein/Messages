package com.afkanerd.deku.security

import android.content.Context
import android.util.Base64
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
        if(!SecureSendPreference.isEnabled(context, message.address, message.subscriptionId)) {
            return OutboundSmsDecision.Allow(message)
        }
        val channelAddress = SecureChannelId.storageAddress(
            message.address,
            message.subscriptionId,
        )
        val status = SecureSessionStatusResolver.resolve(
            context,
            message.address,
            message.subscriptionId,
        )
        val trustedControlMessage = message.transportData?.let {
            EncryptionController.isLocallySignedKeyExchange(context, it)
        } ?: false

        val decision = SecureOutboundDecisionEngine.decide(
            status,
            message,
            trustedControlMessage,
            forcePlainText = message.forcePlainText,
        ) {
            EncryptionController.encrypt(
                context = context,
                address = channelAddress,
                text = message.displayText,
                retryTransportText = message.retryTransportText,
            )
        }
        if(decision !is OutboundSmsDecision.Allow ||
            status != SecureSessionStatus.SECURE_ESTABLISHED ||
            message.forcePlainText ||
            message.transportData != null ||
            !SecureMessageTransportPreference.shouldUseData(context, message.address)
        ) return decision

        val rawEnvelope = runCatching {
            Base64.decode(decision.message.transportText, Base64.NO_WRAP)
        }.getOrNull() ?: return decision
        if(SecureMessageCodec.decodeMessageOrNull(rawEnvelope) == null) return decision
        return OutboundSmsDecision.Allow(
            decision.message.copy(transportData = rawEnvelope)
        )
    }
}
