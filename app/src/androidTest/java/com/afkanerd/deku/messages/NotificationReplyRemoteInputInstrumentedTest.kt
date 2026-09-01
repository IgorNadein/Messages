package com.afkanerd.deku.messages

import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsMmsActionsImpl
import com.afkanerd.smswithoutborders_libsmsmms.receivers.extractNotificationReplyCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReplyRemoteInputInstrumentedTest {
    @Test
    fun androidRemoteInputExtractsUnicodeReplyAndIncomingSubscription() {
        val intent = replyIntent(subscriptionId = 22, text = "Ответ 🔐")

        val command = requireNotNull(
            extractNotificationReplyCommand(intent, defaultSubscriptionId = 11)
        )

        assertEquals("+79990000000", command.address)
        assertEquals(42, command.threadId)
        assertEquals(22L, command.subscriptionId)
        assertEquals("Ответ 🔐", command.text)
    }

    @Test
    fun missingIncomingSubscriptionUsesValidDefaultWithoutSendingAnything() {
        val intent = replyIntent(subscriptionId = null, text = "fallback")

        val command = requireNotNull(
            extractNotificationReplyCommand(intent, defaultSubscriptionId = 11)
        )

        assertEquals(11L, command.subscriptionId)
    }

    @Test
    fun emptyRemoteInputIsRejectedBeforeBroadcastOrTransport() {
        assertNull(
            extractNotificationReplyCommand(
                replyIntent(subscriptionId = 22, text = ""),
                defaultSubscriptionId = 11,
            )
        )
    }

    private fun replyIntent(subscriptionId: Long?, text: String): Intent {
        val intent = Intent(SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_INTENT_ACTION).apply {
            putExtra("address", "+79990000000")
            putExtra("thread_id", 42)
            subscriptionId?.let { putExtra("sub_id", it) }
        }
        val input = RemoteInput.Builder(SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_KEY).build()
        val results = Bundle().apply {
            putCharSequence(SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_KEY, text)
        }
        RemoteInput.addResultsToIntent(arrayOf(input), intent, results)
        return intent
    }
}
