package com.afkanerd.deku.messages.service

import com.afkanerd.deku.Router.data.models.FTP
import com.afkanerd.deku.Router.data.models.GatewayServer
import com.afkanerd.deku.Router.data.models.SMTP
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewayPayloadFormat
import com.afkanerd.deku.messages.domain.GatewayProtocol
import com.afkanerd.deku.messages.domain.GatewaySummary

internal object GatewayConfigurationMapper {
    fun toSummary(entity: GatewayServer): GatewaySummary {
        val protocol = protocol(entity)
        val endpoint = when(protocol) {
            GatewayProtocol.HTTPS -> entity.URL.orEmpty()
            GatewayProtocol.SMTP -> entity.smtp?.smtp_host.orEmpty()
            GatewayProtocol.FTP -> entity.ftp.ftp_host.orEmpty()
        }
        return GatewaySummary(
            id = entity.id,
            protocol = protocol,
            endpoint = endpoint,
            tag = entity.tag,
            payloadFormat = if(entity.format == GatewayServer.BASE64_FORMAT) {
                GatewayPayloadFormat.BASE64_ONLY
            } else {
                GatewayPayloadFormat.ALL
            },
            updatedAtMillis = entity.date,
        )
    }

    fun toDraft(entity: GatewayServer): GatewayDraft? = when(protocol(entity)) {
        GatewayProtocol.HTTPS -> GatewayDraft(
            protocol = GatewayProtocol.HTTPS,
            url = entity.URL.orEmpty(),
            tag = entity.tag,
            payloadFormat = payloadFormat(entity),
        )
        GatewayProtocol.SMTP -> entity.smtp?.let { smtp ->
            GatewayDraft(
                protocol = GatewayProtocol.SMTP,
                tag = entity.tag,
                payloadFormat = payloadFormat(entity),
                smtpHost = smtp.smtp_host,
                smtpUsername = smtp.smtp_username,
                smtpPassword = smtp.smtp_password,
                smtpRecipient = smtp.smtp_recipient,
                smtpFrom = smtp.smtp_from,
                smtpSubject = smtp.smtp_subject,
                smtpPort = smtp.smtp_port.toString(),
            )
        }
        GatewayProtocol.FTP -> null
    }

    fun toEntity(
        id: Long,
        draft: GatewayDraft,
        nowMillis: Long,
    ): GatewayServer {
        require(draft.isValid()) { "Invalid gateway configuration" }
        return when(draft.protocol) {
            GatewayProtocol.HTTPS -> GatewayServer(
                id = id,
                smtp = null,
                ftp = FTP(),
                URL = draft.url.trim(),
                protocol = GatewayServer.POST_PROTOCOL,
                tag = draft.tag.trim(),
                format = draft.payloadFormat.storageValue(),
                date = nowMillis,
            )
            GatewayProtocol.SMTP -> GatewayServer(
                id = id,
                smtp = SMTP(
                    smtp_host = draft.smtpHost.trim(),
                    smtp_username = draft.smtpUsername.trim(),
                    smtp_password = draft.smtpPassword,
                    smtp_recipient = draft.smtpRecipient.trim(),
                    smtp_from = draft.smtpFrom.trim(),
                    smtp_subject = draft.smtpSubject.trim(),
                    smtp_port = requireNotNull(draft.smtpPort.toIntOrNull()),
                ),
                ftp = FTP(),
                URL = null,
                protocol = SMTP.PROTOCOL,
                tag = draft.tag.trim(),
                format = draft.payloadFormat.storageValue(),
                date = nowMillis,
            )
            GatewayProtocol.FTP -> error("FTP editing is not exposed by the current UI")
        }
    }

    private fun protocol(entity: GatewayServer): GatewayProtocol = when {
        entity.smtp != null || entity.protocol == SMTP.PROTOCOL -> GatewayProtocol.SMTP
        entity.protocol == FTP.PROTOCOL -> GatewayProtocol.FTP
        else -> GatewayProtocol.HTTPS
    }

    private fun payloadFormat(entity: GatewayServer): GatewayPayloadFormat =
        if(entity.format == GatewayServer.BASE64_FORMAT) GatewayPayloadFormat.BASE64_ONLY
        else GatewayPayloadFormat.ALL

    private fun GatewayPayloadFormat.storageValue(): String = when(this) {
        GatewayPayloadFormat.ALL -> GatewayServer.ALL_FORMAT
        GatewayPayloadFormat.BASE64_ONLY -> GatewayServer.BASE64_FORMAT
    }
}
