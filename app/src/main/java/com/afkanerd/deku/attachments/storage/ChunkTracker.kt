package com.afkanerd.deku.attachments.storage

import com.afkanerd.deku.attachments.protocol.TransferLimits
import java.util.BitSet

class ChunkTracker private constructor(
    val totalChunks: Int,
    private val bits: BitSet,
) {
    init { require(totalChunks in 1..TransferLimits.MAX_TOTAL_CHUNKS) }

    fun mark(index: Int): Boolean {
        require(index in 0 until totalChunks)
        val wasSet = bits[index]
        bits.set(index)
        return !wasSet
    }

    fun clear(index: Int) {
        require(index in 0 until totalChunks)
        bits.clear(index)
    }

    operator fun contains(index: Int): Boolean = index in 0 until totalChunks && bits[index]

    fun count(): Int = bits.cardinality()

    fun isComplete(): Boolean = count() == totalChunks

    fun missing(): IntArray = (0 until totalChunks).filterNot(bits::get).toIntArray()

    fun window(baseIndex: Int, bitCount: Int): BitSet {
        require(baseIndex in 0 until totalChunks)
        require(bitCount in 1..TransferLimits.ACK_WINDOW_BITS)
        val actual = minOf(bitCount, totalChunks - baseIndex)
        return BitSet(actual).also { result ->
            repeat(actual) { if (bits[baseIndex + it]) result.set(it) }
        }
    }

    fun serialize(): ByteArray {
        val size = (totalChunks + 7) / 8
        return bits.toByteArray().copyOf(size)
    }

    companion object {
        fun empty(totalChunks: Int): ChunkTracker = ChunkTracker(totalChunks, BitSet(totalChunks))

        fun restore(totalChunks: Int, encoded: ByteArray): ChunkTracker {
            require(encoded.size <= (totalChunks + 7) / 8) { "Chunk bitmap is too large" }
            val bits = BitSet.valueOf(encoded)
            require(bits.length() <= totalChunks) { "Chunk bitmap contains out-of-range bits" }
            return ChunkTracker(totalChunks, bits)
        }
    }
}
