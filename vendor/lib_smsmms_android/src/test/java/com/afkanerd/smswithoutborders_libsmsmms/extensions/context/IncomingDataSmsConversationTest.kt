package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.provider.Telephony
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingDataSmsConversationTest {
    @Test
    fun `reassembled payload is copied into the logical incoming message`() {
        val payload = ByteArray(131) { (it * 17).toByte() }

        val conversation = newIncomingDataSmsConversation(
            address = "+79620030095",
            subscriptionId = 12,
            payload = payload,
            date = 2000L,
            dateSent = 1000L,
            threadId = 42,
        )

        assertArrayEquals(payload, conversation.sms_data)
        assertEquals(Telephony.Sms.MESSAGE_TYPE_INBOX, conversation.sms?.type)
        assertEquals(Telephony.Sms.STATUS_NONE, conversation.sms?.status)
        assertEquals(12L, conversation.sms?.sub_id)
        assertEquals(42, conversation.sms?.thread_id)

        payload[0] = 99
        assertEquals(0, conversation.sms_data?.get(0)?.toInt())
    }
}
