package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibleTextSmsCodecTest {
    private val frame = SmsFrame(
        packetType = SmsPacketType.TRANSFER_CHUNK,
        transferId = TransferId.fromBytes(ByteArray(16) { it.toByte() }),
        chunkIndex = 86,
        totalChunks = 108,
        payload = ByteArray(94) { (it * 7).toByte() },
    )

    @Test
    fun `full attachment frame survives ordinary sms text encoding`() {
        val encoded = CompatibleTextSmsCodec.encode(frame)
        assertTrue(encoded.startsWith(CompatibleTextSmsCodec.PREFIX))
        assertEquals(frame, CompatibleTextSmsCodec.decodeOrNull(encoded))
    }

    @Test
    fun `ordinary and malformed base64 messages are not consumed`() {
        assertNull(CompatibleTextSmsCodec.decodeOrNull("Hello from an ordinary SMS"))
        assertNull(CompatibleTextSmsCodec.decodeOrNull("${CompatibleTextSmsCodec.PREFIX}broken"))
        assertNull(CompatibleTextSmsCodec.decodeOrNull("${CompatibleTextSmsCodec.PREFIX}SGVsbG8="))
    }
}
