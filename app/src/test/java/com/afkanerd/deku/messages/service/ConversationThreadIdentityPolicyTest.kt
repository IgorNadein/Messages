package com.afkanerd.deku.messages.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConversationThreadIdentityPolicyTest {
    @Test
    fun normalizedAliasesOfOneNumberShareAnInboxIdentity() {
        assertEquals(
            ConversationThreadIdentityPolicy.key(listOf("+79620244454"), null),
            ConversationThreadIdentityPolicy.key(listOf("+79620244454"), null),
        )
    }

    @Test
    fun differentNumbersOfOneContactShareAnInboxIdentity() {
        assertEquals(
            ConversationThreadIdentityPolicy.key(listOf("+79620244454"), 10512L),
            ConversationThreadIdentityPolicy.key(listOf("+79990000000"), 10512L),
        )
    }

    @Test
    fun unrelatedRecipientsRemainSeparate() {
        assertNotEquals(
            ConversationThreadIdentityPolicy.key(listOf("+79620244454"), null),
            ConversationThreadIdentityPolicy.key(listOf("+79990000000"), null),
        )
    }

    @Test
    fun aliasThreadsAndOtherNumbersOfTheContactShareOneHistory() {
        val normalized = mapOf(
            "79620244454" to "+79620244454",
            "+79620244454" to "+79620244454",
            "+79990000000" to "+79990000000",
            "+78880000000" to "+78880000000",
        )

        assertEquals(
            listOf(31, 89, 101),
            ConversationThreadIdentityPolicy.relatedThreadIds(
                participantsByThreadId = linkedMapOf(
                    31 to listOf("79620244454"),
                    89 to listOf("+79620244454"),
                    101 to listOf("+79990000000"),
                    102 to listOf("+78880000000"),
                    103 to listOf("+79620244454", "+78880000000"),
                ),
                contactAddresses = listOf("+79620244454", "+79990000000"),
                normalize = { normalized.getValue(it) },
            ),
        )
    }
}
