package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignedIdentityInstrumentedTest {
    @Test
    fun persistentIdentitySignsAndVerifiesKeyExchangeOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val address = "+79990001122"
        val sessionKey = SecurityCurve25519().generateKey()
        val encoded = SignedKeyExchangeCodec.encode(
            context,
            EncryptionController.MessageRequestType.TYPE_REQUEST,
            sessionKey,
        )

        val decoded = SignedKeyExchangeCodec.decode(context, address, encoded)
        assertArrayEquals(sessionKey, decoded.sessionPublicKey)
        assertEquals(
            IdentityVerificationStatus.UNVERIFIED,
            IdentityKeyManager.getContactIdentity(context, address).status,
        )

        val tampered = encoded.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        assertFalse(runCatching {
            SignedKeyExchangeCodec.decode(context, address, tampered)
        }.isSuccess)

        val unsignedLegacy = byteArrayOf(
            EncryptionController.MessageRequestType.TYPE_REQUEST.code,
            32,
        ) + sessionKey
        assertFalse(runCatching {
            SignedKeyExchangeCodec.decode(context, address, unsignedLegacy)
        }.isSuccess)
    }
}
