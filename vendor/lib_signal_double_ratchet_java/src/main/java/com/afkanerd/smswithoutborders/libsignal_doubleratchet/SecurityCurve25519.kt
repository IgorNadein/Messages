package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import com.github.netricecake.ecdh.Curve25519

class SecurityCurve25519(val privateKey: ByteArray = Curve25519.generateRandomKey()) {
    init {
        require(privateKey.size == KEY_SIZE) { "Invalid X25519 private key" }
    }

    fun generateKey(): ByteArray {
        return Curve25519.publicKey(this.privateKey)
    }

    fun calculateSharedSecret(publicKey: ByteArray): ByteArray {
        require(publicKey.size == KEY_SIZE) { "Invalid X25519 public key" }
        val sharedKey = Curve25519.sharedSecret(this.privateKey, publicKey)
        require(sharedKey.any { it != 0.toByte() }) { "Rejected low-order X25519 public key" }
        return try {
            CryptoHelpers.HKDF("HMACSHA256", sharedKey, null,
                "x25591_key_exchange".encodeToByteArray(), KEY_SIZE, 1)[0]
        } finally {
            sharedKey.fill(0)
        }
    }

    fun getKeypair(): android.util.Pair<ByteArray, ByteArray> {
        return android.util.Pair(privateKey, generateKey())
    }

    companion object {
        private const val KEY_SIZE = 32
    }
}
