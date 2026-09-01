package com.afkanerd.deku.security

import android.content.Context
import android.util.Log
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SavedEncryptedModes
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.getEncryptionModeStatesSync
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSmsPolicy
import com.afkanerd.smswithoutborders_libsmsmms.security.ProcessedInboundSms

/** Ensures authenticated ciphertext is resolved before any UI-observable insert. */
class SecureInboundSmsPolicy : InboundSmsPolicy {
    override suspend fun evaluate(
        context: Context,
        message: InboundSms,
    ): ProcessedInboundSms {
        if(SecureMessageCodec.decodeTextOrNull(message.transportText) == null) {
            return ProcessedInboundSms(displayText = message.transportText)
        }

        return try {
            val savedMode = SavedEncryptedModes.deserialize(
                context.getEncryptionModeStatesSync(message.address)
            )
            check(savedMode.mode == EncryptionController.SecureRequestMode.REQUEST_ACCEPTED) {
                "Secure session setup is not complete"
            }
            val plaintext = EncryptionController.decrypt(
                context,
                message.address,
                message.transportText,
            ) ?: throw SecurityException("Secure decryption returned no plaintext")
            ProcessedInboundSms(
                displayText = plaintext,
                secureTransportText = message.transportText,
            )
        } catch(e: Exception) {
            Log.w("SecureMessage", "Incoming secure SMS failed authentication", e)
            EncryptionController.markSessionBroken(context, message.address)
            ProcessedInboundSms(
                displayText = context.getString(R.string.security_decryption_failed),
                secureTransportText = message.transportText,
            )
        }
    }
}
