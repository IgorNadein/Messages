package com.afkanerd.deku.messages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.MediaTransport
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.deku.messages.domain.SimTransportSettings
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

        composeRule.onNodeWithTag("settings-section-transport").performClick()
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

    @Test
    fun mediaDataSmsWarningUsesGenericCancelLabel() {
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

        composeRule.onNodeWithTag("settings-section-transport").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_media_transport))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_media_transport_data_sms))
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.oneui_media_data_sms_warning_title))
            .assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithText(context.getString(R.string.oneui_cancel))
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText(context.getString(R.string.attachment_cancel))
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun compactTitleAppearsOnlyAfterSettingsContentScrolls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = SettingsViewModel(TransportSettingsService())
        composeRule.setContent {
            MessagesAppTheme {
                SettingsScreen(viewModel, {}, {}, {}, {}, {}, {})
            }
        }

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings-compact-title")
                .fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("settings-list").performScrollToIndex(5)
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("settings-compact-title")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun mediaTransportCanBeChangedForOnlyOneSim() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = TransportSettingsService(
            listOf(
                SimTransportSettings(
                    2,
                    "Lizerk",
                    0,
                    SecureMessageTransport.STANDARD_SMS,
                    MediaTransport.DATA_SMS,
                ),
                SimTransportSettings(
                    12,
                    "beeline",
                    1,
                    SecureMessageTransport.STANDARD_SMS,
                    MediaTransport.MMS,
                ),
            )
        )
        val viewModel = SettingsViewModel(service)
        composeRule.setContent {
            MessagesAppTheme {
                SettingsScreen(viewModel, {}, {}, {}, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("settings-section-transport").performClick()
        composeRule.onNodeWithTag("settings-sim-selector-12").performClick()
        composeRule.onNodeWithTag("settings-media-transport-12")
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_media_transport_data_sms))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_enable_data_sms))
            .performClick()

        assertEquals(MediaTransport.DATA_SMS, service.value.simTransportSettings[0].mediaTransport)
        assertEquals(MediaTransport.DATA_SMS, service.value.simTransportSettings[1].mediaTransport)
        assertEquals("media:12:DATA_SMS", service.calls.single())
    }

    @Test
    fun detailedSettingsAreHiddenBehindSamsungStyleSections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = SettingsViewModel(TransportSettingsService())
        composeRule.setContent {
            MessagesAppTheme {
                SettingsScreen(viewModel, {}, {}, {}, {}, {}, {})
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.oneui_secure_message_transport))
            .assertDoesNotExist()
        composeRule.onNodeWithTag("settings-section-transport").performClick()
        composeRule.onNodeWithText(context.getString(R.string.oneui_secure_message_transport))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.oneui_media_transport))
            .assertIsDisplayed()
    }

    @Test
    fun existingSettingsRemainReachableThroughNestedSections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = SettingsViewModel(TransportSettingsService())
        val backDescription = context.getString(R.string.oneui_back)
        composeRule.setContent {
            MessagesAppTheme {
                SettingsScreen(viewModel, {}, {}, {}, {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("settings-section-appearance").performClick()
        composeRule.onNodeWithText(context.getString(R.string.theme)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.language)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(backDescription).performClick()

        composeRule.onNodeWithTag("settings-section-storage").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.save_messages_to_system_s_database)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.keep_messages_archived))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(backDescription).performClick()

        composeRule.onNodeWithTag("settings-section-behavior").performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.get_sms_delivery_reports))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.enable_notification_context_replies)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(backDescription).performClick()

        composeRule.onNodeWithTag("settings-section-advanced").performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.remote_listeners)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.gateway_clients)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_SMS_routing_title))
            .performScrollTo()
            .assertIsDisplayed()
    }
}

private class TransportSettingsService(
    simTransportSettings: List<SimTransportSettings> = emptyList(),
) : AppSettingsService {
    val calls = mutableListOf<String>()
    var value = AppSettingsSnapshot(
        languageTag = "en",
        languageName = "English",
        languages = listOf(LanguageOption("en", "English")),
        themeMode = ThemeMode.SYSTEM,
        storeInSystemDatabase = false,
        deleteFromSystemDatabase = false,
        deliveryReports = false,
        keepArchived = false,
        contextReplies = false,
        use24HourTime = false,
        simTransportSettings = simTransportSettings,
    )

    override fun snapshot() = value

    override fun setTheme(mode: ThemeMode) = value.copy(themeMode = mode).also { value = it }

    override fun setLanguage(tag: String) = value.copy(languageTag = tag).also { value = it }

    override fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot = value

    override fun setSecureMessageTransport(
        transport: SecureMessageTransport,
    ) = value.copy(secureMessageTransport = transport).also { value = it }

    override fun setSecureMessageTransport(
        subscriptionId: Long,
        transport: SecureMessageTransport,
    ): AppSettingsSnapshot {
        calls += "secure:$subscriptionId:$transport"
        value = value.copy(
            simTransportSettings = value.simTransportSettings.map {
                if(it.subscriptionId == subscriptionId) {
                    it.copy(secureMessageTransport = transport)
                } else it
            }
        )
        return value
    }

    override fun setMediaTransport(
        subscriptionId: Long,
        transport: MediaTransport,
    ): AppSettingsSnapshot {
        calls += "media:$subscriptionId:$transport"
        value = value.copy(
            simTransportSettings = value.simTransportSettings.map {
                if(it.subscriptionId == subscriptionId) it.copy(mediaTransport = transport)
                else it
            }
        )
        return value
    }
}
