package com.afkanerd.deku.attachments.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.BitSet

data class AckWindow(val baseIndex: Int, val bitCount: Int, val received: BitSet)

object AckWindowCodec {
    fun encode(window: AckWindow): ByteArray {
        require(window.baseIndex in 0 until TransferLimits.MAX_TOTAL_CHUNKS)
        require(window.bitCount in 1..TransferLimits.ACK_WINDOW_BITS)
        require(window.received.length() <= window.bitCount)
        val bitmapBytes = (window.bitCount + 7) / 8
        val output = ByteBuffer.allocate(3 + bitmapBytes).order(ByteOrder.BIG_ENDIAN)
            .putShort(window.baseIndex.toShort()).put(window.bitCount.toByte())
        repeat(bitmapBytes) { byteIndex ->
            var value = 0
            repeat(8) { bit ->
                val relative = byteIndex * 8 + bit
                if (relative < window.bitCount && window.received[relative]) value = value or (1 shl bit)
            }
            output.put(value.toByte())
        }
        return output.array()
    }

    fun decode(data: ByteArray): AckWindow {
        require(data.size in 4..11) { "ACK window length is out of bounds" }
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val base = input.short.toInt() and 0xffff
        val bitCount = input.get().toInt() and 0xff
        require(base < TransferLimits.MAX_TOTAL_CHUNKS && bitCount in 1..TransferLimits.ACK_WINDOW_BITS)
        require(input.remaining() == (bitCount + 7) / 8) { "ACK bitmap length mismatch" }
        val bits = BitSet(bitCount)
        repeat(input.remaining()) { byteIndex ->
            val value = input.get().toInt() and 0xff
            repeat(8) { bit ->
                val relative = byteIndex * 8 + bit
                if (relative < bitCount && value and (1 shl bit) != 0) bits.set(relative)
            }
            if (byteIndex == (bitCount - 1) / 8 && bitCount % 8 != 0) {
                val unusedMask = 0xff shl (bitCount % 8)
                require(value and unusedMask == 0) { "Non-zero unused ACK bits" }
            }
        }
        return AckWindow(base, bitCount, bits)
    }
}
