package com.afkanerd.deku.attachments.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.afkanerd.deku.attachments.protocol.TransferLimits
import java.io.File
import java.io.FileOutputStream

class PhotoCompressor(private val context: Context) {
    enum class Preset(val targetBytes: Int, val maxDimension: Int) {
        LOW(12 * 1024, 320),
        MEDIUM(32 * 1024, 640),
        HIGH(96 * 1024, 1280),
        ORIGINAL(TransferLimits.MAX_TRANSFER_BYTES, 4096),
    }

    data class Result(
        val file: File,
        val originalBytes: Long,
        val encodedBytes: Long,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val codec: String,
    )

    fun compress(
        source: Uri,
        preset: Preset,
        maxEncodedBytes: Long = TransferLimits.MAX_TRANSFER_BYTES.toLong(),
    ): Result {
        require(maxEncodedBytes > 0)
        val originalBytes = context.contentResolver.openAssetFileDescriptor(source, "r")
            ?.use { it.length.takeIf { size -> size >= 0 } } ?: 0L
        var bitmap = decodeBounded(source, preset.maxDimension)
        val outputDirectory = File(context.cacheDir, "attachment-photos")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory)
        if (preset == Preset.ORIGINAL) {
            require(originalBytes in 1..maxEncodedBytes) {
                "Original photo exceeds the selected media transport limit"
            }
            val mime = context.contentResolver.getType(source) ?: "application/octet-stream"
            val suffix = when (mime) {
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                else -> ".img"
            }
            val original = File.createTempFile("photo_original_", suffix, outputDirectory)
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input)
                FileOutputStream(original).use { output -> input.copyTo(output); output.fd.sync() }
            }
            return Result(
                original, originalBytes, original.length(), bitmap.width, bitmap.height,
                mime, "original",
            ).also { bitmap.recycle() }
        }
        val output = File.createTempFile("photo_", ".webp", outputDirectory)
        val qualities = intArrayOf(78, 68, 58, 48, 38, 30, 24)
        var qualityIndex = 0
        while (true) {
            FileOutputStream(output, false).use { stream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                check(bitmap.compress(format, qualities[qualityIndex], stream))
                stream.fd.sync()
            }
            if (output.length() <= preset.targetBytes ||
                (bitmap.width <= MIN_DIMENSION && bitmap.height <= MIN_DIMENSION)) break
            if (qualityIndex < qualities.lastIndex) {
                qualityIndex++
            } else {
                val width = maxOf(MIN_DIMENSION, (bitmap.width * 0.78f).toInt())
                val height = maxOf(MIN_DIMENSION, (bitmap.height * 0.78f).toInt())
                val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
                qualityIndex = minOf(2, qualities.lastIndex)
            }
        }
        require(output.length() <= maxEncodedBytes) {
            "Compressed photo exceeds the selected media transport limit"
        }
        val result = Result(
            output, originalBytes, output.length(), bitmap.width, bitmap.height,
            "image/webp", "webp-lossy",
        )
        bitmap.recycle()
        return result
    }

    private fun decodeBounded(source: Uri, maxDimension: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val imageSource = ImageDecoder.createSource(context.contentResolver, source)
            return ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = minOf(1f, maxDimension.toFloat() / maxOf(width, height))
                decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
        val decoded = context.contentResolver.openInputStream(source).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unable to decode image")
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(decoded.width, decoded.height))
        if (scale == 1f) return decoded
        return Bitmap.createScaledBitmap(
            decoded, maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), true,
        ).also { if (it !== decoded) decoded.recycle() }
    }

    companion object { private const val MIN_DIMENSION = 96 }
}
