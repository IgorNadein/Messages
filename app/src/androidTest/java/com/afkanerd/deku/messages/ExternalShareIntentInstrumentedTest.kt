package com.afkanerd.deku.messages

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.MainActivity
import org.junit.Rule
import org.junit.Test

class ExternalShareIntentInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sharedTextIntentOpensComposeRecipientPicker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.compose_new_message_title)

        composeRule.activity.startActivity(
            Intent(composeRule.activity, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "external share regression")
            }
        )

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
