package com.afkanerd.smswithoutborders_libsmsmms.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSmsFragmentCodecTest {
    @Test
    fun `short payload uses one frame and round trips`() {
        val payload = "защищённое сообщение".encodeToByteArray()

        val frames = DataSmsFragmentCodec.fragment(payload)
        val decoded = DataSmsFragmentCodec.decode(frames.single())
            as DataSmsFragmentCodec.DecodeResult.Success

        assertEquals(0, decoded.frame.index)
        assertEquals(1, decoded.frame.total)
        assertArrayEquals(payload, decoded.frame.payload)
    }

    @Test
    fun `large payload is safely split and reassembled out of order`() {
        val payload = ByteArray(DataSmsFragmentCodec.MAX_PAYLOAD_BYTES * 3 + 17) {
            (it * 31).toByte()
        }

        val decoded = DataSmsFragmentCodec.fragment(payload)
            .reversed()
            .map { DataSmsFragmentCodec.decode(it) as DataSmsFragmentCodec.DecodeResult.Success }
            .map { it.frame }
        val messageIds = decoded.map { it.messageId }.distinct()
        val assembled = decoded.sortedBy { it.index }
            .flatMap { it.payload.asIterable() }
            .toByteArray()

        assertEquals(1, messageIds.size)
        assertTrue(decoded.all { it.total == 4 })
        assertArrayEquals(payload, assembled)
    }

    @Test
    fun `corrupt framed payload is rejected rather than exposed as legacy data`() {
        val frame = DataSmsFragmentCodec.fragment(ByteArray(12) { 7 }).single()
        frame[15] = 99

        assertEquals(
            DataSmsFragmentCodec.DecodeResult.Rejected,
            DataSmsFragmentCodec.decode(frame),
        )
    }

    @Test
    fun `unframed payload remains compatible with existing data sms handlers`() {
        assertEquals(
            DataSmsFragmentCodec.DecodeResult.NotFragment,
            DataSmsFragmentCodec.decode(byteArrayOf(0x03, 0x20, 0x30)),
        )
    }
}
