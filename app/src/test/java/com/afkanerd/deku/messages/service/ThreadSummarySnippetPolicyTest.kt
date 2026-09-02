package com.afkanerd.deku.messages.service

import com.afkanerd.deku.security.SecureMessageCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadSummarySnippetPolicyTest {
    @Test
    fun encryptedDataMessageKeepsItsAlreadyDecryptedPreview() {
        val messageEnvelope = byteArrayOf(SecureMessageCodec.TYPE_MESSAGE, 40, 48) +
            ByteArray(40) { 1 } + ByteArray(48) { 2 }

        assertEquals("Привет", display("Привет", messageEnvelope, "ciphertext"))
    }

    @Test
    fun keyExchangeRemainsAControlPreview() {
        val request = byteArrayOf(SecureMessageCodec.TYPE_REQUEST, 32) + ByteArray(32) { 7 }

        assertEquals("security update", display("binary", request, null))
    }

    @Test
    fun failedSecureMessageNeverLeaksTransportText() {
        assertEquals("decrypt failed", display("failure", null, "ciphertext"))
    }

    private fun display(snippet: String, data: ByteArray?, transport: String?) =
        ThreadSummarySnippetPolicy.display(
            snippet = snippet,
            smsData = data,
            secureTransportText = transport,
            decryptionFailureText = "failure",
            recoveryText = "recovery",
            securityUpdateText = "security update",
            decryptionFailedText = "decrypt failed",
        )
}
