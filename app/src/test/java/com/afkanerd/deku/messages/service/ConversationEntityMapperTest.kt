package com.afkanerd.deku.messages.service

import android.provider.Telephony
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.SecurityEventKind
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.security.SecureMessageCodec
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import kotlin.io.encoding.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEntityMapperTest {
    @Test
    fun draftIsKeptOutOfTimeline() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_DRAFT,
            body = "unfinished",
        ).apply {
            sms = sms?.copy(type = Telephony.Sms.MESSAGE_TYPE_DRAFT)
        }

        assertFalse(ConversationEntityMapper.shouldExpose(entity))
    }

    @Test
    fun malformedControlPacketIsNotExposed() {
        val entity = conversation(type = Telephony.Sms.MESSAGE_TYPE_INBOX).copy(
            sms_data = byteArrayOf(0x01, 0x20, 0x02),
        )

        assertFalse(ConversationEntityMapper.shouldExpose(entity))
    }

    @Test
    fun keyExchangeBecomesSecurityEventInsteadOfControlMessage() {
        val payload = byteArrayOf(SecureMessageCodec.TYPE_REQUEST, 32) + ByteArray(32) { 7 }
        val entity = conversation(type = Telephony.Sms.MESSAGE_TYPE_INBOX).copy(sms_data = payload)

        assertTrue(ConversationEntityMapper.shouldExpose(entity))
        val item = ConversationEntityMapper.map(entity) as TimelineItem.SecurityEvent
        assertEquals(SecurityEventKind.REQUEST_RECEIVED, item.kind)
    }

    @Test
    fun incomingCiphertextNeverBecomesTextBubble() {
        val wire = byteArrayOf(SecureMessageCodec.TYPE_MESSAGE, 40, 48) +
            ByteArray(40) { 1 } + ByteArray(48) { 2 }
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = Base64.encode(wire),
        )

        val item = ConversationEntityMapper.map(entity) as TimelineItem.SecurityEvent
        assertEquals(SecurityEventKind.DECRYPTION_FAILED, item.kind)
    }

    @Test
    fun importedBase64KeyExchangeNeverBecomesTextBubble() {
        val wire = byteArrayOf(
            SecureMessageCodec.TYPE_ACCEPT,
            0,
            2,
        ) + ByteArray(128) { index -> (index + 1).toByte() }
        val imported = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            body = Base64.encode(wire),
        )

        val item = ConversationEntityMapper.map(imported) as TimelineItem.SecurityEvent

        assertEquals(SecurityEventKind.SESSION_ESTABLISHED, item.kind)
    }

    @Test
    fun importedLegacyBinaryBodyIsHiddenOnlyForKnownSecureConversation() {
        val legacyBinaryText = "2A06ydPÇØ#aNΨ0ØQqia)ÖØ%/4SeqwaÜ7,W=X\$XL.öØ"
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = legacyBinaryText,
        )

        val secureItem = ConversationEntityMapper.map(
            entity,
            secureConversation = true,
        ) as TimelineItem.SecurityEvent
        val plainItem = ConversationEntityMapper.map(
            entity,
            secureConversation = false,
        ) as TimelineItem.Text

        assertEquals(SecurityEventKind.DECRYPTION_FAILED, secureItem.kind)
        assertEquals(legacyBinaryText, plainItem.text)
    }

    @Test
    fun normalMultilingualTextInSecureConversationIsNotHidden() {
        val body = "Привет! Как твои дела? Alles gut."
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = body,
        )

        val item = ConversationEntityMapper.map(
            entity,
            secureConversation = true,
        ) as TimelineItem.Text

        assertEquals(body, item.text)
    }

    @Test
    fun searchResultNeverExposesSecureTransportCiphertext() {
        val wire = byteArrayOf(SecureMessageCodec.TYPE_MESSAGE, 40, 48) +
            ByteArray(40) { 1 } + ByteArray(48) { 2 }
        val encoded = Base64.encode(wire)

        val visible = SearchSnippetPolicy.safeSnippet(encoded)

        assertEquals("Protected message needs attention", visible)
        assertFalse(visible.contains(encoded))
    }

    @Test
    fun ordinarySearchSnippetRemainsUnchanged() {
        assertEquals("ordinary SMS", SearchSnippetPolicy.safeSnippet("ordinary SMS"))
    }

    @Test
    fun searchNeverExposesImportedKeyExchangeOrLegacyBinaryBody() {
        val exchange = byteArrayOf(
            SecureMessageCodec.TYPE_ACCEPT,
            0,
            2,
        ) + ByteArray(128) { index -> (index + 1).toByte() }
        val encodedExchange = Base64.encode(exchange)
        val legacyBinaryText = "2A06ydPÇØ#aNΨ0ØQqia)ÖØ%/4SeqwaÜ7,W=X\$XL.öØ"

        assertEquals(
            "Protected message needs attention",
            SearchSnippetPolicy.safeSnippet(encodedExchange),
        )
        assertEquals(
            "Protected message needs attention",
            SearchSnippetPolicy.safeSnippet(legacyBinaryText),
        )
    }

    @Test
    fun queuedSecureOutboundKeepsPlainDisplayText() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            body = "visible plaintext",
        ).copy(secure_transport_text = "ciphertext kept for sent callback")

        val item = ConversationEntityMapper.map(entity) as TimelineItem.Text
        assertEquals("visible plaintext", item.text)
        assertEquals(MessageDirection.OUTGOING, item.direction)
        assertTrue(item.isSecure)
    }

    @Test
    fun successfullyDecryptedIncomingMessageIsASecureTextBubble() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = "decrypted text",
        ).copy(secure_transport_text = "authenticated ciphertext")

        val item = ConversationEntityMapper.map(
            entity,
            decryptionFailureText = "could not decrypt",
        ) as TimelineItem.Text

        assertEquals("decrypted text", item.text)
        assertTrue(item.isSecure)
    }

    @Test
    fun storedDecryptionFailureRemainsASecurityEvent() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = "could not decrypt",
        ).copy(secure_transport_text = "authenticated ciphertext")

        val item = ConversationEntityMapper.map(
            entity,
            decryptionFailureText = "could not decrypt",
        ) as TimelineItem.SecurityEvent

        assertEquals(SecurityEventKind.DECRYPTION_FAILED, item.kind)
    }

    @Test
    fun sentDeliveredAndFailedStatesRemainVisibleAfterUiMigration() {
        val sent = ConversationEntityMapper.map(
            conversation(Telephony.Sms.MESSAGE_TYPE_SENT)
        ) as TimelineItem.Text
        val delivered = ConversationEntityMapper.map(
            conversation(Telephony.Sms.MESSAGE_TYPE_SENT).apply {
                sms?.status = Telephony.Sms.STATUS_COMPLETE
            }
        ) as TimelineItem.Text
        val failed = ConversationEntityMapper.map(
            conversation(Telephony.Sms.MESSAGE_TYPE_FAILED)
        ) as TimelineItem.Text

        assertEquals(DeliveryState.SENT, sent.deliveryState)
        assertEquals(DeliveryState.DELIVERED, delivered.deliveryState)
        assertEquals(DeliveryState.FAILED, failed.deliveryState)
    }

    @Test
    fun textOnlyGroupMmsIsRenderedAsTextInsteadOfEmptyMedia() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            body = "group MMS",
        ).copy(
            mms = SmsMmsNatives.Mms(
                _id = 101,
                thread_id = 7,
                date = 1234,
                date_sent = 0,
                msg_box = Telephony.Mms.MESSAGE_BOX_OUTBOX,
                text_only = 1,
            ),
            mms_text = "group MMS",
        )

        val item = ConversationEntityMapper.map(entity) as TimelineItem.Text

        assertEquals("group MMS", item.text)
        assertEquals(MessageDirection.OUTGOING, item.direction)
    }

    @Test
    fun incomingGroupMmsKeepsPerMessageAuthor() {
        val entity = conversation(
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = "from Alice",
        ).copy(sender_address = "+15550000001")
        val author = MessageAuthor(
            address = "+15550000001",
            displayName = "Alice",
            avatarUri = null,
        )

        val item = ConversationEntityMapper.map(entity, author = author) as TimelineItem.Text

        assertEquals(author, item.author)
        assertEquals("from Alice", item.text)
    }

    private fun conversation(type: Int, body: String = "hello") = Conversations(
        id = 42,
        sms = SmsMmsNatives.Sms(
            _id = 100,
            thread_id = 7,
            address = "+15550000000",
            date = 1234,
            date_sent = 1234,
            read = 1,
            status = Telephony.Sms.STATUS_NONE,
            type = type,
            body = body,
            sub_id = 1,
        ),
    )
}
