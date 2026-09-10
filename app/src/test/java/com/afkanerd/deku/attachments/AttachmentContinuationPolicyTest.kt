package com.afkanerd.deku.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentContinuationPolicyTest {
    @Test
    fun `manual data sms continuation permits exactly one transport attempt`() {
        assertEquals(
            7,
            manualContinuationRetryCount(
                transport = AttachmentWireTransport.DATA_SMS,
                maxSmsRetries = 8,
            ),
        )
    }

    @Test
    fun `non sms continuation starts a fresh transport retry budget`() {
        assertEquals(0, manualContinuationRetryCount(AttachmentWireTransport.MMS, 8))
        assertEquals(0, manualContinuationRetryCount(AttachmentWireTransport.CLOUD_STORAGE, 8))
    }

    @Test
    fun `receiver acknowledgement requests stop at configured limit`() {
        assertEquals(1, nextAttachmentAckRequestCount(currentCount = 0, maxRequests = 3))
        assertEquals(2, nextAttachmentAckRequestCount(currentCount = 1, maxRequests = 3))
        assertEquals(3, nextAttachmentAckRequestCount(currentCount = 2, maxRequests = 3))
        assertNull(nextAttachmentAckRequestCount(currentCount = 3, maxRequests = 3))
    }
}
