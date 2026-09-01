package com.afkanerd.deku.messages.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxScrollBehaviorTest {
    @Test
    fun chromeHidesOnlyForStrongForwardFling() {
        assertFalse(shouldHideInboxChromeForFling(velocityYPx = -600f, density = 2f))
        assertFalse(shouldHideInboxChromeForFling(velocityYPx = -2_400f, density = 2f))
        assertFalse(shouldHideInboxChromeForFling(velocityYPx = 4_000f, density = 2f))
        assertTrue(shouldHideInboxChromeForFling(velocityYPx = -2_401f, density = 2f))
        assertTrue(shouldHideInboxChromeForFling(velocityYPx = -6_000f, density = 2f))
    }
}
