package com.afkanerd.deku.messages.presentation

import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.deku.messages.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelRegressionTest {
    @Test
    fun `secure transport defaults to compatible sms and can be explicitly changed`() {
        val service = FakeSettingsService()
        val viewModel = SettingsViewModel(service)

        assertEquals(SecureMessageTransport.STANDARD_SMS, viewModel.state.value.secureMessageTransport)

        viewModel.setSecureMessageTransport(SecureMessageTransport.DATA_SMS)

        assertEquals(SecureMessageTransport.DATA_SMS, viewModel.state.value.secureMessageTransport)
        assertEquals("transport:DATA_SMS", service.calls.single())
    }

    @Test
    fun `theme language and legacy boolean settings propagate through service boundary`() {
        val service = FakeSettingsService()
        val viewModel = SettingsViewModel(service)

        viewModel.setTheme(ThemeMode.DARK)
        viewModel.setLanguage("ru")
        viewModel.setBoolean(BooleanSetting.DELIVERY_REPORTS, true)

        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
        assertEquals("ru", viewModel.state.value.languageTag)
        assertTrue(viewModel.state.value.deliveryReports)
        assertEquals(
            listOf("theme:DARK", "language:ru", "DELIVERY_REPORTS:true"),
            service.calls,
        )
    }
}

private class FakeSettingsService : AppSettingsService {
    val calls = mutableListOf<String>()
    private var value = AppSettingsSnapshot(
        languageTag = "en",
        languageName = "English",
        languages = listOf(LanguageOption("en", "English"), LanguageOption("ru", "Русский")),
        themeMode = ThemeMode.SYSTEM,
        storeInSystemDatabase = false,
        deleteFromSystemDatabase = false,
        deliveryReports = false,
        swipeActions = false,
        keepArchived = false,
        contextReplies = false,
        use24HourTime = false,
    )

    override fun snapshot(): AppSettingsSnapshot = value

    override fun setTheme(mode: ThemeMode): AppSettingsSnapshot {
        calls += "theme:$mode"
        value = value.copy(themeMode = mode)
        return value
    }

    override fun setLanguage(tag: String): AppSettingsSnapshot {
        calls += "language:$tag"
        val language = value.languages.single { it.tag == tag }
        value = value.copy(languageTag = tag, languageName = language.displayName)
        return value
    }

    override fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot {
        calls += "$setting:$enabled"
        value = when(setting) {
            BooleanSetting.STORE_IN_SYSTEM_DATABASE -> value.copy(storeInSystemDatabase = enabled)
            BooleanSetting.DELETE_FROM_SYSTEM_DATABASE -> value.copy(deleteFromSystemDatabase = enabled)
            BooleanSetting.DELIVERY_REPORTS -> value.copy(deliveryReports = enabled)
            BooleanSetting.SWIPE_ACTIONS -> value.copy(swipeActions = enabled)
            BooleanSetting.KEEP_ARCHIVED -> value.copy(keepArchived = enabled)
            BooleanSetting.CONTEXT_REPLIES -> value.copy(contextReplies = enabled)
            BooleanSetting.USE_24_HOUR_TIME -> value.copy(use24HourTime = enabled)
        }
        return value
    }

    override fun setSecureMessageTransport(
        transport: SecureMessageTransport,
    ): AppSettingsSnapshot {
        calls += "transport:$transport"
        value = value.copy(secureMessageTransport = transport)
        return value
    }
}
