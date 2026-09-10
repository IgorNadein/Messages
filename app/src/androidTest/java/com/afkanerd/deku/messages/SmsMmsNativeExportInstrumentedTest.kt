package com.afkanerd.deku.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.deku.DefaultSMS.extensions.context.exportRawWithColumnGuesses
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsMmsNativeExportInstrumentedTest {

    @Test
    fun exportAcceptsTheDeviceTelephonyProviderMmsColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val export = context.exportRawWithColumnGuesses()

        assertTrue(export.contains("\"mms\""))
        assertTrue(export.contains("\"mms_parts\""))
        assertTrue(export.contains("\"mms_addr\""))
        assertTrue(export.contains("\"sms\""))
    }
}
