package com.afkanerd.deku.messages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.deku.messages.presentation.SettingsViewModel
import com.afkanerd.deku.messages.ui.SettingsScreen
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsTransportInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataSmsCannotBeEnabledWithoutCompatibilityWarningAndConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = TransportSettingsService()
        val viewModel = SettingsViewModel(service)
        composeRule.setContent {
            MessagesAppTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onDeveloperOptions = {},
                    onRemoteListeners = {},
                    onGatewayClients = {},
                    onRoutingHistory = {},
                    onAbout = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.oneui_secure_message_transport))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_transport_data_sms))
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.oneui_data_sms_warning_title))
            .assertIsDisplayed()
        assertEquals(SecureMessageTransport.STANDARD_SMS, service.value.secureMessageTransport)

        composeRule.onNodeWithText(context.getString(R.string.oneui_enable_data_sms))
            .performClick()
        assertEquals(SecureMessageTransport.DATA_SMS, service.value.secureMessageTransport)
    }
}

private class TransportSettingsService : AppSettingsService {
    var value = AppSettingsSnapshot(
        languageTag = "en",
        languageName = "English",
        languages = listOf(LanguageOption("en", "English")),
        themeMode = ThemeMode.SYSTEM,
        storeInSystemDatabase = false,
        deleteFromSystemDatabase = false,
        deliveryReports = false,
        swipeActions = false,
        keepArchived = false,
        contextReplies = false,
        use24HourTime = false,
    )

    override fun snapshot() = value

    override fun setTheme(mode: ThemeMode) = value.copy(themeMode = mode).also { value = it }

    override fun setLanguage(tag: String) = value.copy(languageTag = tag).also { value = it }

    override fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot = value

    override fun setSecureMessageTransport(
        transport: SecureMessageTransport,
    ) = value.copy(secureMessageTransport = transport).also { value = it }
}
