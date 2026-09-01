package com.afkanerd.deku.attachments.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferOverheadTest {
    @Test
    fun implementationOverheadReport() {
        println("Transport payload: ${TransferLimits.FRAME_BYTES} B (conservative application frame)")
        println("16-bit port UDH overhead: 7 B; theoretical TP-UD application maximum: 133 B")
        println("Protocol header: ${TransferLimits.FRAME_HEADER_BYTES} B")
        println("AEAD overhead: ${TransferLimits.AEAD_TAG_BYTES} B")
        println("Usable attachment bytes/SMS: ${TransferLimits.CHUNK_PLAINTEXT_BYTES} B")
        listOf(1L, 100L, 1024L, 10 * 1024L, 100 * 1024L).forEach {
            println("$it bytes -> ${TransferLimits.chunkCount(it)} data SMS")
        }
        assertEquals(78, TransferLimits.CHUNK_PLAINTEXT_BYTES)
        assertEquals(1313, TransferLimits.chunkCount(100 * 1024L))
    }
}
