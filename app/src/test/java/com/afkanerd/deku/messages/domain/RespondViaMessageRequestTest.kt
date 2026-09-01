package com.afkanerd.deku.messages.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RespondViaMessageRequestTest {
    @Test
    fun validSystemReplyPreservesUnicodeAddressPlusAndText() {
        assertEquals(
            RespondViaMessageRequest("+79990000000", "Перезвоню позже 👋"),
            RespondViaMessageRequest.create(
                dataUri = "smsto:%2B79990000000",
                text = "Перезвоню позже 👋",
            ),
        )
    }

    @Test
    fun missingTextAddressOrMessageSchemeIsRejectedBeforeTransport() {
        assertNull(RespondViaMessageRequest.create("smsto:+79990000000", null))
        assertNull(RespondViaMessageRequest.create("smsto:+79990000000", "  "))
        assertNull(RespondViaMessageRequest.create("smsto:", "reply"))
        assertNull(RespondViaMessageRequest.create("https://example.com", "reply"))
    }
}
