package com.afkanerd.deku.messages.service

import com.afkanerd.deku.Router.data.models.GatewayServer
import com.afkanerd.deku.Router.data.models.SMTP
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewayPayloadFormat
import com.afkanerd.deku.messages.domain.GatewayProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayConfigurationMapperTest {
    @Test
    fun smtpDraftMapsToSmtpProtocolWithoutSwappingRecipientAndFrom() {
        val entity = GatewayConfigurationMapper.toEntity(
            id = 7,
            nowMillis = 1234,
            draft = GatewayDraft(
                protocol = GatewayProtocol.SMTP,
                tag = "alerts",
                payloadFormat = GatewayPayloadFormat.BASE64_ONLY,
                smtpHost = "smtp.example.org",
                smtpUsername = "user",
                smtpPassword = "secret",
                smtpRecipient = "recipient@example.org",
                smtpFrom = "sender@example.org",
                smtpSubject = "Messages",
                smtpPort = "465",
            ),
        )

        assertEquals(SMTP.PROTOCOL, entity.protocol)
        assertNull(entity.URL)
        assertEquals("recipient@example.org", entity.smtp?.smtp_recipient)
        assertEquals("sender@example.org", entity.smtp?.smtp_from)
        assertEquals(465, entity.smtp?.smtp_port)
        assertEquals(GatewayServer.BASE64_FORMAT, entity.format)
    }

    @Test
    fun existingSmtpLoadsBackIntoSmtpEditor() {
        val entity = GatewayServer(
            id = 9,
            smtp = SMTP(
                smtp_host = "mail.example.org",
                smtp_username = "name",
                smtp_password = "password",
                smtp_recipient = "to@example.org",
                smtp_from = "from@example.org",
                smtp_subject = "Subject",
                smtp_port = 587,
            ),
            protocol = SMTP.PROTOCOL,
        )

        val draft = requireNotNull(GatewayConfigurationMapper.toDraft(entity))

        assertEquals(GatewayProtocol.SMTP, draft.protocol)
        assertEquals("to@example.org", draft.smtpRecipient)
        assertEquals("from@example.org", draft.smtpFrom)
        assertEquals("587", draft.smtpPort)
    }
}
