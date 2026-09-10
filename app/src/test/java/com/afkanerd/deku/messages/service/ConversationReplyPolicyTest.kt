package com.afkanerd.deku.messages.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationReplyPolicyTest {
    @Test
    fun phoneNumbersAndNumericShortCodesCanReceiveReplies() {
        assertTrue(ConversationReplyPolicy.canReply("+7 (962) 003-00-95"))
        assertTrue(ConversationReplyPolicy.canReply("900"))
    }

    @Test
    fun alphanumericSenderIdsCannotReceiveReplies() {
        assertFalse(ConversationReplyPolicy.canReply("Avito"))
        assertFalse(ConversationReplyPolicy.canReply("VK.RU"))
        assertFalse(ConversationReplyPolicy.canReply("MSG007"))
    }

    @Test
    fun groupCanReplyOnlyWhenEveryDestinationIsReplyable() {
        assertTrue(ConversationReplyPolicy.canReply(listOf("+79620030095", "+79966333395")))
        assertFalse(ConversationReplyPolicy.canReply(listOf("+79620030095", "Ya.Market")))
        assertFalse(ConversationReplyPolicy.canReply(emptyList()))
    }
}
