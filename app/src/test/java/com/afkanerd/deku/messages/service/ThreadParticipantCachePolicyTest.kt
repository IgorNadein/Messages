package com.afkanerd.deku.messages.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadParticipantCachePolicyTest {
    @Test
    fun cachedThreadMembersPreventRepeatedProviderQueries() {
        val cached = listOf("+15550000001", "+15550000002")

        assertFalse(ThreadParticipantCachePolicy.shouldQueryProvider(cached))
        assertEquals(
            cached,
            ThreadParticipantCachePolicy.resolve(
                cached = cached,
                provider = listOf("provider-must-not-replace-cache"),
                fallback = listOf("fallback"),
            ),
        )
    }

    @Test
    fun emptyCacheUsesProviderOnceBeforeFallback() {
        assertTrue(ThreadParticipantCachePolicy.shouldQueryProvider(emptyList()))
        assertEquals(
            listOf("+15550000003"),
            ThreadParticipantCachePolicy.resolve(
                cached = emptyList(),
                provider = listOf("+15550000003"),
                fallback = listOf("fallback"),
            ),
        )
    }
}
