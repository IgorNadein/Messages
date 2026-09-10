package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.telephony.SmsManager
import android.app.Activity
import android.app.PendingIntent
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsDeliveryReportOutcome
import com.afkanerd.smswithoutborders_libsmsmms.receivers.classifyDeliveryReportStatus
import com.afkanerd.smswithoutborders_libsmsmms.receivers.isSuccessfulSmsCallback
import com.afkanerd.smswithoutborders_libsmsmms.receivers.resolveDeliveryCallbackOutcome
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SmsTransportInvariantTest {
    @Test
    fun acceptsBothFrameworkSuccessCodesButRejectsRealSmsErrors() {
        assertEquals(true, isSuccessfulSmsCallback(Activity.RESULT_OK))
        assertEquals(true, isSuccessfulSmsCallback(SmsManager.RESULT_ERROR_NONE))
        assertEquals(false, isSuccessfulSmsCallback(SmsManager.RESULT_ERROR_GENERIC_FAILURE))
        assertEquals(false, isSuccessfulSmsCallback(SmsManager.RESULT_ERROR_RADIO_OFF))
        assertEquals(false, isSuccessfulSmsCallback(SmsManager.RESULT_ERROR_NO_SERVICE))
    }

    @Test
    fun deliveryReportStatusRangesAreNotConfusedWithSuccess() {
        assertEquals(SmsDeliveryReportOutcome.UNKNOWN, classifyDeliveryReportStatus(null))
        assertEquals(SmsDeliveryReportOutcome.DELIVERED, classifyDeliveryReportStatus(0))
        assertEquals(SmsDeliveryReportOutcome.UNKNOWN, classifyDeliveryReportStatus(1))
        assertEquals(SmsDeliveryReportOutcome.UNKNOWN, classifyDeliveryReportStatus(31))
        assertEquals(SmsDeliveryReportOutcome.PENDING, classifyDeliveryReportStatus(32))
        assertEquals(SmsDeliveryReportOutcome.PENDING, classifyDeliveryReportStatus(63))
        assertEquals(SmsDeliveryReportOutcome.FAILED, classifyDeliveryReportStatus(64))
        assertEquals(SmsDeliveryReportOutcome.FAILED, classifyDeliveryReportStatus(127))
        assertEquals(SmsDeliveryReportOutcome.UNKNOWN, classifyDeliveryReportStatus(128))
    }

    @Test
    fun callbackWithoutStatusPduNeverClaimsDelivery() {
        assertNull(
            resolveDeliveryCallbackOutcome(
                SmsDeliveryReportOutcome.UNKNOWN,
                SmsManager.RESULT_ERROR_NONE,
            )
        )
        assertNull(
            resolveDeliveryCallbackOutcome(
                SmsDeliveryReportOutcome.UNKNOWN,
                Activity.RESULT_OK,
            )
        )
        assertEquals(
            false,
            resolveDeliveryCallbackOutcome(
                SmsDeliveryReportOutcome.UNKNOWN,
                SmsManager.RESULT_ERROR_NO_SERVICE,
            )
        )
    }

    @Test
    fun smsCallbacksAllowTelephonyToAttachDeliveryReport() {
        val modernFlags = smsStatusPendingIntentFlags(31)
        assertEquals(PendingIntent.FLAG_MUTABLE, modernFlags and PendingIntent.FLAG_MUTABLE)
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT,
            modernFlags and PendingIntent.FLAG_UPDATE_CURRENT,
        )
        assertEquals(0, smsStatusPendingIntentFlags(30) and PendingIntent.FLAG_MUTABLE)
    }

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

    @Test
    fun dataSmsUsesBinaryPortAndNeverFallsBackToVisibleCiphertext() {
        val visibleCiphertext = "this must not become a text SMS"
        val frame = ByteArray(120) { it.toByte() }
        val smsManager = SmsManager.getDefault()
        val shadow = shadowOf(smsManager)
        val previousTextSend = shadow.lastSentTextMessageParams

        dispatchSmsToAndroid(
            smsManager,
            "+79990000000",
            visibleCiphertext,
            frame,
            null,
            null,
        )

        assertArrayEquals(frame, shadow.lastSentDataMessageParams.data)
        assertEquals(8200.toShort(), shadow.lastSentDataMessageParams.destinationPort)
        assertSame(previousTextSend, shadow.lastSentTextMessageParams)
    }
}
