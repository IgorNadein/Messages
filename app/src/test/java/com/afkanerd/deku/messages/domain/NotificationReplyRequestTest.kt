package com.afkanerd.deku.messages.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationReplyRequestTest {
    @Test
    fun validUnicodeReplyPreservesTextAndIncomingSim() {
        val request = requireNotNull(
            NotificationReplyRequest.create(
                address = "  +79990000000  ",
                threadId = 42,
                subscriptionId = 22,
                text = "Ответ 🔐",
            )
        )

        assertEquals("+79990000000", request.address)
        assertEquals(42, request.threadId)
        assertEquals(22L, request.subscriptionId)
        assertEquals("Ответ 🔐", request.text)
    }

    @Test
    fun incompleteOrInvalidReplayBroadcastIsRejectedBeforeTransport() {
        assertNull(NotificationReplyRequest.create(null, 42, 22, "reply"))
        assertNull(NotificationReplyRequest.create("  ", 42, 22, "reply"))
        assertNull(NotificationReplyRequest.create("+1", -1, 22, "reply"))
        assertNull(NotificationReplyRequest.create("+1", 42, -1, "reply"))
        assertNull(NotificationReplyRequest.create("+1", 42, 22, null))
        assertNull(NotificationReplyRequest.create("+1", 42, 22, ""))
    }
}
