package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.TestMessageService
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.RespondViaMessageRequest
import com.afkanerd.deku.messages.domain.SendResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RespondViaMessageHandlerRegressionTest {
    @Test
    fun systemReplyUsesConversationSelectedSimAndHighLevelSendBoundary() = runTest {
        val service = FakeRespondService(SendResult.Sent(77))
        val result = RespondViaMessageHandler(service).send(request())

        assertTrue(result is SendResult.Sent)
        assertEquals(1, service.calls)
        assertEquals(42, service.lastThreadId)
        assertEquals(22L, service.lastSubscriptionId)
        assertEquals("Перезвоню позже", service.lastText)
    }

    @Test
    fun secureBlockIsReturnedAfterOneAttemptWithoutPlaintextFallback() = runTest {
        val service = FakeRespondService(
            SendResult.BlockedBySecurity("secure session unavailable")
        )
        val result = RespondViaMessageHandler(service).send(request())

        assertTrue(result is SendResult.BlockedBySecurity)
        assertEquals(1, service.calls)
    }

    private fun request() = RespondViaMessageRequest(
        address = "+79990000000",
        text = "Перезвоню позже",
    )

    private class FakeRespondService(
        private val result: SendResult,
    ) : TestMessageService() {
        var calls = 0
        var lastThreadId: Int? = null
        var lastSubscriptionId: Long? = null
        var lastText: String? = null

        override suspend fun conversationHeader(
            address: String,
            threadId: Int?,
        ) = ConversationHeader(
            threadId = 42,
            address = address,
            displayName = address,
            avatarUri = null,
            subscriptionId = 22,
            subscriptions = emptyList(),
            securityState = ConversationSecurityState.SECURE_VERIFIED,
        )

        override suspend fun sendText(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            calls++
            lastThreadId = threadId
            lastSubscriptionId = subscriptionId
            lastText = text
            return result
        }
    }
}
