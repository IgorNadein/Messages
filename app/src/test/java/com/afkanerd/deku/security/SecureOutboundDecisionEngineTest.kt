package com.afkanerd.deku.security

import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsDecision
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureOutboundDecisionEngineTest {
    private val plaintext = "секрет 🔐"
    private val outbound = OutboundSms("+79990000000", plaintext)

    @Test
    fun everySecureSendPathReceivesOnlyCiphertext() = runTest {
        val paths = listOf("conversation", "notification", "share", "retry", "gateway")
        paths.forEach { _ ->
            val decision = SecureOutboundDecisionEngine.decide(
                SecureSessionStatus.SECURE_ESTABLISHED,
                outbound,
            ) { validCipherText() }

            assertTrue(decision is OutboundSmsDecision.Allow)
            val transport = (decision as OutboundSmsDecision.Allow).message.transportText
            assertFalse(transport.contains(plaintext))
            assertTrue(SecureMessageCodec.decodeTextOrNull(transport) != null)
        }
    }

    @Test
    fun secureSessionNeverFallsBackWhenEncryptionFails() = runTest {
        listOf<String?>(null, "", plaintext, "not-base64").forEach { result ->
            val decision = SecureOutboundDecisionEngine.decide(
                SecureSessionStatus.SECURE_ESTABLISHED,
                outbound,
            ) { result }
            assertTrue(decision is OutboundSmsDecision.Block)
        }
    }

    @Test
    fun pendingBrokenAndBinaryBypassAreBlocked() = runTest {
        assertTrue(decide(SecureSessionStatus.SECURE_PENDING) is OutboundSmsDecision.Block)
        assertTrue(decide(SecureSessionStatus.SECURE_BROKEN) is OutboundSmsDecision.Block)

        val binary = outbound.copy(transportData = byteArrayOf(0x44, 0x45))
        val decision = SecureOutboundDecisionEngine.decide(
            SecureSessionStatus.SECURE_ESTABLISHED,
            binary,
        ) { validCipherText() }
        assertTrue(decision is OutboundSmsDecision.Block)

        val mmsMarker = outbound.copy(transportData = byteArrayOf())
        val mmsDecision = SecureOutboundDecisionEngine.decide(
            SecureSessionStatus.SECURE_ESTABLISHED,
            mmsMarker,
        ) { validCipherText() }
        assertTrue(mmsDecision is OutboundSmsDecision.Block)
    }

    @Test
    fun plainSmsAndValidKeyExchangeRemainAvailable() = runTest {
        assertTrue(decide(SecureSessionStatus.PLAIN) is OutboundSmsDecision.Allow)

        val key = ByteArray(32)
        val exchange = outbound.copy(
            transportData = byteArrayOf(SecureMessageCodec.TYPE_REQUEST, 32) + key,
        )
        val decision = SecureOutboundDecisionEngine.decide(
            SecureSessionStatus.SECURE_PENDING,
            exchange,
            trustedControlMessage = true,
        ) { null }
        assertTrue(decision is OutboundSmsDecision.Allow)

        val untrusted = SecureOutboundDecisionEngine.decide(
            SecureSessionStatus.SECURE_PENDING,
            exchange,
            trustedControlMessage = false,
        ) { null }
        assertTrue(untrusted is OutboundSmsDecision.Block)
    }

    private suspend fun decide(status: SecureSessionStatus) =
        SecureOutboundDecisionEngine.decide(status, outbound) { validCipherText() }

    private fun validCipherText(): String {
        val header = ByteArray(40) { 0x41 }
        val cipher = ByteArray(48) { 0x7f }
        val packet = byteArrayOf(
            SecureMessageCodec.TYPE_MESSAGE,
            header.size.toByte(),
            cipher.size.toByte(),
        ) + header + cipher
        return Base64.getEncoder().encodeToString(packet)
    }
}
