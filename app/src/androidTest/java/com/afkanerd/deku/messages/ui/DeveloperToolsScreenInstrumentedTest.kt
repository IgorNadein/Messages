package com.afkanerd.deku.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.messages.domain.NativeMessageImportSummary
import com.afkanerd.deku.messages.presentation.DeveloperToolsViewModel
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeveloperToolsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nativeToolsRemainVisibleAndClearRequiresConfirmation() {
        val service = FakeDeveloperToolsService()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val importTitle = context.getString(R.string.oneui_developer_import_native)
        val clearTitle = context.getString(R.string.oneui_developer_clear_native)
        val confirmationTitle = context.getString(
            R.string.oneui_developer_clear_native_confirmation_title
        )
        val viewModel = DeveloperToolsViewModel(service)

        composeRule.setContent {
            MessagesAppTheme {
                DeveloperToolsScreen(
                    viewModel = viewModel,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(importTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(clearTitle).performScrollTo().performClick()
        composeRule.onNodeWithText(confirmationTitle).assertIsDisplayed()
        assertEquals(0, service.clearNativeCalls)
    }

    private class FakeDeveloperToolsService : DeveloperToolsService {
        var clearNativeCalls = 0

        override suspend fun triggerSampleMmsNotification() = true
        override suspend fun clearLocalMessageHistory() = true
        override suspend fun exportNativeMessageDatabase(destinationUri: String) = true
        override suspend fun importNativeMessageDatabase(sourceUri: String) =
            NativeMessageImportSummary(0, 0, 0)
        override suspend fun clearNativeMessageDatabase(): Boolean {
            clearNativeCalls++
            return true
        }
    }
}
