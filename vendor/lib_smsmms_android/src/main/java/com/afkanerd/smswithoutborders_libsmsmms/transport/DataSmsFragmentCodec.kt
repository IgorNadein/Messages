package com.afkanerd.smswithoutborders_libsmsmms.transport

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

/** Small persistent framing layer because Android has no multipart Data-SMS API. */
object DataSmsFragmentCodec {
    const val MAX_FRAME_BYTES = 120
    const val HEADER_BYTES = 16
    const val MAX_PAYLOAD_BYTES = MAX_FRAME_BYTES - HEADER_BYTES
    const val MAX_PARTS = 64

    data class Frame(
        val messageId: Long,
        val index: Int,
        val total: Int,
        val payload: ByteArray,
    )

    sealed interface DecodeResult {
        data class Success(val frame: Frame) : DecodeResult
        data object NotFragment : DecodeResult
        data object Rejected : DecodeResult
    }

    fun fragment(payload: ByteArray): List<ByteArray> {
        require(payload.isNotEmpty())
        val total = (payload.size + MAX_PAYLOAD_BYTES - 1) / MAX_PAYLOAD_BYTES
        require(total in 1..MAX_PARTS) { "Data SMS payload is too large" }
        val messageId = SecureRandom().nextLong()
        return List(total) { index ->
            val start = index * MAX_PAYLOAD_BYTES
            val end = minOf(payload.size, start + MAX_PAYLOAD_BYTES)
            encode(Frame(messageId, index, total, payload.copyOfRange(start, end)))
        }
    }

    fun decode(bytes: ByteArray): DecodeResult {
        if(bytes.size < 2 || bytes[0] != MAGIC_HIGH || bytes[1] != MAGIC_LOW) {
            return DecodeResult.NotFragment
        }
        if(bytes.size !in HEADER_BYTES..MAX_FRAME_BYTES) return DecodeResult.Rejected
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if(input.short.toInt() and 0xffff != MAGIC) return DecodeResult.Rejected
        if(input.get().toInt() and 0xff != VERSION) return DecodeResult.Rejected
        val messageId = input.long
        val index = input.short.toInt() and 0xffff
        val total = input.short.toInt() and 0xffff
        val length = input.get().toInt() and 0xff
        if(total !in 1..MAX_PARTS || index !in 0 until total ||
            length !in 1..MAX_PAYLOAD_BYTES || length != input.remaining()
        ) return DecodeResult.Rejected
        return DecodeResult.Success(
            Frame(messageId, index, total, ByteArray(length).also(input::get))
        )
    }

    private fun encode(frame: Frame): ByteArray = ByteBuffer
        .allocate(HEADER_BYTES + frame.payload.size)
        .order(ByteOrder.BIG_ENDIAN)
        .putShort(MAGIC.toShort())
        .put(VERSION.toByte())
        .putLong(frame.messageId)
        .putShort(frame.index.toShort())
        .putShort(frame.total.toShort())
        .put(frame.payload.size.toByte())
        .put(frame.payload)
        .array()

    private const val MAGIC = 0x4453
    private const val VERSION = 1
    private const val MAGIC_HIGH: Byte = 0x44
    private const val MAGIC_LOW: Byte = 0x53
}

internal object DataSmsFragmentStore {
    sealed interface Result {
        data object Pending : Result
        data object Rejected : Result
        data class Complete(val payload: ByteArray) : Result
    }

    private val lock = Any()

    fun accept(context: Context, address: String, frame: DataSmsFragmentCodec.Frame): Result =
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            cleanup(prefs, now)
            val base = baseKey(address, frame.messageId)
            val storedTotal = prefs.getInt("$base.total", -1)
            if(storedTotal != -1 && storedTotal != frame.total) {
                remove(prefs, base, storedTotal)
                return@synchronized Result.Rejected
            }
            val encoded = Base64.encodeToString(frame.payload, Base64.NO_WRAP)
            val existing = prefs.getString("$base.${frame.index}", null)
            if(existing != null && existing != encoded) {
                remove(prefs, base, frame.total)
                return@synchronized Result.Rejected
            }
            val active = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toMutableSet()
            active += base
            prefs.edit()
                .putStringSet(KEY_ACTIVE, active)
                .putInt("$base.total", frame.total)
                .putLong("$base.time", now)
                .putString("$base.${frame.index}", encoded)
                .commit()

            val parts = (0 until frame.total).map { index ->
                prefs.getString("$base.$index", null)?.let {
                    runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
                }
            }
            if(parts.any { it == null }) return@synchronized Result.Pending
            val size = parts.sumOf { it!!.size }
            if(size > DataSmsFragmentCodec.MAX_PAYLOAD_BYTES * DataSmsFragmentCodec.MAX_PARTS) {
                remove(prefs, base, frame.total)
                return@synchronized Result.Rejected
            }
            val assembled = ByteArray(size)
            var offset = 0
            parts.forEach { part ->
                part!!.copyInto(assembled, offset)
                offset += part.size
            }
            remove(prefs, base, frame.total)
            Result.Complete(assembled)
        }

    private fun cleanup(
        prefs: android.content.SharedPreferences,
        now: Long,
    ) {
        val active = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toMutableSet()
        val expired = active.filter { base ->
            now - prefs.getLong("$base.time", 0L) > TTL_MILLIS
        }.toMutableSet()
        if(active.size - expired.size > MAX_PENDING) {
            active.filterNot(expired::contains)
                .sortedBy { prefs.getLong("$it.time", 0L) }
                .take(active.size - expired.size - MAX_PENDING)
                .forEach(expired::add)
        }
        expired.forEach { base -> remove(prefs, base, prefs.getInt("$base.total", 0)) }
    }

    private fun remove(
        prefs: android.content.SharedPreferences,
        base: String,
        total: Int,
    ) {
        val active = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toMutableSet()
        active.remove(base)
        val editor = prefs.edit().putStringSet(KEY_ACTIVE, active)
            .remove("$base.total").remove("$base.time")
        repeat(total.coerceIn(0, DataSmsFragmentCodec.MAX_PARTS)) { editor.remove("$base.$it") }
        editor.commit()
    }

    private fun baseKey(address: String, messageId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(address.encodeToByteArray())
        val addressKey = digest.take(12).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        return "$addressKey-${messageId.toULong()}"
    }

    private const val PREFS = "data_sms_fragments_v1"
    private const val KEY_ACTIVE = "active"
    private const val MAX_PENDING = 32
    private const val TTL_MILLIS = 24 * 60 * 60 * 1000L
}
