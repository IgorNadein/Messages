package com.afkanerd.deku.attachments.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object HkdfSha256 {
    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(ikm.isNotEmpty() && length in 1..(255 * 32))
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        return try {
            val output = ByteArray(length)
            var previous = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                val expand = Mac.getInstance("HmacSHA256")
                expand.init(SecretKeySpec(prk, "HmacSHA256"))
                expand.update(previous)
                expand.update(info)
                previous = expand.doFinal(byteArrayOf(counter.toByte()))
                val copied = minOf(previous.size, length - offset)
                previous.copyInto(output, offset, 0, copied)
                offset += copied
                counter++
            }
            previous.fill(0)
            output
        } finally {
            prk.fill(0)
        }
    }
}
