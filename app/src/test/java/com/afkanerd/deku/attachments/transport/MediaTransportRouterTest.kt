package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.AttachmentProtection
import com.afkanerd.deku.messages.domain.MediaTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTransportRouterTest {
    @Test
    fun `ordinary media defaults to compatible mms`() {
        assertEquals(
            MediaTransportRouter.Route.Mms,
            MediaTransportRouter.resolve(MediaTransport.MMS, 1, secureOneToOne = false),
        )
    }

    @Test
    fun `data sms in ordinary chat is explicitly unprotected`() {
        assertEquals(
            MediaTransportRouter.Route.DataSms(AttachmentProtection.UNPROTECTED),
            MediaTransportRouter.resolve(MediaTransport.DATA_SMS, 1, secureOneToOne = false),
        )
    }

    @Test
    fun `ordinary sms media keeps protection independent from its envelope`() {
        assertEquals(
            MediaTransportRouter.Route.StandardSms(AttachmentProtection.UNPROTECTED),
            MediaTransportRouter.resolve(
                MediaTransport.STANDARD_SMS,
                1,
                secureOneToOne = false,
            ),
        )
        assertEquals(
            MediaTransportRouter.Route.StandardSms(AttachmentProtection.SECURE),
            MediaTransportRouter.resolve(
                MediaTransport.STANDARD_SMS,
                1,
                secureOneToOne = true,
            ),
        )
    }

    @Test
    fun `secure media can never be downgraded by transport selection`() {
        MediaTransport.entries.forEach { selected ->
            val route = MediaTransportRouter.resolve(selected, 1, secureOneToOne = true)
            if(route is MediaTransportRouter.Route.DataSms) {
                assertEquals(AttachmentProtection.SECURE, route.protection)
            }
        }
    }

    @Test
    fun `data sms group is rejected until one logical group transfer is supported`() {
        assertEquals(
            MediaTransportRouter.Route.UnsupportedGroupDataSms,
            MediaTransportRouter.resolve(MediaTransport.DATA_SMS, 2, secureOneToOne = false),
        )
    }
}
