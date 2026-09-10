package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.app.Application
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.security.SECURE_TRANSPORT_TEXT_EXTRA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationBroadcastPolicyTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun ordinaryOutgoingStatusNeverRequestsANotification() {
        application.sendNotificationBroadcast(
            conversation = Conversations(id = 41),
            type = NotificationTxType.TEXT,
            self = true,
            secureTransportText = "transport",
        )

        val intent = shadowOf(application).broadcastIntents.last()
        assertTrue(intent.getBooleanExtra("self", false))
        assertFalse(intent.getBooleanExtra(SHOW_NOTIFICATION_EXTRA, true))
        assertEquals("transport", intent.getStringExtra(SECURE_TRANSPORT_TEXT_EXTRA))
    }

    @Test
    fun incomingMessageRequestsANotificationByDefault() {
        application.sendNotificationBroadcast(
            conversation = Conversations(id = 42),
            type = NotificationTxType.TEXT,
        )

        val intent = shadowOf(application).broadcastIntents.last()
        assertFalse(intent.getBooleanExtra("self", true))
        assertTrue(intent.getBooleanExtra(SHOW_NOTIFICATION_EXTRA, false))
    }

    @Test
    fun notificationReplyMayExplicitlyRefreshTheExistingNotification() {
        application.sendNotificationBroadcast(
            conversation = Conversations(id = 43),
            type = NotificationTxType.TEXT,
            self = true,
            showNotification = true,
        )

        val intent = shadowOf(application).broadcastIntents.last()
        assertTrue(intent.getBooleanExtra("self", false))
        assertTrue(intent.getBooleanExtra(SHOW_NOTIFICATION_EXTRA, false))
    }
}
