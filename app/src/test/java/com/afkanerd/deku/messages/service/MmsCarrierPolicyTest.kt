package com.afkanerd.deku.messages.service

import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.MmsCarrierPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsCarrierPolicyTest {
    @Test
    fun carrierBudgetReservesEnvelopeAndTextBytes() {
        val budget = MmsCarrierPolicy.attachmentBudget(
            maxMessageBytes = 300 * 1024,
            bodyBytes = 2 * 1024,
        )

        assertEquals((300 * 1024 * 0.9f).toInt() - 7 * 1024, budget)
        assertTrue(budget < 300 * 1024)
    }

    @Test
    fun oversizedTextProducesNoAttachmentBudget() {
        assertTrue(MmsCarrierPolicy.attachmentBudget(10 * 1024, 20 * 1024) < 0)
    }
}
