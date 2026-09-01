package com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RatchetLifecycleTest {
    @Test
    fun oneHundredAlternatingMessagesSurviveRestartAfterEveryMessage() {
        val session = newSession()
        var alice = session.alice
        var bob = session.bob

        repeat(100) { index ->
            if(index % 2 == 0) {
                val expected = "Alice #$index — Привет 👋🏽"
                val encrypted = Ratchets.ratchetEncrypt(
                    alice,
                    expected.toByteArray(StandardCharsets.UTF_8),
                    session.bobPublic,
                )
                alice = States(alice.serializedStates)
                assertEquals(
                    expected,
                    String(
                        Ratchets.ratchetDecrypt(
                            bob,
                            encrypted.first,
                            encrypted.second,
                            session.bobPublic,
                        ),
                        StandardCharsets.UTF_8,
                    ),
                )
                bob = States(bob.serializedStates)
            } else {
                val expected = "Bob #$index — ответ 🚀"
                val encrypted = Ratchets.ratchetEncrypt(
                    bob,
                    expected.toByteArray(StandardCharsets.UTF_8),
                    session.alicePublic,
                )
                bob = States(bob.serializedStates)
                assertEquals(
                    expected,
                    String(
                        Ratchets.ratchetDecrypt(
                            alice,
                            encrypted.first,
                            encrypted.second,
                            session.alicePublic,
                        ),
                        StandardCharsets.UTF_8,
                    ),
                )
                alice = States(alice.serializedStates)
            }
        }
    }

    @Test
    fun outOfOrderMessagesUsePersistedSkippedKeysAndDuplicatesFail() {
        val session = newSession()
        val messages = listOf("zero", "one", "two").map { text ->
            Ratchets.ratchetEncrypt(
                session.alice,
                text.encodeToByteArray(),
                session.bobPublic,
            )
        }

        assertArrayEquals(
            "two".encodeToByteArray(),
            Ratchets.ratchetDecrypt(
                session.bob,
                messages[2].first,
                messages[2].second,
                session.bobPublic,
            ),
        )
        var restoredBob = States(session.bob.serializedStates)
        assertArrayEquals(
            "zero".encodeToByteArray(),
            Ratchets.ratchetDecrypt(
                restoredBob,
                messages[0].first,
                messages[0].second,
                session.bobPublic,
            ),
        )
        restoredBob = States(restoredBob.serializedStates)
        assertArrayEquals(
            "one".encodeToByteArray(),
            Ratchets.ratchetDecrypt(
                restoredBob,
                messages[1].first,
                messages[1].second,
                session.bobPublic,
            ),
        )

        val stateAfterSuccess = States(restoredBob.serializedStates)
        assertThrows(Throwable::class.java) {
            Ratchets.ratchetDecrypt(
                stateAfterSuccess,
                messages[1].first,
                messages[1].second,
                session.bobPublic,
            )
        }
    }

    @Test
    fun tamperingAndWrongAssociatedDataFailAuthentication() {
        val session = newSession()
        val encrypted = Ratchets.ratchetEncrypt(
            session.alice,
            "authenticated".encodeToByteArray(),
            session.bobPublic,
        )
        val tampered = encrypted.second.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }

        assertThrows(Throwable::class.java) {
            Ratchets.ratchetDecrypt(
                States(session.bob.serializedStates),
                encrypted.first,
                tampered,
                session.bobPublic,
            )
        }
        assertThrows(Throwable::class.java) {
            Ratchets.ratchetDecrypt(
                States(session.bob.serializedStates),
                encrypted.first,
                encrypted.second,
                ByteArray(32) { 9 },
            )
        }
    }

    @Test
    fun veryLongUnicodePayloadRoundTrips() {
        val session = newSession()
        val expected = "Сообщение 🛡️ é 漢字\n".repeat(4096)
        val encrypted = Ratchets.ratchetEncrypt(
            session.alice,
            expected.encodeToByteArray(),
            session.bobPublic,
        )
        val decrypted = Ratchets.ratchetDecrypt(
            session.bob,
            encrypted.first,
            encrypted.second,
            session.bobPublic,
        )
        assertEquals(expected, decrypted.toString(StandardCharsets.UTF_8))
    }

    private fun newSession(): Session {
        val sharedSecret = ByteArray(32) { index -> (index + 1).toByte() }
        val bobKeyPair = Protocols.GENERATE_DH()
        val alice = States()
        Ratchets.ratchetInitAlice(alice, sharedSecret, bobKeyPair.second)
        val alicePublic = alice.DHs.second.copyOf()
        val bob = States()
        Ratchets.ratchetInitBob(bob, sharedSecret, bobKeyPair)
        return Session(alice, bob, alicePublic, bobKeyPair.second.copyOf())
    }

    private data class Session(
        val alice: States,
        val bob: States,
        val alicePublic: ByteArray,
        val bobPublic: ByteArray,
    )
}
