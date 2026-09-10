package com.afkanerd.deku.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutScreenRegressionTest {
    @Test
    fun sourceCardOpensThisProjectsRepository() {
        assertEquals(
            "https://github.com/IgorNadein/Messages",
            OPEN_SOURCE_PROJECT_URL,
        )
        assertTrue(OPEN_SOURCE_PROJECT_URL.startsWith("https://"))
    }
}
