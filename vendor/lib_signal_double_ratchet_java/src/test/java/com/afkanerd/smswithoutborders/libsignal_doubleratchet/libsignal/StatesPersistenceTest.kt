package com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal

import android.util.Pair
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class StatesPersistenceTest {
    @Test
    fun stableSchemaSurvivesPersistenceAndSecondEncryption() {
        val bob = Protocols.GENERATE_DH()
        val alice = States()
        Ratchets.ratchetInitAlice(alice, ByteArray(32) { 0x31 }, bob.second)

        val first = Ratchets.ratchetEncrypt(
            alice,
            "first".toByteArray(StandardCharsets.UTF_8),
            bob.second,
        )
        assertTrue(first.second.isNotEmpty())

        val persisted = alice.serializedStates
        assertTrue(persisted.contains("\"schema\":2"))
        assertTrue(persisted.contains("\"Ns\":1"))

        val restored = States(persisted)
        val second = Ratchets.ratchetEncrypt(
            restored,
            "second".toByteArray(StandardCharsets.UTF_8),
            bob.second,
        )

        assertTrue(second.second.isNotEmpty())
        assertEquals(2, restored.Ns)
        assertEquals(0, restored.Nr)
        assertEquals(0, restored.PN)
    }

    @Test
    fun skippedKeysUsePublicKeyContentsNotArrayIdentity() {
        val state = populatedState()
        val serialized = state.serializedStates
        val restored = States(serialized)

        val entry = restored.MKSKIPPED.entries.single()
        assertArrayEquals(ByteArray(32) { 0x55 }, entry.key.first)
        assertEquals(7, entry.key.second)
        assertArrayEquals(ByteArray(32) { 0x66 }, entry.value)
    }

    @Test(expected = org.json.JSONException::class)
    fun obfuscatedOrCorruptedLegacyCountersFailClosed() {
        States("""{"a":1,"b":2,"c":3,"MKSKIPPED":[]}""")
    }

    @Test(expected = org.json.JSONException::class)
    fun unknownStateSchemaFailsClosed() {
        States("""{"schema":999,"Ns":0,"Nr":0,"PN":0,"MKSKIPPED":[]}""")
    }

    private fun populatedState() = States().apply {
        DHs = Pair(ByteArray(32) { 0x11 }, ByteArray(32) { 0x22 })
        DHr = ByteArray(32) { 0x33 }
        RK = ByteArray(32) { 0x44 }
        Ns = 4
        Nr = 5
        PN = 3
        MKSKIPPED[Pair(ByteArray(32) { 0x55 }, 7)] = ByteArray(32) { 0x66 }
    }
}
