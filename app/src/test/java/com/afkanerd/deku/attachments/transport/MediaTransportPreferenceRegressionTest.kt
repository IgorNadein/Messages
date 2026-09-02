package com.afkanerd.deku.attachments.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.afkanerd.deku.messages.domain.MediaTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaTransportPreferenceRegressionTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `mms is the safe default`() {
        assertEquals(MediaTransport.MMS, MediaTransportPreference.selected(context))
    }

    @Test
    fun `selected outgoing transport is persisted`() {
        MediaTransportPreference.setSelected(context, MediaTransport.DATA_SMS)

        assertEquals(MediaTransport.DATA_SMS, MediaTransportPreference.selected(context))
    }

    @Test
    fun `unknown persisted value falls back to mms`() {
        preferences().edit().putString("selected", "REMOVED_TRANSPORT").commit()

        assertEquals(MediaTransport.MMS, MediaTransportPreference.selected(context))
    }

    private fun preferences() = context.getSharedPreferences("media_transport", Context.MODE_PRIVATE)
}
