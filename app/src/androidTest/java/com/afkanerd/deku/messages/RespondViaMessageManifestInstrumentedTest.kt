package com.afkanerd.deku.messages

import android.app.Service
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RespondViaMessageManifestInstrumentedTest {
    @Test
    fun respondViaMessageEntryPointIsARealProtectedService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(
                context.packageName,
                "com.afkanerd.deku.messages.RespondViaMessageService",
            ),
            0,
        )

        assertEquals(
            "android.permission.SEND_RESPOND_VIA_MESSAGE",
            serviceInfo.permission,
        )
        assertTrue(
            Service::class.java.isAssignableFrom(Class.forName(serviceInfo.name))
        )
    }
}
