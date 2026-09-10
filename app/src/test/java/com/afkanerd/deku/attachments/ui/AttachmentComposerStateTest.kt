package com.afkanerd.deku.attachments.ui

import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.messages.domain.MediaTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentComposerStateTest {
    @Test
    fun pickerIsReplacedByConfirmationAfterAttachmentIsPrepared() {
        assertTrue(shouldShowAttachmentPicker(hasPendingAttachment = false))
        assertFalse(shouldShowAttachmentPicker(hasPendingAttachment = true))
    }

    @Test
    fun `mms confirmation uses mms units and mms size limit`() {
        val policy = attachmentConfirmationPolicy(
            mediaTransport = MediaTransport.MMS,
            encodedBytes = 2_360,
            recipientCount = 1,
            secureTransfer = true,
        )

        assertEquals(1, policy.estimatedUnits)
        assertEquals(TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong(), policy.maxBytes)
        assertFalse(policy.showSmsCostWarning)
        assertFalse(policy.unsupportedGroup)
    }

    @Test
    fun `data sms confirmation retains conservative limit and cost warning`() {
        val policy = attachmentConfirmationPolicy(
            mediaTransport = MediaTransport.DATA_SMS,
            encodedBytes = TransferLimits.CONFIRM_TRANSFER_BYTES.toLong(),
            recipientCount = 1,
            secureTransfer = false,
        )

        assertEquals(TransferLimits.MAX_TRANSFER_BYTES.toLong(), policy.maxBytes)
        assertTrue(policy.estimatedUnits!! > 1)
        assertTrue(policy.showSmsCostWarning)
    }

    @Test
    fun `packet and cloud group media are blocked before queueing`() {
        listOf(MediaTransport.DATA_SMS, MediaTransport.CLOUD_STORAGE).forEach { transport ->
            val policy = attachmentConfirmationPolicy(
                mediaTransport = transport,
                encodedBytes = 1024,
                recipientCount = 2,
                secureTransfer = false,
            )
            assertTrue(policy.unsupportedGroup)
            if(transport == MediaTransport.CLOUD_STORAGE) assertNull(policy.estimatedUnits)
        }
    }

    @Test
    fun `voice preview progress is bounded and handles unknown duration`() {
        assertEquals(0f, voicePreviewProgress(250, 0), 0f)
        assertEquals(0f, voicePreviewProgress(-100, 1_000), 0f)
        assertEquals(0.5f, voicePreviewProgress(500, 1_000), 0f)
        assertEquals(1f, voicePreviewProgress(2_000, 1_000), 0f)
    }
}
