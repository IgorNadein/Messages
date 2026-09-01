package com.afkanerd.deku.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveMessagesLayoutTest {
    @Test
    fun twoPaneStartsAtExpandedWindowWidth() {
        assertFalse(useTwoPaneMessagesLayout(839))
        assertTrue(useTwoPaneMessagesLayout(840))
        assertTrue(useTwoPaneMessagesLayout(1200))
    }
}
