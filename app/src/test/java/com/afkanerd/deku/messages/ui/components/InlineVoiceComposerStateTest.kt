package com.afkanerd.deku.messages.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineVoiceComposerStateTest {
    @Test
    fun `holding records and releasing pauses without leaving composer`() {
        val recording = inlineVoiceTransition(
            InlineVoicePhase.IDLE,
            InlineVoiceEvent.PRESS,
        )
        val paused = inlineVoiceTransition(recording, InlineVoiceEvent.RELEASE)
        val resumed = inlineVoiceTransition(paused, InlineVoiceEvent.PRESS)

        assertEquals(InlineVoicePhase.RECORDING, recording)
        assertEquals(InlineVoicePhase.PAUSED, paused)
        assertEquals(InlineVoicePhase.RECORDING, resumed)
    }

    @Test
    fun `finish and discard return to ordinary message field`() {
        assertEquals(
            InlineVoicePhase.IDLE,
            inlineVoiceTransition(InlineVoicePhase.PAUSED, InlineVoiceEvent.FINISH),
        )
        assertEquals(
            InlineVoicePhase.IDLE,
            inlineVoiceTransition(InlineVoicePhase.RECORDING, InlineVoiceEvent.DISCARD),
        )
    }
}
