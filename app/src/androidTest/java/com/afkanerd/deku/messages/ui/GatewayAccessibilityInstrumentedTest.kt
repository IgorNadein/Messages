package com.afkanerd.deku.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.paging.PagingData
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.GatewayProtocol
import com.afkanerd.deku.messages.domain.GatewaySummary
import com.afkanerd.deku.messages.domain.ImportProgress
import com.afkanerd.deku.messages.domain.ImportResult
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.SecureSessionActionResult
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.presentation.GatewayViewModel
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class GatewayAccessibilityInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smtpPasswordToggleAnnouncesItsCurrentAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = GatewayViewModel(FakeGatewayService()).also {
            it.create(GatewayProtocol.SMTP)
        }

        composeRule.setContent {
            MessagesAppTheme {
                GatewayScreen(viewModel = viewModel, onBack = {})
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.show_password))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.hide_password))
            .assertIsDisplayed()
    }

    private class FakeGatewayService : MessageService {
        override fun gatewayConfigurations(): Flow<List<GatewaySummary>> = flowOf(emptyList())
        override fun isDefaultSmsApp() = true
        override fun hasContactAccess() = true
        override fun isContactPromptCompleted() = true
        override fun completeContactPrompt() = Unit
        override fun conversationThreads(): Flow<PagingData<ConversationThread>> =
            flowOf(PagingData.empty())
        override fun timeline(threadId: Int): Flow<PagingData<TimelineItem>> =
            flowOf(PagingData.empty())
        override fun attachmentTransfers(address: String): Flow<List<AttachmentTransfer>> =
            flowOf(emptyList())
        override suspend fun performAttachmentAction(
            transferId: String,
            action: AttachmentAction,
        ) = Unit
        override suspend fun prepareAttachment(
            address: String,
            subscriptionId: Long,
            attachment: PreparedAttachment,
        ) = AttachmentPrepareResult.Queued
        override suspend fun importIfNeeded(
            onProgress: suspend (ImportProgress) -> Unit,
        ) = ImportResult.AlreadyReady
        override suspend fun conversationHeader(address: String, threadId: Int?): ConversationHeader =
            error("Not used")
        override suspend fun loadDraft(threadId: Int) = ""
        override suspend fun saveDraft(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ) = Unit
        override suspend fun selectSubscription(address: String, subscriptionId: Long) = Unit
        override suspend fun sendText(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ) = SendResult.Failed("Not used")
        override suspend fun requestOrRepairSecureSession(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            forceRenewal: Boolean,
        ) = SecureSessionActionResult.Failed("Not used")
        override suspend fun securityFingerprint(address: String): String? = null
        override suspend fun acceptChangedIdentityAndRepair(
            address: String,
            threadId: Int,
            subscriptionId: Long,
        ) = SecureSessionActionResult.Failed("Not used")
    }
}
