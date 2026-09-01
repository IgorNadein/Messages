package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.telephony.SmsManager
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class MmsCarrierLimits(
    val maxMessageBytes: Int,
    val maxImageWidth: Int,
    val maxImageHeight: Int,
)

data class PreparedMmsAttachment(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

object MmsCarrierPolicy {
    fun attachmentBudget(maxMessageBytes: Int, bodyBytes: Int): Int =
        (maxMessageBytes * SAFETY_FACTOR).toInt() - bodyBytes - ENVELOPE_RESERVE_BYTES

    private const val SAFETY_FACTOR = 0.90f
    private const val ENVELOPE_RESERVE_BYTES = 5 * 1024
}

@Suppress("DEPRECATION")
fun Context.getMmsCarrierLimits(subscriptionId: Long): MmsCarrierLimits {
    val manager = if(subscriptionId >= 0) {
        SmsManager.getSmsManagerForSubscriptionId(subscriptionId.toInt())
    } else {
        SmsManager.getDefault()
    }
    val config = runCatching { manager.carrierConfigValues }.getOrNull()
    return MmsCarrierLimits(
        maxMessageBytes = config
            ?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_MESSAGE_BYTES,
        maxImageWidth = config
            ?.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH)
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_IMAGE_DIMENSION,
        maxImageHeight = config
            ?.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT)
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_IMAGE_DIMENSION,
    )
}

fun Context.prepareMmsAttachment(
    uri: Uri,
    mimeType: String,
    fileName: String,
    subscriptionId: Long,
    body: String,
): PreparedMmsAttachment {
    val limits = getMmsCarrierLimits(subscriptionId)
    val budget = MmsCarrierPolicy.attachmentBudget(
        maxMessageBytes = limits.maxMessageBytes,
        bodyBytes = body.toByteArray().size,
    )
    require(budget > 0) { "MMS text exceeds the carrier message-size limit" }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if(mimeType.startsWith("image/") && mimeType != "image/gif") {
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported MMS image" }
        val sourceBytes = contentResolver.openInputStream(uri)?.use { input ->
            input.readAtMost(budget + 1)
        } ?: error("Unable to read MMS attachment")
        if(sourceBytes.size <= budget &&
            bounds.outWidth <= limits.maxImageWidth &&
            bounds.outHeight <= limits.maxImageHeight
        ) {
            return PreparedMmsAttachment(sourceBytes, mimeType, fileName)
        }
        return transcodeMmsImage(uri, limits, budget, fileName)
    }

    val bytes = contentResolver.openInputStream(uri)?.use { input ->
        input.readAtMost(budget + 1)
    } ?: error("Unable to read MMS attachment")
    require(bytes.size <= budget) { "Attachment exceeds the carrier MMS size limit" }
    return PreparedMmsAttachment(bytes, mimeType, fileName)
}

private fun Context.transcodeMmsImage(
    uri: Uri,
    limits: MmsCarrierLimits,
    budget: Int,
    originalName: String,
): PreparedMmsAttachment {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while(bounds.outWidth / sample > limits.maxImageWidth * 2 ||
        bounds.outHeight / sample > limits.maxImageHeight * 2
    ) sample *= 2
    var bitmap = contentResolver.openInputStream(uri).use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } ?: error("Unable to decode MMS image")
    bitmap = bitmap.scaledWithin(limits.maxImageWidth, limits.maxImageHeight)

    val output = ByteArrayOutputStream()
    val qualities = intArrayOf(88, 78, 68, 58, 48, 38, 30, 24)
    var qualityIndex = 0
    while(true) {
        output.reset()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, qualities[qualityIndex], output))
        if(output.size() <= budget) break
        if(qualityIndex < qualities.lastIndex) {
            qualityIndex++
        } else {
            require(bitmap.width > MIN_IMAGE_DIMENSION || bitmap.height > MIN_IMAGE_DIMENSION) {
                "Image cannot be compressed to the carrier MMS size limit"
            }
            val resized = Bitmap.createScaledBitmap(
                bitmap,
                maxOf(MIN_IMAGE_DIMENSION, (bitmap.width * 0.78f).toInt()),
                maxOf(MIN_IMAGE_DIMENSION, (bitmap.height * 0.78f).toInt()),
                true,
            )
            if(resized !== bitmap) bitmap.recycle()
            bitmap = resized
            qualityIndex = 2
        }
    }
    bitmap.recycle()
    return PreparedMmsAttachment(
        bytes = output.toByteArray(),
        mimeType = "image/jpeg",
        fileName = originalName.substringBeforeLast('.', originalName) + ".jpg",
    )
}

private fun Bitmap.scaledWithin(maxWidth: Int, maxHeight: Int): Bitmap {
    val scale = minOf(1f, maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    if(scale >= 1f) return this
    return Bitmap.createScaledBitmap(
        this,
        maxOf(1, (width * scale).toInt()),
        maxOf(1, (height * scale).toInt()),
        true,
    ).also { scaled -> if(scaled !== this) recycle() }
}

private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = maxBytes
    while(remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if(read < 0) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}

private const val DEFAULT_MAX_MESSAGE_BYTES = 300 * 1024
private const val DEFAULT_MAX_IMAGE_DIMENSION = 1280
private const val MIN_IMAGE_DIMENSION = 96
