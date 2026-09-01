package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureModePersistenceTest {
    @Test
    fun onlyInitiatorMayCreateTheFirstSendingChain() {
        EncryptionController.requireInitialEncryptRole(
            EncryptionController.SessionRole.INITIATOR
        )
        assertThrows(IllegalStateException::class.java) {
            EncryptionController.requireInitialEncryptRole(
                EncryptionController.SessionRole.RESPONDER
            )
        }
    }

    @Test
    fun onlyResponderMayCreateTheInitialReceivingState() {
        EncryptionController.requireInitialDecryptRole(
            EncryptionController.SessionRole.RESPONDER
        )
        assertThrows(IllegalStateException::class.java) {
            EncryptionController.requireInitialDecryptRole(
                EncryptionController.SessionRole.INITIATOR
            )
        }
    }

    @Test
    fun explicitModeSchemaSurvivesObfuscationIndependentRoundTrip() {
        val publicKey = ByteArray(32) { it.toByte() }
        val encodedKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)
        val stored = SavedEncryptedModes(
            mode = EncryptionController.SecureRequestMode.REQUEST_ACCEPTED,
            publicKey = encodedKey,
            role = EncryptionController.SessionRole.INITIATOR,
        ).serialize()

        assertFalse(stored.contains("SavedEncryptedModes"))
        val restored = SavedEncryptedModes.deserialize(stored)
        assertEquals(EncryptionController.SecureRequestMode.REQUEST_ACCEPTED, restored.mode)
        assertEquals(EncryptionController.SessionRole.INITIATOR, restored.role)
        assertArrayEquals(publicKey, Base64.decode(restored.publicKey, Base64.NO_WRAP))
    }

    @Test
    fun brokenModeSurvivesPersistenceRoundTrip() {
        val restored = SavedEncryptedModes.deserialize(
            SavedEncryptedModes(
                mode = EncryptionController.SecureRequestMode.REQUEST_BROKEN,
                role = EncryptionController.SessionRole.RESPONDER,
            ).serialize()
        )

        assertEquals(EncryptionController.SecureRequestMode.REQUEST_BROKEN, restored.mode)
        assertEquals(EncryptionController.SessionRole.RESPONDER, restored.role)
    }
}
