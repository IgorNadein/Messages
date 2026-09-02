package com.afkanerd.smswithoutborders_libsmsmms.transport

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SmsPartStatusTrackerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("data_sms_part_status_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun successfulMultipartMessageFinalizesOnlyAfterEveryPart() {
        assertFalse(finalize(messageId = 41, partIndex = 0, successful = true))
        assertTrue(finalize(messageId = 41, partIndex = 1, successful = true))
        assertFalse(finalize(messageId = 41, partIndex = 1, successful = true))
    }

    @Test
    fun oneFailedPartFinalizesTheWholeMessageAsFailure() {
        assertTrue(finalize(messageId = 42, partIndex = 0, successful = false))
        assertFalse(finalize(messageId = 42, partIndex = 1, successful = true))
    }

    private fun finalize(messageId: Long, partIndex: Int, successful: Boolean): Boolean =
        DataSmsPartStatusTracker.shouldFinalize(
            context = context,
            messageId = messageId,
            stage = "sent",
            partIndex = partIndex,
            partCount = 2,
            successful = successful,
        )
}
