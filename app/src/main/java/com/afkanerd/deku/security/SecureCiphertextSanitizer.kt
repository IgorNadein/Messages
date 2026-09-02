package com.afkanerd.deku.security

import android.content.Context
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.makeE16PhoneNumber

/** Prevents legacy decryption failures from exposing transport ciphertext in the UI. */
object SecureCiphertextSanitizer {
    suspend fun sanitize(context: Context): Int {
        val dao = context.getDatabase().conversationsDao() ?: return 0
        var sanitized = 0
        val affectedThreads = mutableSetOf<Int>()
        val affectedChannels = mutableSetOf<Pair<String, Long>>()
        dao.getPotentialUndecryptedSecureMessages().forEach { conversation ->
            val transportText = conversation.sms?.body ?: return@forEach
            if(SecureMessageCodec.decodeTextOrNull(transportText) == null) return@forEach

            conversation.secure_transport_text = transportText
            conversation.sms?.body = context.getString(R.string.security_decryption_failed)
            dao.updateConversation(conversation)
            conversation.sms?.thread_id?.let(affectedThreads::add)
            conversation.sms?.address?.let {
                affectedChannels += context.makeE16PhoneNumber(it) to
                    (conversation.sms?.sub_id ?: -1)
            }
            sanitized++
        }
        affectedThreads.forEach { threadId ->
            dao.getLatestConversation(threadId)?.let(dao::update)
        }

        // Builds released before REQUEST_BROKEN already moved ciphertext out of
        // the body, but could not mark the session itself. Migrate those rows
        // exactly once so a later successful renewal is not re-broken at startup.
        val migrationPreferences = context.getSharedPreferences(
            MIGRATION_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        if(!migrationPreferences.getBoolean(BROKEN_STATE_MIGRATION, false)) {
            dao.getStoredSecureDecryptionFailures(
                context.getString(R.string.security_decryption_failed)
            ).forEach { conversation ->
                val transportText = conversation.secure_transport_text ?: return@forEach
                if(SecureMessageCodec.decodeTextOrNull(transportText) != null) {
                    conversation.sms?.address?.let {
                        affectedChannels += context.makeE16PhoneNumber(it) to
                            (conversation.sms?.sub_id ?: -1)
                    }
                }
            }
        }
        affectedChannels.forEach { (address, subscriptionId) ->
            EncryptionController.markSessionBroken(
                context,
                SecureChannelId.storageAddress(address, subscriptionId),
            )
        }
        migrationPreferences.edit()
            .putBoolean(BROKEN_STATE_MIGRATION, true)
            .apply()
        return sanitized
    }

    private const val MIGRATION_PREFERENCES = "secure_ciphertext_sanitizer"
    private const val BROKEN_STATE_MIGRATION = "broken_state_v1"
}
