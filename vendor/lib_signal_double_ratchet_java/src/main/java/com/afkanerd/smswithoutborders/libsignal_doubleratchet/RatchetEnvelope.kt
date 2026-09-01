package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import org.json.JSONArray
import org.json.JSONObject

internal data class PendingCipherText(
    val plaintext: String,
    val transportText: String,
)

internal data class RatchetEnvelope(
    val ratchetState: String?,
    val pending: List<PendingCipherText> = emptyList(),
) {
    fun serialize(): ByteArray = JSONObject().apply {
        put(FIELD_SCHEMA, SCHEMA_VERSION)
        ratchetState?.let { put(FIELD_STATE, JSONObject(it)) }
        put(FIELD_PENDING, JSONArray().apply {
            pending.forEach { item ->
                put(JSONObject().apply {
                    put(FIELD_PLAINTEXT, item.plaintext)
                    put(FIELD_TRANSPORT, item.transportText)
                })
            }
        })
    }.toString().encodeToByteArray()

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val FIELD_SCHEMA = "envelopeSchema"
        private const val FIELD_STATE = "ratchetState"
        private const val FIELD_PENDING = "pending"
        private const val FIELD_PLAINTEXT = "plaintext"
        private const val FIELD_TRANSPORT = "transportText"
        private const val MAX_PENDING = 100
        private const val MAX_FIELD_LENGTH = 1_500_000

        fun deserialize(data: ByteArray?): RatchetEnvelope {
            if(data == null) return RatchetEnvelope(null)
            val encoded = data.decodeToString()
            val input = JSONObject(encoded)

            // Backward-compatible migration from the pre-envelope States JSON.
            if(!input.has(FIELD_SCHEMA)) return RatchetEnvelope(encoded)
            require(input.getInt(FIELD_SCHEMA) == SCHEMA_VERSION) {
                "Unsupported ratchet envelope schema"
            }

            val state = input.optJSONObject(FIELD_STATE)?.toString()
            val pendingJson = input.optJSONArray(FIELD_PENDING) ?: JSONArray()
            require(pendingJson.length() <= MAX_PENDING) { "Too many pending secure messages" }
            val pending = buildList {
                repeat(pendingJson.length()) { index ->
                    val item = pendingJson.getJSONObject(index)
                    val plaintext = item.getString(FIELD_PLAINTEXT)
                    val transport = item.getString(FIELD_TRANSPORT)
                    require(plaintext.length <= MAX_FIELD_LENGTH &&
                        transport.length <= MAX_FIELD_LENGTH) {
                        "Pending secure message is too large"
                    }
                    add(PendingCipherText(plaintext, transport))
                }
            }
            return RatchetEnvelope(state, pending)
        }
    }
}
