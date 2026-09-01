package com.afkanerd.deku.messages.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMessageRouteMapperTest {
    @Test
    fun sharedTextOpensRecipientPickerWithoutLosingUnicode() {
        assertEquals(
            ExternalMessageRoute.RecipientPicker("Привет 👋\nsecure later"),
            ExternalMessageRouteMapper.fromSharedText("Привет 👋\nsecure later"),
        )
    }

    @Test
    fun sendToExtractsAddressAndPercentEncodedBodyWithoutTreatingPlusAsSpace() {
        assertEquals(
            ExternalMessageRoute.Conversation(
                address = "+79991234567",
                text = "Код + 42",
            ),
            ExternalMessageRouteMapper.fromSendTo(
                dataUri = "smsto:%2B79991234567?body=%D0%9A%D0%BE%D0%B4%20%2B%2042",
                smsBody = null,
                sharedText = null,
            ),
        )
    }

    @Test
    fun explicitSmsBodyWinsOverUriBodyAndUnsupportedSchemesAreRejected() {
        assertEquals(
            ExternalMessageRoute.Conversation("+15551234567", "from extra"),
            ExternalMessageRouteMapper.fromSendTo(
                dataUri = "sms:+15551234567?body=from%20uri",
                smsBody = "from extra",
                sharedText = "shared fallback",
            ),
        )
        assertNull(
            ExternalMessageRouteMapper.fromSendTo(
                dataUri = "https://example.com",
                smsBody = "must not route",
                sharedText = null,
            )
        )
    }

    @Test
    fun notificationRequiresANonBlankAddress() {
        assertEquals(
            ExternalMessageRoute.Conversation("+15550001111", null),
            ExternalMessageRouteMapper.fromNotification(" +15550001111 "),
        )
        assertNull(ExternalMessageRouteMapper.fromNotification("  "))
    }
}
