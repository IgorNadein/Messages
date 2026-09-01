package com.afkanerd.deku.attachments.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class VoiceRecorder(private val context: Context) {
    data class Result(
        val file: File,
        val codec: String,
        val mimeType: String,
        val sampleRate: Int,
        val durationMs: Long,
        val encodedBytes: Long,
    )

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0

    fun start() {
        check(recorder == null) { "Recording is already active" }
        val directory = File(context.cacheDir, "attachment-voice")
        check(directory.mkdirs() || directory.isDirectory)
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        output = File.createTempFile("voice_", if (opus) ".ogg" else ".amr", directory)
        @Suppress("DEPRECATION")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (opus) {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            mediaRecorder.setAudioSamplingRate(OPUS_SAMPLE_RATE)
            mediaRecorder.setAudioEncodingBitRate(OPUS_BITRATE)
        } else {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder.setAudioSamplingRate(AMR_SAMPLE_RATE)
            mediaRecorder.setAudioEncodingBitRate(AMR_BITRATE)
        }
        mediaRecorder.setOutputFile(output!!.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        startedAt = SystemClock.elapsedRealtime()
    }

    fun stop(): Result {
        val active = requireNotNull(recorder) { "Recording is not active" }
        val duration = SystemClock.elapsedRealtime() - startedAt
        try { active.stop() } finally {
            active.release()
            recorder = null
        }
        val file = requireNotNull(output)
        output = null
        check(duration >= MIN_DURATION_MILLIS && file.length() > 0) { "Voice message is too short" }
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return Result(
            file = file,
            codec = if (opus) "opus" else "amr-nb",
            mimeType = if (opus) "audio/ogg" else "audio/3gpp",
            sampleRate = if (opus) OPUS_SAMPLE_RATE else AMR_SAMPLE_RATE,
            durationMs = duration,
            encodedBytes = file.length(),
        )
    }

    fun elapsedMillis(): Long = if(recorder == null) 0L else {
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
    }

    fun maxAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }
        .getOrDefault(0)

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
        output?.delete()
        output = null
    }

    companion object {
        private const val OPUS_SAMPLE_RATE = 16_000
        private const val OPUS_BITRATE = 6_000
        private const val AMR_SAMPLE_RATE = 8_000
        private const val AMR_BITRATE = 4_750
        private const val MIN_DURATION_MILLIS = 350L
    }
}
