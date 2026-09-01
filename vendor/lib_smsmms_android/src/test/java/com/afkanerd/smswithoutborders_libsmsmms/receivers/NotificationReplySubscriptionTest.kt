package com.afkanerd.smswithoutborders_libsmsmms.receivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationReplySubscriptionTest {
    @Test
    fun replyUsesSimFromIncomingMessageOnDualSimDevice() {
        assertEquals(22L, resolveNotificationReplySubscription(22L, 11L))
    }

    @Test
    fun replyFallsBackToDefaultOnlyWhenNotificationHasNoValidSim() {
        assertEquals(11L, resolveNotificationReplySubscription(-1L, 11L))
        assertEquals(11L, resolveNotificationReplySubscription(null, 11L))
    }

    @Test
    fun askEveryTimeWithoutIncomingSimDoesNotCrash() {
        assertNull(resolveNotificationReplySubscription(null, null))
    }
}
