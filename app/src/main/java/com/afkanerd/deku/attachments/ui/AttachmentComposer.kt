package com.afkanerd.deku.attachments.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.attachments.media.PhotoCompressor
import com.afkanerd.deku.attachments.media.VoiceRecorder
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.PreparedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PendingAttachment(
    val uri: Uri,
    val mediaType: AttachmentManifest.MediaType,
    val mimeType: String,
    val filename: String,
    val originalSize: Long,
    val encodedSize: Long,
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sampleRate: Int = 0,
    val durationMs: Long = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentComposer(
    show: Boolean,
    address: String,
    subscriptionId: Int,
    secureEstablished: Boolean,
    startVoiceRecording: Boolean = false,
    onDismiss: () -> Unit,
    onSendAttachment: suspend (PreparedAttachment) -> Boolean,
) {
    if (!show) return
    val context = LocalContext.current
    val compressor = remember { PhotoCompressor(context) }
    val voiceRecorder = remember { VoiceRecorder(context) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingAttachment?>(null) }
    var photoPreset by remember { mutableStateOf(PhotoCompressor.Preset.LOW) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    var amplitudes by remember { mutableStateOf(emptyList<Int>()) }
    var busy by remember { mutableStateOf(false) }
    val preparationFailedMessage = stringResource(R.string.attachment_failed_prepare)

    fun reportError(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        Toast.makeText(
            context,
            attachmentPreparationFailureMessage(preparationFailedMessage),
            Toast.LENGTH_LONG,
        ).show()
    }

    fun preparePhoto(uri: Uri) {
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { compressor.compress(uri, photoPreset) }
                pending = PendingAttachment(
                    uri = Uri.fromFile(result.file), mediaType = AttachmentManifest.MediaType.PHOTO,
                    mimeType = result.mimeType, filename = "photo.webp",
                    originalSize = result.originalBytes, encodedSize = result.encodedBytes,
                    codec = result.codec,
                    width = result.width,
                    height = result.height,
                )
            } catch (error: Exception) { reportError(error) } finally { busy = false }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val details = fileDetails(context, it)
            pending = PendingAttachment(
                it, AttachmentManifest.MediaType.FILE, details.mime, details.name,
                details.size, details.size,
            )
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::preparePhoto)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let(::preparePhoto)
    }
    fun beginRecording() {
        runCatching {
            voiceRecorder.start()
            recordingElapsed = 0L
            amplitudes = emptyList()
            recording = true
        }.onFailure(::reportError)
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording()
    }
    DisposableEffect(Unit) { onDispose { if (recording) voiceRecorder.cancel() } }
    LaunchedEffect(recording) {
        while(recording) {
            recordingElapsed = voiceRecorder.elapsedMillis()
            amplitudes = (amplitudes + voiceRecorder.maxAmplitude()).takeLast(VOICE_WAVEFORM_BARS)
            delay(VOICE_SAMPLE_INTERVAL_MILLIS)
        }
    }
    LaunchedEffect(show, startVoiceRecording) {
        if(show && startVoiceRecording && !recording && pending == null) {
            if(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                beginRecording()
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            ListItem(
                    headlineContent = { Text(stringResource(R.string.attachment_file)) },
                    leadingContent = { Icon(Icons.Default.Description, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.attachment_choose_file)) }

                Text(stringResource(R.string.attachment_photo_quality), Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhotoCompressor.Preset.entries.forEach { preset ->
                        OutlinedButton(onClick = { photoPreset = preset }, modifier = Modifier.weight(1f)) {
                            Text(preset.name.lowercase().replaceFirstChar(Char::uppercase))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { photoPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Photo, null); Text(stringResource(R.string.attachment_photo))
                    }
                    Button(onClick = {
                        val file = File.createTempFile("camera_", ".jpg", context.cacheDir)
                        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        camera.launch(cameraUri!!)
                    }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, null); Text(stringResource(R.string.attachment_camera))
                    }
                }

                if(!recording) {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            beginRecording() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Mic, null)
                        Text(stringResource(R.string.attachment_voice))
                    }
                } else {
                    VoiceRecordingPanel(
                        elapsedMillis = recordingElapsed,
                        amplitudes = amplitudes,
                        onCancel = {
                            voiceRecorder.cancel()
                            recording = false
                            recordingElapsed = 0L
                            amplitudes = emptyList()
                        },
                        onStop = {
                            runCatching { voiceRecorder.stop() }.onSuccess { result ->
                                recording = false
                                pending = PendingAttachment(
                                    Uri.fromFile(result.file), AttachmentManifest.MediaType.VOICE,
                                    result.mimeType, if (result.codec == "opus") "voice.ogg" else "voice.amr",
                                    result.encodedBytes, result.encodedBytes,
                                    codec = result.codec,
                                    sampleRate = result.sampleRate,
                                    durationMs = result.durationMs,
                                )
                            }.onFailure { recording = false; reportError(it) }
                        },
                    )
                }
            if (busy) Text(stringResource(R.string.attachment_status_preparing), Modifier.padding(16.dp))
        }
    }

    pending?.let { item ->
        AttachmentConfirmation(
            item = item,
            onDismiss = { pending = null },
            onSend = {
                busy = true
                scope.launch {
                    try {
                        val queued = onSendAttachment(
                            PreparedAttachment(
                                sourceUri = item.uri.toString(),
                                kind = when(item.mediaType) {
                                    AttachmentManifest.MediaType.PHOTO -> AttachmentKind.PHOTO
                                    AttachmentManifest.MediaType.VOICE -> AttachmentKind.VOICE
                                    AttachmentManifest.MediaType.FILE -> AttachmentKind.FILE
                                },
                                mimeType = item.mimeType,
                                fileName = item.filename,
                                originalBytes = item.originalSize,
                                encodedBytes = item.encodedSize,
                                codec = item.codec,
                                width = item.width,
                                height = item.height,
                                sampleRate = item.sampleRate,
                                durationMillis = item.durationMs,
                            )
                        )
                        if(queued) {
                            pending = null
                            onDismiss()
                        }
                    } catch (error: Exception) { reportError(error) } finally { busy = false }
                }
            },
        )
    }
}

@Composable
private fun VoiceRecordingPanel(
    elapsedMillis: Long,
    amplitudes: List<Int>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatVoiceDuration(elapsedMillis),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            val samples = if(amplitudes.isEmpty()) listOf(0) else amplitudes
            samples.forEach { amplitude ->
                val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .width(3.dp)
                        .height((6 + normalized * 34).dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.attachment_cancel))
            }
            Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, null)
                Text(stringResource(R.string.attachment_stop_recording))
            }
        }
    }
}

internal fun formatVoiceDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun AttachmentConfirmation(
    item: PendingAttachment,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val sms = runCatching { TransferLimits.estimatedDataSms(item.encodedSize, 300) }.getOrDefault(Int.MAX_VALUE)
    val overLimit = item.encodedSize !in 1..TransferLimits.MAX_TRANSFER_BYTES.toLong()
    var player by remember(item.uri) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(item.uri) { onDispose { player?.release() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attachment_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.filename)
                if (item.mediaType == AttachmentManifest.MediaType.PHOTO) {
                    AsyncImage(model = item.uri, contentDescription = item.filename, modifier = Modifier.fillMaxWidth())
                }
                Text(stringResource(R.string.attachment_size_and_sms, formatBytes(item.encodedSize), sms))
                if (sms >= TransferLimits.CONFIRM_SMS_COUNT || item.encodedSize >= TransferLimits.CONFIRM_TRANSFER_BYTES) {
                    Text(stringResource(R.string.attachment_large_warning))
                }
                if (overLimit) Text(stringResource(R.string.attachment_hard_limit, formatBytes(TransferLimits.MAX_TRANSFER_BYTES.toLong())))
                if (item.mediaType == AttachmentManifest.MediaType.VOICE) {
                    Text(formatVoiceDuration(item.durationMs))
                    IconButton(onClick = {
                        player?.release()
                        player = MediaPlayer().apply { setDataSource(requireNotNull(item.uri.path)); prepare(); start() }
                    }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.attachment_play_voice)) }
                }
            }
        },
        confirmButton = { Button(onClick = onSend, enabled = !overLimit) { Text(stringResource(R.string.attachment_send)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_cancel)) } },
    )
}

private data class FileDetails(val name: String, val size: Long, val mime: String)

private fun fileDetails(context: Context, uri: Uri): FileDetails {
    var name = "attachment.bin"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = it.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
        }
    }
    if (size < 0) size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
    require(size >= 0) { "Unable to determine file size" }
    return FileDetails(name, size, context.contentResolver.getType(uri) ?: "application/octet-stream")
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    else -> "%.1f KB".format(bytes / 1024.0)
}

private const val VOICE_WAVEFORM_BARS = 24
private const val VOICE_SAMPLE_INTERVAL_MILLIS = 100L
