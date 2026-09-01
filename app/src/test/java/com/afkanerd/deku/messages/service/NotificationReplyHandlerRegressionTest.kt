package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.TestMessageService
import com.afkanerd.deku.messages.domain.NotificationReplyRequest
import com.afkanerd.deku.messages.domain.SendResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReplyHandlerRegressionTest {
    @Test
    fun notificationReplyUsesExactIncomingSimAndHighLevelServiceBoundary() = runTest {
        val service = FakeReplyService(SendResult.Sent(99))
        val result = NotificationReplyHandler(service).send(request(subscriptionId = 22))

        assertTrue(result is SendResult.Sent)
        assertEquals(1, service.calls)
        assertEquals(22L, service.lastRequest?.subscriptionId)
        assertEquals("Ответ из уведомления", service.lastRequest?.text)
    }

    @Test
    fun securityBlockIsReturnedWithoutPlaintextFallbackOrSecondAttempt() = runTest {
        val service = FakeReplyService(
            SendResult.BlockedBySecurity("Secure session is corrupted")
        )
        val result = NotificationReplyHandler(service).send(request())

        assertTrue(result is SendResult.BlockedBySecurity)
        assertEquals(1, service.calls)
    }

    @Test
    fun handlerHasNoCachedSecurityModeAcrossProcessStyleRecreation() = runTest {
        val service = FakeReplyService(SendResult.Sent(100))

        NotificationReplyHandler(service).send(request(text = "first"))
        NotificationReplyHandler(service).send(request(text = "after recreation"))

        assertEquals(2, service.calls)
        assertEquals("after recreation", service.lastRequest?.text)
    }

    private fun request(
        subscriptionId: Long = 11,
        text: String = "Ответ из уведомления",
    ) = NotificationReplyRequest(
        address = "+79990000000",
        threadId = 42,
        subscriptionId = subscriptionId,
        text = text,
    )

    private class FakeReplyService(
        private val result: SendResult,
    ) : TestMessageService() {
        var calls = 0
        var lastRequest: NotificationReplyRequest? = null

        override suspend fun sendNotificationReply(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            calls++
            lastRequest = NotificationReplyRequest(address, threadId, subscriptionId, text)
            return result
        }
    }
}
