package com.afkanerd.deku.attachments.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.core.content.FileProvider
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.attachments.media.PhotoCompressor
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.transport.MediaTransportPreference
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.MediaTransport
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

private fun PreparedAttachment.toPendingAttachment(): PendingAttachment = PendingAttachment(
    uri = Uri.parse(sourceUri),
    mediaType = when(kind) {
        AttachmentKind.PHOTO -> AttachmentManifest.MediaType.PHOTO
        AttachmentKind.VOICE -> AttachmentManifest.MediaType.VOICE
        AttachmentKind.FILE -> AttachmentManifest.MediaType.FILE
    },
    mimeType = mimeType,
    filename = fileName,
    originalSize = originalBytes,
    encodedSize = encodedBytes,
    codec = codec,
    width = width,
    height = height,
    sampleRate = sampleRate,
    durationMs = durationMillis,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentComposer(
    show: Boolean,
    address: String,
    subscriptionId: Int,
    secureEstablished: Boolean,
    initialAttachment: PreparedAttachment? = null,
    onDismiss: () -> Unit,
    onAttachmentSent: () -> Unit = {},
    onSendAttachment: suspend (PreparedAttachment) -> Boolean,
) {
    if (!show) return
    val context = LocalContext.current
    val mediaTransport = MediaTransportPreference.selected(context, subscriptionId.toLong())
    val compressor = remember { PhotoCompressor(context) }
    val scope = rememberCoroutineScope()
    var pending by remember(initialAttachment) {
        mutableStateOf(initialAttachment?.toPendingAttachment())
    }
    var photoPreset by remember { mutableStateOf(PhotoCompressor.Preset.LOW) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
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
                val result = withContext(Dispatchers.IO) {
                    compressor.compress(
                        source = uri,
                        preset = photoPreset,
                        maxEncodedBytes = attachmentTransportMaxBytes(mediaTransport),
                    )
                }
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
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::preparePhoto)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let(::preparePhoto)
    }
    if (shouldShowAttachmentPicker(pending != null)) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.attachment_menu),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AttachmentMenuTile(
                        icon = { Icon(Icons.Default.CameraAlt, null) },
                        label = stringResource(R.string.attachment_camera),
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val file = File.createTempFile("camera_", ".jpg", context.cacheDir)
                            cameraUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            camera.launch(cameraUri!!)
                        },
                    )
                    AttachmentMenuTile(
                        icon = { Icon(Icons.Default.Photo, null) },
                        label = stringResource(R.string.attachment_photo),
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                )
                            )
                        },
                    )
                    AttachmentMenuTile(
                        icon = { Icon(Icons.Default.Description, null) },
                        label = stringResource(R.string.attachment_file),
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                    )
                }
                Text(
                    stringResource(R.string.attachment_photo_quality),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PhotoCompressor.Preset.entries.forEach { preset ->
                        FilterChip(
                            selected = photoPreset == preset,
                            onClick = { photoPreset = preset },
                            modifier = Modifier.weight(
                                if(preset == PhotoCompressor.Preset.ORIGINAL) 1.18f else 1f,
                            ),
                            shape = RoundedCornerShape(18.dp),
                            label = {
                                Text(
                                    stringResource(
                                        when(preset) {
                                            PhotoCompressor.Preset.LOW -> R.string.attachment_quality_low
                                            PhotoCompressor.Preset.MEDIUM -> R.string.attachment_quality_medium
                                            PhotoCompressor.Preset.HIGH -> R.string.attachment_quality_high
                                            PhotoCompressor.Preset.ORIGINAL -> R.string.attachment_quality_original
                                        }
                                    ),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
                if (busy) Text(stringResource(R.string.attachment_status_preparing), Modifier.padding(16.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            }
        }
    }

    pending?.let { item ->
        AttachmentConfirmation(
            item = item,
            mediaTransport = mediaTransport,
            recipientCount = address.split(',').map(String::trim).filter(String::isNotEmpty)
                .distinct().size.coerceAtLeast(1),
            secureTransfer = secureEstablished,
            onDismiss = {
                if(initialAttachment != null) {
                    onDismiss()
                } else {
                    deleteOwnedCacheFile(context, item.uri)
                    pending = null
                }
            },
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
                            deleteOwnedCacheFile(context, item.uri)
                            pending = null
                            onAttachmentSent()
                            onDismiss()
                        }
                    } catch (error: Exception) { reportError(error) } finally { busy = false }
                }
            },
        )
    }
}

@Composable
private fun AttachmentMenuTile(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(label, maxLines = 1)
        }
    }
}

/** A confirmation dialog must replace the picker sheet instead of being composed behind it. */
internal fun shouldShowAttachmentPicker(hasPendingAttachment: Boolean): Boolean =
    !hasPendingAttachment

internal fun formatVoiceDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun AttachmentConfirmation(
    item: PendingAttachment,
    mediaTransport: MediaTransport,
    recipientCount: Int,
    secureTransfer: Boolean,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val policy = attachmentConfirmationPolicy(
        mediaTransport = mediaTransport,
        encodedBytes = item.encodedSize,
        recipientCount = recipientCount,
        secureTransfer = secureTransfer,
    )
    val overLimit = item.encodedSize !in 1..policy.maxBytes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attachment_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.filename)
                if (item.mediaType == AttachmentManifest.MediaType.PHOTO) {
                    AsyncImage(model = item.uri, contentDescription = item.filename, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    when(policy.mediaTransport) {
                        MediaTransport.DATA_SMS,
                        MediaTransport.STANDARD_SMS -> stringResource(
                            R.string.attachment_size_and_sms,
                            formatBytes(item.encodedSize),
                            policy.estimatedUnits ?: Int.MAX_VALUE,
                        )
                        MediaTransport.MMS -> stringResource(
                            R.string.attachment_size_and_mms,
                            formatBytes(item.encodedSize),
                            policy.estimatedUnits ?: 1,
                        )
                        MediaTransport.CLOUD_STORAGE -> stringResource(
                            R.string.attachment_size_and_cloud,
                            formatBytes(item.encodedSize),
                        )
                    }
                )
                if (policy.showSmsCostWarning) {
                    Text(stringResource(R.string.attachment_large_warning))
                }
                if(policy.unsupportedGroup) {
                    Text(stringResource(R.string.attachment_group_transport_unsupported))
                }
                if (overLimit) Text(
                    stringResource(R.string.attachment_hard_limit, formatBytes(policy.maxBytes))
                )
                if (item.mediaType == AttachmentManifest.MediaType.VOICE) {
                    VoiceAttachmentPreview(
                        uri = item.uri,
                        expectedDurationMillis = item.durationMs,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSend, enabled = !overLimit && !policy.unsupportedGroup) {
                Text(stringResource(
                    if(policy.showSmsCostWarning) R.string.attachment_continue_anyway
                    else R.string.attachment_send
                ))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_cancel)) } },
    )
}

@Composable
private fun VoiceAttachmentPreview(
    uri: Uri,
    expectedDurationMillis: Long,
) {
    val context = LocalContext.current
    var prepared by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var positionMillis by remember(uri) { mutableLongStateOf(0L) }
    var durationMillis by remember(uri) {
        mutableLongStateOf(expectedDurationMillis.coerceAtLeast(0L))
    }
    var playbackFailed by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { MediaPlayer() }

    DisposableEffect(player, uri) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnPreparedListener { ready ->
            prepared = true
            durationMillis = ready.duration.toLong().coerceAtLeast(durationMillis)
        }
        player.setOnCompletionListener {
            playing = false
            positionMillis = 0L
        }
        player.setOnErrorListener { _, _, _ ->
            prepared = false
            playing = false
            playbackFailed = true
            true
        }
        runCatching {
            player.setDataSource(context, uri)
            player.prepareAsync()
        }.onFailure {
            playbackFailed = true
        }
        onDispose {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
    LaunchedEffect(playing, player) {
        while(playing) {
            positionMillis = runCatching { player.currentPosition.toLong() }
                .getOrDefault(positionMillis)
            delay(VOICE_SAMPLE_INTERVAL_MILLIS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = prepared && !playbackFailed,
                onClick = {
                    if(player.isPlaying) {
                        player.pause()
                        playing = false
                        positionMillis = player.currentPosition.toLong()
                    } else {
                        if(durationMillis > 0L && positionMillis >= durationMillis) {
                            player.seekTo(0)
                            positionMillis = 0L
                        }
                        player.start()
                        playing = true
                    }
                },
            ) {
                Icon(
                    if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(R.string.attachment_play_voice),
                )
            }
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { voicePreviewProgress(positionMillis, durationMillis) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${formatVoiceDuration(positionMillis)} / ${formatVoiceDuration(durationMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if(playbackFailed) {
            Text(
                stringResource(R.string.attachment_voice_preview_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun voicePreviewProgress(positionMillis: Long, durationMillis: Long): Float =
    if(durationMillis <= 0L) 0f
    else (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)

internal data class AttachmentConfirmationPolicy(
    val mediaTransport: MediaTransport,
    val maxBytes: Long,
    val estimatedUnits: Int?,
    val unsupportedGroup: Boolean,
    val showSmsCostWarning: Boolean,
)

internal fun attachmentConfirmationPolicy(
    mediaTransport: MediaTransport,
    encodedBytes: Long,
    recipientCount: Int,
    secureTransfer: Boolean,
): AttachmentConfirmationPolicy {
    require(recipientCount > 0)
    val maxBytes = attachmentTransportMaxBytes(mediaTransport)
    val estimatedUnits = when(mediaTransport) {
        MediaTransport.DATA_SMS,
        MediaTransport.STANDARD_SMS -> runCatching {
            TransferLimits.estimatedDataSms(encodedBytes, 300)
        }.getOrDefault(Int.MAX_VALUE).let { units ->
            if(mediaTransport == MediaTransport.STANDARD_SMS) units * 2 else units
        }
        MediaTransport.MMS -> if(secureTransfer) {
            runCatching { TransferLimits.mmsPartCount(encodedBytes) }
                .getOrDefault(Int.MAX_VALUE)
        } else {
            1
        }
        MediaTransport.CLOUD_STORAGE -> null
    }
    return AttachmentConfirmationPolicy(
        mediaTransport = mediaTransport,
        maxBytes = maxBytes,
        estimatedUnits = estimatedUnits,
        unsupportedGroup = recipientCount > 1 && mediaTransport != MediaTransport.MMS,
        showSmsCostWarning = mediaTransport in setOf(
            MediaTransport.DATA_SMS,
            MediaTransport.STANDARD_SMS,
        ) &&
            ((estimatedUnits ?: 0) >= TransferLimits.CONFIRM_SMS_COUNT ||
                encodedBytes >= TransferLimits.CONFIRM_TRANSFER_BYTES),
    )
}

internal fun attachmentTransportMaxBytes(mediaTransport: MediaTransport): Long =
    when(mediaTransport) {
        MediaTransport.DATA_SMS,
        MediaTransport.STANDARD_SMS -> TransferLimits.MAX_TRANSFER_BYTES.toLong()
        MediaTransport.MMS,
        MediaTransport.CLOUD_STORAGE
        -> TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong()
    }

private data class FileDetails(val name: String, val size: Long, val mime: String)

private fun deleteOwnedCacheFile(context: Context, uri: Uri) {
    if(uri.scheme != "file") return
    val path = uri.path ?: return
    runCatching {
        val cacheRoot = context.cacheDir.canonicalFile
        val candidate = File(path).canonicalFile
        if(candidate.parentFile == cacheRoot ||
            candidate.path.startsWith(cacheRoot.path + File.separator)
        ) {
            candidate.delete()
        }
    }
}

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
