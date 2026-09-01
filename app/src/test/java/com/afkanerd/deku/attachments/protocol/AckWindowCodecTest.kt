package com.afkanerd.deku.attachments.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.BitSet

class AckWindowCodecTest {
    @Test
    fun compactBitmapRoundTrip() {
        val bits = BitSet(64).apply { set(0); set(1); set(3); set(63) }
        val encoded = AckWindowCodec.encode(AckWindow(128, 64, bits))
        assertEquals(11, encoded.size)
        val decoded = AckWindowCodec.decode(encoded)
        assertEquals(128, decoded.baseIndex)
        assertEquals(64, decoded.bitCount)
        assertEquals(bits, decoded.received)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonCanonicalUnusedBits() {
        AckWindowCodec.decode(byteArrayOf(0, 0, 1, 2))
    }
}
