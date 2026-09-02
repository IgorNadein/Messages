package com.afkanerd.deku.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncomingSecureRequestInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun incomingRequestRendersAcceptAndDispatchesNonRenewalAction() {
        var forceRenewal: Boolean? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val acceptText = context.getString(
            R.string.conversations_secure_conversation_request_agree
        )

        composeRule.setContent {
            MessagesAppTheme {
                SecuritySheet(
                    state = ConversationSecurityState.REQUEST_RECEIVED,
                    contactName = "+79990000000",
                    fingerprint = null,
                    secureSendingEnabled = true,
                    busy = false,
                    onAction = { forceRenewal = it },
                    onAcceptChangedIdentity = {},
                    onShowQr = {},
                    onScanQr = {},
                    onSecureSendingChange = {},
                )
            }
        }

        composeRule.onNodeWithText(acceptText).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("oneui-secure-sending-switch")
            .assertIsOff()
            .assertIsNotEnabled()
        assertEquals(false, forceRenewal)
    }
}
