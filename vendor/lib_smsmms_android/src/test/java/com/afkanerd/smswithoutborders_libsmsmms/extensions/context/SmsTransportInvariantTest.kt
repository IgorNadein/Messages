package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.telephony.SmsManager
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SmsTransportInvariantTest {
    @Test
    fun groupMmsSettingsKeepAllRecipientsInOneTransport() {
        assertEquals(true, MmsParser.getSendMessageSettings(group = true).group)
        assertEquals(false, MmsParser.getSendMessageSettings(group = false).group)
    }

    @Test
    fun ordinarySmsPreservesExactUnicodePlaintext() {
        val plaintext = "Обычное SMS 👋"
        val smsManager = SmsManager.getDefault()

        dispatchSmsToAndroid(
            smsManager,
            "+79990000000",
            plaintext,
            null,
            null,
            null,
        )

        assertEquals(plaintext, shadowOf(smsManager).lastSentTextMessageParams.text)
    }

    @Test
    fun androidSmsManagerReceivesOnlyProvidedCiphertext() {
        val plaintext = "секретное сообщение"
        val ciphertext = "AAMoMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAw"
        val smsManager = SmsManager.getDefault()

        dispatchSmsToAndroid(
            smsManager,
            "+79990000000",
            ciphertext,
            null,
            null,
            null,
        )

        val shadow = shadowOf(smsManager)
        val sent = shadow.lastSentTextMessageParams
        assertEquals(ciphertext, sent.text)
        assertFalse(sent.text.contains(plaintext))
        assertNull(shadow.lastSentDataMessageParams)
    }

    @Test
    fun multipartTransportNeverSubstitutesDisplayPlaintext() {
        val plaintext = "секрет"
        val ciphertext = "Q".repeat(500)
        val smsManager = SmsManager.getDefault()

        dispatchSmsToAndroid(
            smsManager,
            "+79990000000",
            ciphertext,
            null,
            null,
            null,
        )

        val parts = shadowOf(smsManager).lastSentMultipartTextMessageParams.parts
        assertEquals(ciphertext, parts.joinToString(""))
        assertFalse(parts.any { it.contains(plaintext) })
    }
}
