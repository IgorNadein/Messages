package com.afkanerd.deku.messages.service

import android.content.Context
import androidx.core.net.toUri
import android.provider.Telephony
import com.afkanerd.deku.DefaultSMS.extensions.context.clearRawColumnGuesses
import com.afkanerd.deku.DefaultSMS.extensions.context.exportRawWithColumnGuesses
import com.afkanerd.deku.DefaultSMS.extensions.context.importRawColumnGuesses
import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.messages.domain.NativeMessageImportSummary
import com.afkanerd.lib_smsmms_android.R as SmsLibraryR
import com.afkanerd.smswithoutborders_libsmsmms.activities.DeveloperModeNotificationCls
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getThreadId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getUriForDrawable
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeveloperToolsService(context: Context) : DeveloperToolsService {
    private val appContext = context.applicationContext

    override suspend fun triggerSampleMmsNotification(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val address = "+237123456789"
            val contentUri = appContext.getUriForDrawable(
                SmsLibraryR.drawable.egs_cyberpunk2077_cdprojektred_s1_03_2560x1440_359e77d3cd0a40aebf3bbc130d14c5c7,
            ).toString()
            val conversation = Conversations(
                mms_text = "Hello world MMS",
                mms_mimetype = "image/jpeg",
            ).apply {
                sms = SmsMmsNatives.Sms(
                    _id = now,
                    thread_id = appContext.getThreadId(address),
                    address = address,
                    date = now,
                    date_sent = now,
                    read = 0,
                    status = Telephony.Sms.STATUS_NONE,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    body = "Hello world MMS",
                    sub_id = -1,
                )
                mms_content_uri = contentUri
                mms = SmsMmsNatives.Mms(
                    _id = -1,
                    thread_id = -1,
                    date = now,
                    date_sent = now,
                    sub = "New MMS",
                    msg_box = Telephony.Mms.MESSAGE_BOX_INBOX,
                )
            }

            appContext.getDatabase().conversationsDao()?.insert(conversation)
                ?: error("Conversation database is unavailable")
            appContext.notify(conversation, DeveloperModeNotificationCls::class.java)
        }.isSuccess
    }

    override suspend fun clearLocalMessageHistory(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dao = appContext.getDatabase().conversationsDao()
                ?: error("Conversation database is unavailable")
            // Keep both tables consistent inside the DAO's existing transaction.
            dao.insertAll(emptyList(), deleteDb = true)
        }.isSuccess
    }

    override suspend fun exportNativeMessageDatabase(
        destinationUri: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = appContext.exportRawWithColumnGuesses().encodeToByteArray()
            val output = requireNotNull(
                appContext.contentResolver.openOutputStream(destinationUri.toUri(), "w")
            ) { "Unable to open export destination" }
            output.use { it.write(payload) }
        }.isSuccess
    }

    override suspend fun importNativeMessageDatabase(
        sourceUri: String,
    ): NativeMessageImportSummary = withContext(Dispatchers.IO) {
        val input = requireNotNull(
            appContext.contentResolver.openInputStream(sourceUri.toUri())
        ) { "Unable to open import source" }
        val payload = input.bufferedReader().use { it.readText() }
        val details = appContext.importRawColumnGuesses(payload)
        NativeMessageImportSummary(
            mmsCount = details.mmsCount,
            mmsPartCount = details.mmsPartCount,
            mmsAddressCount = details.mmsAddrCount,
        )
    }

    override suspend fun clearNativeMessageDatabase(): Boolean = withContext(Dispatchers.IO) {
        runCatching { appContext.clearRawColumnGuesses() }.isSuccess
    }
}
