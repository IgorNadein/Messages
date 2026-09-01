package com.afkanerd.deku.attachments.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AttachmentUiErrorPolicyTest {
    @Test
    fun `preparation failure never exposes raw exception details`() {
        val rawDiagnostic = "SMS send failed (0): /data/user/0/private.tmp"
        val visible = attachmentPreparationFailureMessage("Unable to prepare attachment")

        assertEquals("Unable to prepare attachment", visible)
        assertFalse(visible.contains(rawDiagnostic))
        assertFalse(visible.contains("/data/user"))
    }
}
