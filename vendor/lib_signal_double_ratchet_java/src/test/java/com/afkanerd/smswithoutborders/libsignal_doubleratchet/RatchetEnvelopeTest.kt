package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal.States
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RatchetEnvelopeTest {
    @Test
    fun pendingCiphertextAndStateSurviveRestart() {
        val envelope = RatchetEnvelope(
            ratchetState = States().serializedStates,
            pending = listOf(
                PendingCipherText("одинаковый текст", "ciphertext-one"),
                PendingCipherText("одинаковый текст", "ciphertext-two"),
            ),
        )

        val restored = RatchetEnvelope.deserialize(envelope.serialize())
        assertEquals(envelope.ratchetState, restored.ratchetState)
        assertEquals(envelope.pending, restored.pending)
    }

    @Test
    fun legacyRawRatchetStateMigratesIntoEnvelope() {
        val state = States().serializedStates
        val restored = RatchetEnvelope.deserialize(state.encodeToByteArray())
        assertEquals(state, restored.ratchetState)
        assertTrue(restored.pending.isEmpty())
    }

    @Test
    fun corruptEnvelopeFailsClosed() {
        assertThrows(Exception::class.java) {
            RatchetEnvelope.deserialize("{not-json".encodeToByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RatchetEnvelope.deserialize(
                """{"envelopeSchema":999,"pending":[]}""".encodeToByteArray()
            )
        }
    }
}
