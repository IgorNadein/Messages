package com.afkanerd.deku.messages.service

import android.provider.Telephony
import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.SecurityEventKind
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.security.SecureMessageCodec
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations

/** Converts persistence entities into UI-safe domain items before Compose can observe them. */
object ConversationEntityMapper {
    fun shouldExpose(entity: Conversations): Boolean {
        if(entity.sms?.type == Telephony.Sms.MESSAGE_TYPE_DRAFT) return false
        val data = entity.sms_data
        if(data == null) return true
        return SecureMessageCodec.decodeKeyExchangeOrNull(data) != null ||
            SecureMessageCodec.decodeMessageOrNull(data) != null
    }

    fun map(
        entity: Conversations,
        secureConversation: Boolean = false,
        author: MessageAuthor? = null,
        decryptionFailureText: String? = null,
    ): TimelineItem {
        val sms = requireNotNull(entity.sms) { "Conversation has no SMS envelope" }
        val stableId = "message-${entity.id}"
        val direction = direction(sms.type)

        entity.sms_data?.let { data ->
            SecureMessageCodec.decodeKeyExchangeOrNull(data)?.let { exchange ->
                val kind = when(exchange.type) {
                    SecureMessageCodec.TYPE_ACCEPT -> SecurityEventKind.SESSION_ESTABLISHED
                    else -> if(direction == MessageDirection.OUTGOING) {
                        SecurityEventKind.REQUEST_SENT
                    } else {
                        SecurityEventKind.REQUEST_RECEIVED
                    }
                }
                return TimelineItem.SecurityEvent(stableId, sms.date, kind)
            }
        }

        val body = sms.body.orEmpty()
        SecureMessageCodec.decodeKeyExchangeTextOrNull(body)?.let { exchange ->
            val kind = when(exchange.type) {
                SecureMessageCodec.TYPE_ACCEPT -> SecurityEventKind.SESSION_ESTABLISHED
                else -> if(direction == MessageDirection.OUTGOING) {
                    SecurityEventKind.REQUEST_SENT
                } else {
                    SecurityEventKind.REQUEST_RECEIVED
                }
            }
            return TimelineItem.SecurityEvent(stableId, sms.date, kind)
        }

        if((direction == MessageDirection.INCOMING &&
                entity.secure_transport_text != null &&
                decryptionFailureText != null &&
                body == decryptionFailureText) ||
            SecureMessageCodec.decodeTextOrNull(body) != null ||
            (secureConversation && looksLikeLegacyBinaryPayload(body))
        ) {
            return TimelineItem.SecurityEvent(
                stableId = stableId,
                timestampMillis = sms.date,
                kind = SecurityEventKind.DECRYPTION_FAILED,
                direction = direction,
            )
        }

        if(entity.mms_content_uri != null || entity.mms_mimetype != null) {
            return TimelineItem.Media(
                stableId = stableId,
                timestampMillis = sms.date,
                uri = entity.mms_content_uri,
                fileName = entity.mms_filename,
                mimeType = entity.mms_mimetype,
                caption = entity.mms_text ?: sms.body,
                direction = direction,
                deliveryState = deliveryState(sms.type, sms.status),
                isSecure = !entity.secure_transport_text.isNullOrEmpty(),
                subscriptionId = sms.sub_id,
                author = author,
            )
        }

        return TimelineItem.Text(
            stableId = stableId,
            timestampMillis = sms.date,
            text = sms.body.orEmpty(),
            direction = direction,
            deliveryState = deliveryState(sms.type, sms.status),
            isSecure = !entity.secure_transport_text.isNullOrEmpty(),
            subscriptionId = sms.sub_id,
            author = author,
        )
    }

    /**
     * Old Data-SMS rows imported from an OEM Telephony provider can contain
     * decoded binary bytes instead of the original userData. Restrict this
     * fallback to a conversation already known to be secure and require a
     * high-diversity symbol-heavy body so normal multilingual SMS remain visible.
     */
    internal fun looksLikeLegacyBinaryPayload(body: String): Boolean {
        if(body.length !in MIN_LEGACY_BINARY_LENGTH..MAX_LEGACY_BINARY_LENGTH) return false
        if(body.any(Char::isISOControl)) return true

        val nonAscii = body.count { it.code > 0x7f }
        val punctuationOrSymbols = body.count {
            !it.isLetterOrDigit() && !it.isWhitespace()
        }
        val visibleLength = body.count { !it.isWhitespace() }.coerceAtLeast(1)
        val diversity = body.asSequence()
            .filterNot(Char::isWhitespace)
            .distinct()
            .count()
            .toFloat() / visibleLength
        return nonAscii >= 4 &&
            punctuationOrSymbols >= MIN_BINARY_SYMBOLS &&
            diversity >= MIN_BINARY_DIVERSITY
    }

    private fun direction(type: Int): MessageDirection = when(type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageDirection.INCOMING
        else -> MessageDirection.OUTGOING
    }

    private fun deliveryState(type: Int, status: Int): DeliveryState = when {
        type == Telephony.Sms.MESSAGE_TYPE_INBOX -> DeliveryState.RECEIVED
        type == Telephony.Sms.MESSAGE_TYPE_FAILED || status == Telephony.Sms.STATUS_FAILED ->
            DeliveryState.FAILED
        type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX -> DeliveryState.QUEUED
        status == Telephony.Sms.STATUS_COMPLETE -> DeliveryState.DELIVERED
        else -> DeliveryState.SENT
    }

    private const val MIN_LEGACY_BINARY_LENGTH = 24
    private const val MAX_LEGACY_BINARY_LENGTH = 512
    private const val MIN_BINARY_SYMBOLS = 8
    private const val MIN_BINARY_DIVERSITY = 0.55f
}
