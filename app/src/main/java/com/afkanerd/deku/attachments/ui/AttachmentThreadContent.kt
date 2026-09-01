package com.afkanerd.deku.attachments.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AttachmentThreadContent(address: String) {
    val context = LocalContext.current
    val transfers by remember(address) {
        Datastore.getDatastore(context).attachmentTransferDao().observeForAddress(address)
    }.collectAsState(initial = emptyList())
    if (transfers.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        transfers.take(MAX_VISIBLE_TRANSFERS).reversed().forEach { transfer ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (transfer.outgoing) Arrangement.End else Arrangement.Start,
            ) { AttachmentBubble(transfer) }
        }
    }
}

@Composable
private fun AttachmentBubble(transfer: AttachmentTransferEntity) {
    val context = LocalContext.current
    val manager = remember { AttachmentManager.get(context) }
    val scope = rememberCoroutineScope()
    val status = runCatching { AttachmentTransferStatus.valueOf(transfer.status) }
        .getOrDefault(AttachmentTransferStatus.FAILED)
    val complete = status == AttachmentTransferStatus.COMPLETED
    val chunks = runCatching {
        val encoded = if (transfer.outgoing) transfer.acknowledgedBitmap else transfer.receivedBitmap
        ChunkTracker.restore(transfer.totalChunks, encoded).count()
    }.getOrDefault(0)
    val progress = if (transfer.totalChunks == 0) 0f else chunks.toFloat() / transfer.totalChunks
    Card(Modifier.padding(vertical = 3.dp).widthIn(max = 310.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(mediaIcon(transfer.mediaType), contentDescription = null)
                Text(
                    transfer.filename,
                    Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!status.terminal) {
                    IconButton(onClick = { scope.launch { manager.cancel(transfer.transferId) } }) {
                        Icon(Icons.Default.Cancel, stringResource(R.string.attachment_cancel))
                    }
                }
            }
            if (complete && transfer.mediaType == "PHOTO" && transfer.completedPath != null) {
                AsyncImage(
                    model = File(transfer.completedPath),
                    contentDescription = transfer.filename,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(
                    R.string.attachment_size_and_sms,
                    formatBytes(transfer.encodedSize),
                    TransferLimits.estimatedDataSms(transfer.encodedSize, ESTIMATED_OFFER_BYTES),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!complete && status != AttachmentTransferStatus.OFFERED) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.attachment_progress_sms, chunks, transfer.totalChunks,
                        (progress * 100).roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(statusLabel(status), style = MaterialTheme.typography.labelMedium)
            if (status == AttachmentTransferStatus.OFFERED) {
                Text(stringResource(R.string.attachment_offer_from_contact))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { manager.accept(transfer.transferId) } }) {
                        Text(stringResource(R.string.attachment_accept))
                    }
                    OutlinedButton(onClick = { scope.launch { manager.reject(transfer.transferId) } }) {
                        Text(stringResource(R.string.attachment_reject))
                    }
                }
            }
            if (complete && transfer.mediaType == "VOICE" && transfer.completedPath != null) {
                VoicePlayButton(transfer.completedPath)
            }
            transfer.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun VoicePlayButton(path: String) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release() } }
    IconButton(onClick = {
        player?.release()
        player = MediaPlayer().apply { setDataSource(path); prepare(); start() }
    }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.attachment_play_voice)) }
}

private fun mediaIcon(type: String) = when (type) {
    "PHOTO" -> Icons.Default.Photo
    "VOICE" -> Icons.Default.Mic
    else -> Icons.Default.Description
}

@Composable
private fun statusLabel(status: AttachmentTransferStatus): String = stringResource(when (status) {
    AttachmentTransferStatus.PREPARING -> R.string.attachment_status_preparing
    AttachmentTransferStatus.WAITING_ACCEPT -> R.string.attachment_status_waiting
    AttachmentTransferStatus.OFFERED -> R.string.attachment_status_offered
    AttachmentTransferStatus.SENDING -> R.string.attachment_status_sending
    AttachmentTransferStatus.RECEIVING -> R.string.attachment_status_receiving
    AttachmentTransferStatus.PAUSED -> R.string.attachment_status_paused
    AttachmentTransferStatus.RETRYING -> R.string.attachment_status_retrying
    AttachmentTransferStatus.VERIFYING -> R.string.attachment_status_verifying
    AttachmentTransferStatus.COMPLETED -> R.string.attachment_status_completed
    AttachmentTransferStatus.FAILED -> R.string.attachment_status_failed
    AttachmentTransferStatus.CANCELLED -> R.string.attachment_status_cancelled
})

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    else -> "%.1f KB".format(bytes / 1024.0)
}

private const val MAX_VISIBLE_TRANSFERS = 12
private const val ESTIMATED_OFFER_BYTES = 300
