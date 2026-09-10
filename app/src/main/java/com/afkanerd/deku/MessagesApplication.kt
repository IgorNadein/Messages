package com.afkanerd.deku

import android.app.Application
import com.afkanerd.deku.security.SecureOutboundSmsPolicy
import com.afkanerd.deku.security.SecureInboundSmsPolicy
import com.afkanerd.deku.security.SecureDataSmsHandler
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.deku.attachments.transport.MmsAttachmentInboundHandler
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.messages.service.AndroidAppSettingsService
import com.afkanerd.deku.messages.service.AndroidDeveloperToolsService
import com.afkanerd.deku.messages.service.AndroidMessageService
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSmsPolicyRegistry
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsPolicyRegistry
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundDataSmsHandlerRegistry
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundMmsHandlerRegistry
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundTextSmsHandlerRegistry
import com.afkanerd.deku.attachments.transport.CompatibleTextSmsCodec
import com.afkanerd.smswithoutborders_libsmsmms.transport.InternalMmsSentHandlerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessagesApplication : Application() {
    val messageService: MessageService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMessageService(this)
    }
    val appSettingsService: AppSettingsService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAppSettingsService(this)
    }
    val developerToolsService: DeveloperToolsService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidDeveloperToolsService(this)
    }

    override fun onCreate() {
        super.onCreate()
        InboundSmsPolicyRegistry.policy = SecureInboundSmsPolicy()
        OutboundSmsPolicyRegistry.policy = SecureOutboundSmsPolicy()
        val attachmentManager = AttachmentManager.get(this)
        val secureDataHandler = SecureDataSmsHandler()
        InboundDataSmsHandlerRegistry.handler = { context, address, subscriptionId, payload ->
            secureDataHandler.consume(context, address, subscriptionId, payload) ||
                attachmentManager.consume(context, address, subscriptionId, payload)
        }
        InboundTextSmsHandlerRegistry.handler = { context, address, subscriptionId, text ->
            CompatibleTextSmsCodec.decodeOrNull(text)?.let { frame ->
                attachmentManager.consumeCompatibleText(
                    context,
                    address,
                    subscriptionId,
                    com.afkanerd.deku.attachments.protocol.SmsFrameCodec.encode(frame),
                )
            } == true
        }
        InboundMmsHandlerRegistry.handler = MmsAttachmentInboundHandler(attachmentManager)
        InternalMmsSentHandlerRegistry.handler = { _, transferId, partIndex, successful, resultCode ->
            attachmentManager.onMmsPartSent(
                transferId,
                partIndex,
                successful,
                resultCode,
            )
        }
        CoroutineScope(Dispatchers.IO).launch { attachmentManager.resumePending() }
    }
}
