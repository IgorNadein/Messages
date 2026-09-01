package com.afkanerd.deku.messages.service

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMessagePolicyTest {
    @Test
    fun notificationReplyToGroupUsesMmsInsteadOfCommaAddressedSms() {
        val address = "+15550000001,+15550000002"

        assertEquals(
            ReplyTransport.GROUP_MMS,
            GroupMessagePolicy.notificationReplyTransport(address),
        )
        assertEquals(
            listOf("+15550000001", "+15550000002"),
            GroupMessagePolicy.recipients(address),
        )
    }

    @Test
    fun notificationReplyToOneRecipientStaysSms() {
        assertEquals(
            ReplyTransport.SMS,
            GroupMessagePolicy.notificationReplyTransport("+15550000001"),
        )
    }
}
