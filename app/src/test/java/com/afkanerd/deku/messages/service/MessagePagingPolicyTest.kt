package com.afkanerd.deku.messages.service

import androidx.paging.PagingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePagingPolicyTest {
    @Test
    fun conversationListDoesNotDropEarlierPagesWhileApproachingTheEnd() {
        val config = ConversationPagingPolicy.config()

        assertEquals(40, config.pageSize)
        assertEquals(80, config.initialLoadSize)
        assertEquals(PagingConfig.MAX_SIZE_UNBOUNDED, config.maxSize)
        assertFalse(config.enablePlaceholders)
    }

    @Test
    fun fiftyThousandMessageConversationKeepsABoundedUiWorkingSet() {
        val config = MessagePagingPolicy.config()

        assertEquals(50, config.pageSize)
        assertEquals(100, config.initialLoadSize)
        assertEquals(100, config.prefetchDistance)
        assertEquals(500, config.maxSize)
        assertFalse(config.enablePlaceholders)
        assertTrue(config.maxSize < 50_000 / 10)
    }
}
