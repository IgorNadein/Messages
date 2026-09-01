package com.afkanerd.smswithoutborders_libsmsmms.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretBytesTest {
    @Test
    fun passwordRemainsUsableUntilScopeClosesThenIsZeroed() {
        val expected = ByteArray(32) { index -> (index + 1).toByte() }
        val secret = Cryptography.SecretBytes(expected.copyOf())
        lateinit var observedBackingArray: ByteArray

        secret.useRaw { raw ->
            observedBackingArray = raw
            assertArrayEquals(expected, raw)
            assertFalse(raw.all { it == 0.toByte() })
        }
        secret.close()

        assertTrue(observedBackingArray.all { it == 0.toByte() })
        assertThrows(IllegalStateException::class.java) {
            secret.useRaw { }
        }
    }
}
