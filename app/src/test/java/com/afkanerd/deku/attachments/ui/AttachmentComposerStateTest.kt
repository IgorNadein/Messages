package com.afkanerd.deku.attachments.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentComposerStateTest {
    @Test
    fun pickerIsReplacedByConfirmationAfterAttachmentIsPrepared() {
        assertTrue(shouldShowAttachmentPicker(hasPendingAttachment = false))
        assertFalse(shouldShowAttachmentPicker(hasPendingAttachment = true))
    }
}
