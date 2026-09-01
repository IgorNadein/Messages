package com.afkanerd.deku.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoicePlaybackUiTest {
    @Test
    fun playbackProgressIsBoundedAndDurationReadable() {
        assertEquals(0f, voicePlaybackProgress(10, 0))
        assertEquals(0.5f, voicePlaybackProgress(500, 1_000))
        assertEquals(1f, voicePlaybackProgress(2_000, 1_000))
        assertEquals("1:05", formatVoicePlaybackDuration(65_000))
    }
}
