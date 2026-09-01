package com.afkanerd.deku.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutScreenRendersAppIconAndKeepsSourceLinkBehavior() {
        var openedUrl: String? = null
        composeRule.setContent {
            MessagesAppTheme {
                AboutOneUiScreen(
                    versionName = "9.9.9-regression",
                    onBack = {},
                    onOpenSource = { openedUrl = it },
                )
            }
        }

        composeRule.onNode(hasText("9.9.9-regression", substring = true))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("about-open-source").performScrollTo().performClick()
        assertEquals(OPEN_SOURCE_PROJECT_URL, openedUrl)
    }
}
