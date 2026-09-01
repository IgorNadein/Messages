package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.theme.MessagesAppTheme
import org.junit.Rule
import org.junit.Test

class OneUiCompactBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longLocalizedTitleDoesNotPushTrailingActionOffScreen() {
        composeRule.setContent {
            MessagesAppTheme {
                OneUiCompactBar(
                    title = "Маршрутизированные сообщения",
                    showTitle = true,
                    navigation = { IconButton(onClick = {}) { Text("‹") } },
                    actions = {
                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("trailing-action"),
                        ) { Text("⋮") }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("trailing-action").assertIsDisplayed()
    }
}
