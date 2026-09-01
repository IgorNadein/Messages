package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class IdentityCryptographyTest {
    @Test
    fun ed25519SignatureVerifiesAndTamperingFails() {
        val keyPair = Ed25519Sign.KeyPair.newKeyPair()
        val message = "signed session key".encodeToByteArray()
        val signature = Ed25519Sign(keyPair.privateKey).sign(message)

        assertTrue(IdentityKeyManager.verify(keyPair.publicKey, signature, message))
        assertFalse(
            IdentityKeyManager.verify(
                keyPair.publicKey,
                signature,
                "tampered session key".encodeToByteArray(),
            )
        )
        assertFalse(
            IdentityKeyManager.verify(
                keyPair.publicKey,
                signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
                message,
            )
        )
    }

    @Test
    fun identityQrPayloadRoundTripsAndRejectsMalformedValues() {
        val publicKey = ByteArray(32) { it.toByte() }
        val encoded = IdentityKeyManager.qrPayload(publicKey)

        assertArrayEquals(publicKey, IdentityKeyManager.parseQrPayload(encoded))
        assertNull(IdentityKeyManager.parseQrPayload("https://example.test/not-an-identity"))
        assertNull(IdentityKeyManager.parseQrPayload("deku-identity:v1:AA"))
    }

    @Test
    fun signedKeyExchangeVerifiesAndRejectsAnyTampering() {
        val identity = Ed25519Sign.KeyPair.newKeyPair()
        val sessionKey = ByteArray(32) { index -> (index * 3).toByte() }
        val type = EncryptionController.MessageRequestType.TYPE_REQUEST
        val signature = Ed25519Sign(identity.privateKey).sign(
            SignedKeyExchangeCodec.signatureInput(type.code, sessionKey, identity.publicKey)
        )
        val payload = SignedKeyExchangeCodec.assembleSigned(
            type,
            sessionKey,
            identity.publicKey,
            signature,
        )

        val decoded = SignedKeyExchangeCodec.decodeAndVerify(payload)
        assertEquals(type, decoded.type)
        assertArrayEquals(sessionKey, decoded.sessionPublicKey)
        assertArrayEquals(identity.publicKey, decoded.identityPublicKey)

        listOf(3, 40, payload.lastIndex).forEach { changedIndex ->
            val tampered = payload.copyOf().also {
                it[changedIndex] = (it[changedIndex].toInt() xor 1).toByte()
            }
            assertThrows(IllegalArgumentException::class.java) {
                SignedKeyExchangeCodec.decodeAndVerify(tampered)
            }
        }
    }

    @Test
    fun legacyPacketIsRecognizedOnlyForParsingAndHasNoTrustedIdentity() {
        val sessionKey = ByteArray(32) { 7 }
        val legacy = byteArrayOf(
            EncryptionController.MessageRequestType.TYPE_REQUEST.code,
            32,
        ) + sessionKey

        val parsed = SignedKeyExchangeCodec.decodeAndVerify(legacy)
        assertArrayEquals(sessionKey, parsed.sessionPublicKey)
        assertNull(parsed.identityPublicKey)
    }

    @Test
    fun changedIdentityIsBlockedUntilExplicitAcceptanceAndFreshExchange() {
        val original = ByteArray(32) { 1 }
        val replacement = ByteArray(32) { 2 }
        val verified = ContactIdentity(IdentityVerificationStatus.VERIFIED, original)

        val changed = IdentityKeyManager.evaluateRemoteIdentity(verified, replacement)
        assertTrue(changed.keyChanged)
        assertEquals(IdentityVerificationStatus.KEY_CHANGED, changed.identity.status)
        assertArrayEquals(original, changed.identity.publicKey)
        assertArrayEquals(replacement, changed.identity.candidatePublicKey)

        val explicitlyAccepted = ContactIdentity(
            IdentityVerificationStatus.REKEY_REQUIRED,
            replacement,
        )
        val afterFreshExchange = IdentityKeyManager.evaluateRemoteIdentity(
            explicitlyAccepted,
            replacement,
        )
        assertFalse(afterFreshExchange.keyChanged)
        assertEquals(IdentityVerificationStatus.UNVERIFIED, afterFreshExchange.identity.status)
    }

    @Test
    fun simultaneousRequestsChooseExactlyOneDeterministicInitiator() {
        val lower = ByteArray(32).also { it[31] = 1 }
        val higher = ByteArray(32).also { it[31] = 2 }

        assertTrue(EncryptionController.keepInitiatorOnSimultaneousRequest(lower, higher))
        assertFalse(EncryptionController.keepInitiatorOnSimultaneousRequest(higher, lower))
        assertThrows(SecurityException::class.java) {
            EncryptionController.keepInitiatorOnSimultaneousRequest(lower, lower.copyOf())
        }
    }
}
