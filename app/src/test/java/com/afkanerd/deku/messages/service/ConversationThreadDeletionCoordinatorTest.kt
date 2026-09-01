package com.afkanerd.deku.messages.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationThreadDeletionCoordinatorTest {
    @Test
    fun legacyOrderDeletesLocalConversationBeforeOptionalSystemSmsThread() {
        val calls = mutableListOf<String>()

        val result = ConversationThreadDeletionCoordinator.delete(
            threadId = 42,
            deleteFromSystemDatabase = true,
            deleteLocal = { calls += "local" },
            deleteSystem = { calls += "system:$it" },
        )

        assertTrue(result)
        assertEquals(listOf("local", "system:42"), calls)
    }

    @Test
    fun disabledSystemDeletionOnlyRemovesLocalConversation() {
        val calls = mutableListOf<String>()

        val result = ConversationThreadDeletionCoordinator.delete(
            threadId = 42,
            deleteFromSystemDatabase = false,
            deleteLocal = { calls += "local" },
            deleteSystem = { calls += "system:$it" },
        )

        assertTrue(result)
        assertEquals(listOf("local"), calls)
    }

    @Test
    fun localFailureDoesNotAttemptSystemDeletion() {
        var systemDeletionCalled = false

        val result = ConversationThreadDeletionCoordinator.delete(
            threadId = 42,
            deleteFromSystemDatabase = true,
            deleteLocal = { error("local failure") },
            deleteSystem = { systemDeletionCalled = true },
        )

        assertFalse(result)
        assertFalse(systemDeletionCalled)
    }
}
