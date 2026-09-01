package com.afkanerd.deku.attachments.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRecordingUiTest {
    @Test
    fun durationIsStableAcrossMinuteBoundary() {
        assertEquals("0:00", formatVoiceDuration(0))
        assertEquals("0:59", formatVoiceDuration(59_999))
        assertEquals("1:05", formatVoiceDuration(65_000))
    }
}
