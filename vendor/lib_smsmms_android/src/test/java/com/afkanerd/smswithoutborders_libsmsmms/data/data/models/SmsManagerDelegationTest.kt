package com.afkanerd.smswithoutborders_libsmsmms.data.data.models

import android.content.Context
import android.os.Bundle
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SmsManagerDelegationTest {
    @Test
    fun transportBoundaryPreservesEverySendArgumentAndCallback() {
        val sender = RecordingSmsSender()
        val manager = SmsManager(sender)
        val context = RuntimeEnvironment.getApplication()
        val binary = byteArrayOf(0x01, 0x7f, 0x00, 0x55)
        val bundle = Bundle().apply { putString("correlation", "rmq-42") }
        var callbackCalled = false

        manager.sendSms(
            context = context,
            text = "Unicode reply 🔒",
            address = "+15550001234",
            subscriptionId = 7L,
            threadId = 91,
            data = binary,
            bundle = bundle,
        ) { conversation ->
            assertNull(conversation)
            callbackCalled = true
        }

        val call = requireNotNull(sender.call)
        assertSame(context, call.context)
        assertEquals("Unicode reply 🔒", call.text)
        assertEquals("+15550001234", call.address)
        assertEquals(7L, call.subscriptionId)
        assertEquals(91, call.threadId)
        assertArrayEquals(binary, call.data)
        assertSame(bundle, call.bundle)
        assertTrue(callbackCalled)
    }

    private class RecordingSmsSender : SmsSender {
        var call: Call? = null

        override fun sendSms(
            context: Context,
            text: String,
            address: String,
            subscriptionId: Long,
            threadId: Int,
            data: ByteArray?,
            bundle: Bundle,
            callback: (Conversations?) -> Unit,
        ) {
            call = Call(context, text, address, subscriptionId, threadId, data, bundle)
            callback(null)
        }
    }

    private data class Call(
        val context: Context,
        val text: String,
        val address: String,
        val subscriptionId: Long,
        val threadId: Int,
        val data: ByteArray?,
        val bundle: Bundle,
    )
}
