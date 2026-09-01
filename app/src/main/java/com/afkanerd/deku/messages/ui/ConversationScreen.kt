package com.afkanerd.deku.messages.ui

import android.text.format.DateFormat
import android.media.MediaPlayer
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.SecurityEventKind
import com.afkanerd.deku.messages.domain.SimSubscription
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.presentation.ConversationError
import com.afkanerd.deku.messages.presentation.ConversationViewModel
import com.afkanerd.deku.messages.presentation.IdentityVerificationResult
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiMessageComposer
import com.afkanerd.deku.messages.ui.theme.MessagesTheme
import com.afkanerd.deku.attachments.ui.AttachmentComposer
import java.util.Calendar
import java.util.Date
import java.io.File
import kotlin.math.abs
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMore: () -> Unit,
    onOpenMedia: (TimelineItem.Media) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val messages = viewModel.timeline.collectAsLazyPagingItems()
    val attachments by viewModel.attachments.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSecuritySheet by rememberSaveable { mutableStateOf(false) }
    var showSecurityQr by rememberSaveable { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable { mutableStateOf(false) }
    var startVoiceRecording by rememberSaveable { mutableStateOf(false) }

    val localizedError = state.error?.let {
        stringResource(
            when(it) {
                ConversationError.SECURITY_NOT_READY -> R.string.oneui_secure_send_blocked
                ConversationError.SECURE_REQUEST_FAILED -> R.string.oneui_secure_request_failed
                ConversationError.SEND_FAILED -> R.string.oneui_send_failed
            }
        )
    }
    val verificationMessage = state.identityVerificationResult?.let { result ->
        stringResource(
            if(result == IdentityVerificationResult.VERIFIED) {
                R.string.security_verified_success
            } else {
                R.string.security_verification_failed
            }
        )
    }
    val scanPrompt = stringResource(R.string.security_scan_prompt)
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::verifyContactIdentity)
    }
    LaunchedEffect(localizedError) {
        localizedError?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(verificationMessage) {
        verificationMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearIdentityVerificationResult()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) viewModel.refreshHeader()
            if(event == Lifecycle.Event.ON_STOP) viewModel.persistDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.header?.securityState) {
        if(state.header?.securityState == ConversationSecurityState.REQUEST_RECEIVED) {
            viewModel.loadSecurityQrPayload()
            showSecuritySheet = true
        }
    }
    LaunchedEffect(messages.itemCount, attachments.size) {
        if((messages.itemCount > 0 || attachments.isNotEmpty()) &&
            listState.firstVisibleItemIndex <= 1
        ) {
            listState.scrollToItem(0)
        }
    }
    val timelineEntries = remember(messages.itemSnapshotList.items, attachments) {
        mergeConversationTimeline(messages.itemSnapshotList.items, attachments)
    }

    val header = state.header
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            OneUiCompactBar(
                title = header?.displayName ?: header?.address.orEmpty(),
                showTitle = true,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
                actions = {
                    header?.takeUnless(ConversationHeader::isGroupConversation)?.let {
                        IconButton(onClick = { onCall(it.address) }) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = stringResource(R.string.call),
                            )
                        }
                    }
                    IconButton(onClick = onMore) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.oneui_more_options))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                header?.takeUnless(ConversationHeader::isGroupConversation)?.let {
                    SecurityInlineStatus(
                        state = it.securityState,
                        onClick = {
                            viewModel.loadSecurityQrPayload()
                            showSecuritySheet = true
                        },
                    )
                }
                OneUiMessageComposer(
                    value = state.draft,
                    enabled = true,
                    isSending = state.isSending,
                    subscriptions = header?.subscriptions.orEmpty(),
                    selectedSubscriptionId = header?.subscriptionId,
                    onValueChange = viewModel::updateDraft,
                    onSubscriptionSelected = viewModel::selectSubscription,
                    onAttachment = { showAttachmentSheet = true },
                    onVoice = {
                        startVoiceRecording = true
                        showAttachmentSheet = true
                    },
                    onSend = viewModel::send,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    horizontal = MessagesTheme.spacing.sm,
                    vertical = MessagesTheme.spacing.md,
                ),
            ) {
                itemsIndexed(
                    items = timelineEntries,
                    key = { _, entry -> entry.stableId },
                ) { entryIndex, entry ->
                    val newer = timelineEntries.getOrNull(entryIndex - 1)
                    val older = timelineEntries.getOrNull(entryIndex + 1)
                    when(entry) {
                        is ConversationTimelineEntry.Transfer -> AttachmentTransferBubble(
                            transfer = entry.transfer,
                            onAction = { action ->
                                viewModel.performAttachmentAction(
                                    entry.transfer.stableId.removePrefix("attachment-"),
                                    action,
                                )
                            },
                        )
                        is ConversationTimelineEntry.Message -> {
                            // Preserve Paging prefetch while displaying transfers in timestamp order.
                            val item = messages[entry.pagingIndex] ?: entry.item
                            val newerMessage = (newer as? ConversationTimelineEntry.Message)?.item
                            val olderMessage = (older as? ConversationTimelineEntry.Message)?.item
                            val connectedNewer = item.canGroupWith(newerMessage)
                            val connectedOlder = item.canGroupWith(olderMessage)
                            TimelineRow(
                                item = item,
                                connectedNewer = connectedNewer,
                                connectedOlder = connectedOlder,
                                showSender = header?.isGroupConversation == true &&
                                    !connectedOlder,
                                showMetadata = !connectedNewer,
                                onOpenMedia = onOpenMedia,
                                onSecurityAction = {
                                    viewModel.requestOrRepairSecureSession(forceRenewal = true)
                                },
                            )
                        }
                    }
                    if(entry.timestampMillis.isDifferentDayFrom(older?.timestampMillis)) {
                        DayMarker(entry.timestampMillis)
                    }
                }
                if(messages.loadState.append is LoadState.Loading) {
                    item(key = "timeline-append-loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(MessagesTheme.spacing.md),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
            }
            if(messages.loadState.refresh is LoadState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if(showSecuritySheet && header != null) {
        ModalBottomSheet(onDismissRequest = { showSecuritySheet = false }) {
            SecuritySheet(
                state = header.securityState,
                contactName = header.displayName,
                fingerprint = state.securityFingerprint,
                busy = state.isRequestingSecureSession,
                onAction = { forceRenewal ->
                    viewModel.requestOrRepairSecureSession(forceRenewal)
                },
                onAcceptChangedIdentity = viewModel::acceptChangedIdentityAndRepair,
                onShowQr = { showSecurityQr = true },
                onScanQr = {
                    qrScanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt(scanPrompt)
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
            )
        }
    }
    val securityQrPayload = state.securityQrPayload
    if(showSecurityQr && securityQrPayload != null) {
        SecurityQrDialog(
            payload = securityQrPayload,
            onDismiss = { showSecurityQr = false },
        )
    }
    if(showAttachmentSheet && header != null) {
        AttachmentComposer(
            show = true,
            address = header.address,
            subscriptionId = header.subscriptionId.toInt(),
            secureEstablished = header.securityState == ConversationSecurityState.SECURE_UNVERIFIED ||
                header.securityState == ConversationSecurityState.SECURE_VERIFIED,
            startVoiceRecording = startVoiceRecording,
            onDismiss = {
                startVoiceRecording = false
                showAttachmentSheet = false
            },
            onSendAttachment = viewModel::prepareAttachment,
        )
    }
}

internal sealed interface ConversationTimelineEntry {
    val stableId: String
    val timestampMillis: Long

    data class Message(
        val pagingIndex: Int,
        val item: TimelineItem,
    ) : ConversationTimelineEntry {
        override val stableId: String = "message-${item.stableId}"
        override val timestampMillis: Long = item.timestampMillis
    }

    data class Transfer(
        val transfer: AttachmentTransfer,
    ) : ConversationTimelineEntry {
        override val stableId: String = "transfer-${transfer.stableId}"
        override val timestampMillis: Long = transfer.timestampMillis
    }
}

internal fun mergeConversationTimeline(
    messages: List<TimelineItem>,
    attachments: List<AttachmentTransfer>,
): List<ConversationTimelineEntry> = buildList {
    messages.forEachIndexed { index, item ->
        add(ConversationTimelineEntry.Message(index, item))
    }
    attachments.forEach { transfer ->
        add(ConversationTimelineEntry.Transfer(transfer))
    }
}.sortedWith(
    compareByDescending<ConversationTimelineEntry> { it.timestampMillis }
        .thenBy { it.stableId }
)

@Composable
private fun AttachmentTransferBubble(
    transfer: AttachmentTransfer,
    onAction: (AttachmentAction) -> Unit,
) {
    val outgoing = transfer.direction == MessageDirection.OUTGOING
    val terminal = transfer.state == AttachmentTransferState.COMPLETED ||
        transfer.state == AttachmentTransferState.FAILED ||
        transfer.state == AttachmentTransferState.CANCELLED
    val progress = if(transfer.totalSms <= 0) 0f else {
        transfer.completedSms.toFloat() / transfer.totalSms
    }
    val transferState = attachmentStateLabel(transfer.state)
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = transfer.direction,
        content = listOf(
            transfer.fileName,
            formatTransferBytes(transfer.encodedBytes),
            transferState,
        ),
        timestampMillis = transfer.timestampMillis,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(MessagesTheme.dimensions.bubbleRadius),
            color = if(outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(MessagesTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
            ) {
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = accessibilityLabel
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
                ) {
                    Icon(
                        imageVector = when(transfer.kind) {
                            AttachmentKind.PHOTO -> Icons.Default.Photo
                            AttachmentKind.VOICE -> Icons.Default.Mic
                            AttachmentKind.FILE -> Icons.Default.Description
                        },
                        contentDescription = null,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            transfer.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatTransferBytes(transfer.encodedBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if(transfer.state == AttachmentTransferState.COMPLETED &&
                    transfer.kind == AttachmentKind.PHOTO &&
                    transfer.completedPath != null
                ) {
                    AsyncImage(
                        model = File(transfer.completedPath),
                        contentDescription = transfer.fileName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if(!terminal && transfer.state != AttachmentTransferState.OFFERED) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.attachment_progress_sms,
                            transfer.completedSms,
                            transfer.totalSms,
                            (progress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    transferState,
                    style = MaterialTheme.typography.labelMedium,
                    color = if(transfer.state == AttachmentTransferState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if(transfer.state == AttachmentTransferState.OFFERED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs)) {
                        Button(onClick = { onAction(AttachmentAction.ACCEPT) }) {
                            Text(stringResource(R.string.attachment_accept))
                        }
                        OutlinedButton(onClick = { onAction(AttachmentAction.REJECT) }) {
                            Text(stringResource(R.string.attachment_reject))
                        }
                    }
                } else if(!terminal) {
                    OutlinedButton(onClick = { onAction(AttachmentAction.CANCEL) }) {
                        Text(stringResource(R.string.attachment_cancel))
                    }
                }
                if(transfer.state == AttachmentTransferState.COMPLETED &&
                    transfer.kind == AttachmentKind.VOICE &&
                    transfer.completedPath != null
                ) {
                    AttachmentVoicePlayer(
                        path = transfer.completedPath,
                        expectedDurationMillis = transfer.durationMillis,
                    )
                }
                if(transfer.hasError && transfer.state != AttachmentTransferState.FAILED) {
                    Text(
                        stringResource(R.string.attachment_status_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun AttachmentVoicePlayer(
    path: String,
    expectedDurationMillis: Long,
) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(path) { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(false) }
    var positionMillis by remember(path) { mutableLongStateOf(0L) }
    var durationMillis by remember(path) {
        mutableLongStateOf(expectedDurationMillis.coerceAtLeast(0L))
    }
    DisposableEffect(path) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing, player) {
        while(playing) {
            positionMillis = runCatching { player?.currentPosition?.toLong() ?: 0L }
                .getOrDefault(0L)
            kotlinx.coroutines.delay(VOICE_PLAYBACK_SAMPLE_MILLIS)
        }
    }
    val progress = voicePlaybackProgress(positionMillis, durationMillis)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
    ) {
        IconButton(onClick = {
            val active = player
            when {
                active == null -> {
                    val created = MediaPlayer().apply {
                        setDataSource(path)
                        setOnPreparedListener { ready ->
                            prepared = true
                            durationMillis = ready.duration.toLong().coerceAtLeast(durationMillis)
                            ready.start()
                            playing = true
                        }
                        setOnCompletionListener {
                            playing = false
                            positionMillis = 0L
                        }
                        prepareAsync()
                    }
                    player = created
                }
                !prepared -> Unit
                active.isPlaying -> {
                    active.pause()
                    playing = false
                    positionMillis = active.currentPosition.toLong()
                }
                else -> {
                    active.start()
                    playing = true
                }
            }
        }) {
            Icon(
                if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(R.string.attachment_play_voice),
            )
        }
        Column(Modifier.weight(1f)) {
            VoicePlaybackWaveform(progress)
            Text(
                "${formatVoicePlaybackDuration(positionMillis)} / " +
                    formatVoicePlaybackDuration(durationMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VoicePlaybackWaveform(progress: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(VOICE_PLAYBACK_BARS) { index ->
            val barProgress = (index + 1f) / VOICE_PLAYBACK_BARS
            val height = VOICE_BAR_HEIGHTS[index % VOICE_BAR_HEIGHTS.size]
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .background(
                        if(barProgress <= progress) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
    }
}

internal fun voicePlaybackProgress(positionMillis: Long, durationMillis: Long): Float =
    if(durationMillis <= 0L) 0f else {
        (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    }

internal fun formatVoicePlaybackDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun attachmentStateLabel(state: AttachmentTransferState): String = stringResource(
    when(state) {
        AttachmentTransferState.PREPARING -> R.string.attachment_status_preparing
        AttachmentTransferState.WAITING -> R.string.attachment_status_waiting
        AttachmentTransferState.OFFERED -> R.string.attachment_status_offered
        AttachmentTransferState.SENDING -> R.string.attachment_status_sending
        AttachmentTransferState.RECEIVING -> R.string.attachment_status_receiving
        AttachmentTransferState.PAUSED -> R.string.attachment_status_paused
        AttachmentTransferState.RETRYING -> R.string.attachment_status_retrying
        AttachmentTransferState.VERIFYING -> R.string.attachment_status_verifying
        AttachmentTransferState.COMPLETED -> R.string.attachment_status_completed
        AttachmentTransferState.FAILED -> R.string.attachment_status_failed
        AttachmentTransferState.CANCELLED -> R.string.attachment_status_cancelled
    }
)

private fun formatTransferBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    else -> "%.1f KB".format(bytes / 1024.0)
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    connectedNewer: Boolean,
    connectedOlder: Boolean,
    showSender: Boolean,
    showMetadata: Boolean,
    onOpenMedia: (TimelineItem.Media) -> Unit,
    onSecurityAction: () -> Unit,
) {
    when(item) {
        is TimelineItem.Text -> TextBubble(
            item,
            connectedNewer,
            connectedOlder,
            showSender,
            showMetadata,
        )
        is TimelineItem.Media -> MediaBubble(item, showSender, showMetadata, onOpenMedia)
        is TimelineItem.SecurityEvent -> SecurityEventCard(item, onSecurityAction)
    }
}

@Composable
private fun TextBubble(
    item: TimelineItem.Text,
    connectedNewer: Boolean,
    connectedOlder: Boolean,
    showSender: Boolean,
    showMetadata: Boolean,
) {
    val outgoing = item.direction == MessageDirection.OUTGOING
    val shape = bubbleShape(outgoing, connectedNewer, connectedOlder)
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = item.direction,
        content = listOf(item.text),
        timestampMillis = item.timestampMillis,
        deliveryState = item.deliveryState,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        if(showSender && !outgoing) SenderLabel(item.author)
        Surface(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                },
            shape = shape,
            color = if(outgoing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if(outgoing) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        AnimatedVisibility(showMetadata) {
            MessageMetadata(item.timestampMillis, item.deliveryState, outgoing)
        }
        Spacer(Modifier.height(if(connectedNewer) 3.dp else 10.dp))
    }
}

@Composable
private fun MediaBubble(
    item: TimelineItem.Media,
    showSender: Boolean,
    showMetadata: Boolean,
    onOpenMedia: (TimelineItem.Media) -> Unit,
) {
    val outgoing = item.direction == MessageDirection.OUTGOING
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = item.direction,
        content = listOfNotNull(
            item.fileName ?: stringResource(R.string.oneui_attachment),
            item.caption?.takeIf(String::isNotBlank),
        ),
        timestampMillis = item.timestampMillis,
        deliveryState = item.deliveryState,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        if(showSender && !outgoing) SenderLabel(item.author)
        Surface(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                }
                .then(
                    if(item.uri.isNullOrBlank()) Modifier
                    else Modifier.clickable { onOpenMedia(item) }
                ),
            shape = RoundedCornerShape(MessagesTheme.dimensions.bubbleRadius),
            color = if(outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(MessagesTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Column {
                    Text(
                        text = item.fileName ?: stringResource(R.string.oneui_attachment),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.caption?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if(showMetadata) MessageMetadata(item.timestampMillis, item.deliveryState, outgoing)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SenderLabel(author: MessageAuthor?) {
    author ?: return
    Text(
        text = author.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
    )
}

@Composable
private fun SecurityEventCard(item: TimelineItem.SecurityEvent, onAction: () -> Unit) {
    val title = stringResource(when(item.kind) {
        SecurityEventKind.REQUEST_SENT -> R.string.oneui_security_event_request_sent
        SecurityEventKind.REQUEST_RECEIVED -> R.string.oneui_security_event_request_received
        SecurityEventKind.SESSION_ESTABLISHED -> R.string.oneui_security_event_established
        SecurityEventKind.DECRYPTION_FAILED -> R.string.oneui_security_event_decrypt_failed
    })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MessagesTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
            color = if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                MaterialTheme.colorScheme.errorContainer
            } else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(MessagesTheme.spacing.md)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
                ) {
                    Icon(
                        imageVector = if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                            Icons.Default.ErrorOutline
                        } else Icons.Default.Shield,
                        contentDescription = null,
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                    Spacer(Modifier.height(MessagesTheme.spacing.xs))
                    Text(
                        stringResource(R.string.oneui_security_event_decrypt_help),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(MessagesTheme.spacing.sm))
                    Button(onClick = onAction) {
                        Text(stringResource(R.string.oneui_request_new_key))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageMetadata(timestampMillis: Long, state: DeliveryState, outgoing: Boolean) {
    val context = LocalContext.current
    val time = remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
    }
    val status = if(outgoing) deliveryStateLabel(state) else null
    Text(
        text = listOfNotNull(time, status).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = if(state == DeliveryState.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = MessagesTheme.spacing.xs, vertical = 2.dp),
    )
}

@Composable
private fun timelineAccessibilityDescription(
    direction: MessageDirection,
    content: List<String>,
    timestampMillis: Long,
    deliveryState: DeliveryState? = null,
): String {
    val context = LocalContext.current
    val time = remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
    }
    val state = deliveryState?.let { deliveryStateLabel(it) }
    return buildList {
        add(
            if(direction == MessageDirection.OUTGOING) {
                stringResource(R.string.messages_thread_you)
            } else {
                state ?: stringResource(R.string.oneui_received)
            }
        )
        addAll(content)
        add(time)
        if(direction == MessageDirection.OUTGOING && state != null) add(state)
    }.joinToString(" ")
}

@Composable
private fun deliveryStateLabel(state: DeliveryState): String = stringResource(when(state) {
    DeliveryState.QUEUED -> R.string.oneui_queued
    DeliveryState.SENT -> R.string.oneui_sent
    DeliveryState.DELIVERED -> R.string.oneui_delivered
    DeliveryState.FAILED -> R.string.oneui_failed
    DeliveryState.RECEIVED -> R.string.oneui_received
})

@Composable
private fun DayMarker(timestampMillis: Long) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = remember(timestampMillis) {
                DateFormat.getMediumDateFormat(context).format(Date(timestampMillis))
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = MessagesTheme.spacing.md),
        )
    }
}

@Composable
private fun SecurityInlineStatus(
    state: ConversationSecurityState,
    onClick: () -> Unit,
) {
    if(state == ConversationSecurityState.PLAIN) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.sm)
            .clip(RoundedCornerShape(MessagesTheme.dimensions.groupRadius))
            .clickable(onClick = onClick),
        color = when(state) {
            ConversationSecurityState.KEY_CHANGED,
            ConversationSecurityState.RECOVERY_REQUIRED -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MessagesTheme.spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(securityLabel(state), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun SecuritySheet(
    state: ConversationSecurityState,
    contactName: String,
    fingerprint: String?,
    busy: Boolean,
    onAction: (Boolean) -> Unit,
    onAcceptChangedIdentity: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
) {
    val primaryAction = securitySheetPrimaryAction(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.xl)
            .padding(bottom = 40.dp),
    ) {
        Icon(
            imageVector = if(state == ConversationSecurityState.SECURE_VERIFIED) {
                Icons.Default.VerifiedUser
            } else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(MessagesTheme.spacing.md))
        Text(securityLabel(state), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(MessagesTheme.spacing.xs))
        Text(
            text = when(state) {
                ConversationSecurityState.NEGOTIATING -> stringResource(R.string.oneui_secure_waiting)
                ConversationSecurityState.REQUEST_RECEIVED -> stringResource(
                    R.string.has_requested_to_secure_this_conversation,
                    contactName,
                )
                ConversationSecurityState.KEY_CHANGED,
                ConversationSecurityState.RECOVERY_REQUIRED -> stringResource(R.string.oneui_security_event_decrypt_help)
                else -> stringResource(R.string.oneui_default_privacy)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        fingerprint?.let {
            Spacer(Modifier.height(MessagesTheme.spacing.lg))
            Text(
                stringResource(R.string.oneui_security_fingerprint),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.xs))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if(state == ConversationSecurityState.SECURE_UNVERIFIED ||
                state == ConversationSecurityState.SECURE_VERIFIED
            ) {
                Spacer(Modifier.height(MessagesTheme.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs)) {
                    OutlinedButton(onClick = onShowQr, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.security_show_qr))
                    }
                    Button(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.security_scan_qr))
                    }
                }
            }
        }
        if(primaryAction != SecuritySheetPrimaryAction.NONE) {
            Spacer(Modifier.height(MessagesTheme.spacing.xl))
            Button(
                onClick = {
                    when(primaryAction) {
                        SecuritySheetPrimaryAction.START,
                        SecuritySheetPrimaryAction.ACCEPT_REQUEST -> onAction(false)
                        SecuritySheetPrimaryAction.REPAIR -> onAction(true)
                        SecuritySheetPrimaryAction.ACCEPT_CHANGED_IDENTITY ->
                            onAcceptChangedIdentity()
                        SecuritySheetPrimaryAction.NONE -> Unit
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if(busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(
                    when(primaryAction) {
                        SecuritySheetPrimaryAction.START -> stringResource(R.string.oneui_start_secure)
                        SecuritySheetPrimaryAction.ACCEPT_REQUEST -> stringResource(
                            R.string.conversations_secure_conversation_request_agree
                        )
                        SecuritySheetPrimaryAction.ACCEPT_CHANGED_IDENTITY ->
                            stringResource(R.string.oneui_accept_new_key)
                        SecuritySheetPrimaryAction.REPAIR ->
                            stringResource(R.string.oneui_request_new_key)
                        SecuritySheetPrimaryAction.NONE -> ""
                    }
                )
            }
        }
    }
}

internal enum class SecuritySheetPrimaryAction {
    NONE,
    START,
    ACCEPT_REQUEST,
    ACCEPT_CHANGED_IDENTITY,
    REPAIR,
}

internal fun securitySheetPrimaryAction(
    state: ConversationSecurityState,
): SecuritySheetPrimaryAction = when(state) {
    ConversationSecurityState.PLAIN -> SecuritySheetPrimaryAction.START
    ConversationSecurityState.REQUEST_RECEIVED -> SecuritySheetPrimaryAction.ACCEPT_REQUEST
    ConversationSecurityState.KEY_CHANGED -> SecuritySheetPrimaryAction.ACCEPT_CHANGED_IDENTITY
    ConversationSecurityState.RECOVERY_REQUIRED -> SecuritySheetPrimaryAction.REPAIR
    ConversationSecurityState.NEGOTIATING,
    ConversationSecurityState.SECURE_UNVERIFIED,
    ConversationSecurityState.SECURE_VERIFIED -> SecuritySheetPrimaryAction.NONE
}

@Composable
private fun SecurityQrDialog(payload: String, onDismiss: () -> Unit) {
    var bitmap by remember(payload) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(payload) {
        bitmap = withContext(Dispatchers.Default) { createSecurityQrBitmap(payload) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.security_your_qr)) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.security_your_qr),
                        modifier = Modifier.size(280.dp),
                    )
                } ?: CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private fun createSecurityQrBitmap(value: String, size: Int = 768): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for(y in 0 until size) {
        for(x in 0 until size) {
            pixels[y * size + x] = if(matrix[x, y]) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
private fun securityLabel(state: ConversationSecurityState): String = stringResource(when(state) {
    ConversationSecurityState.PLAIN -> R.string.oneui_secure_plain
    ConversationSecurityState.NEGOTIATING -> R.string.oneui_secure_negotiating
    ConversationSecurityState.REQUEST_RECEIVED -> R.string.oneui_security_event_request_received
    ConversationSecurityState.SECURE_UNVERIFIED -> R.string.oneui_secure_unverified
    ConversationSecurityState.SECURE_VERIFIED -> R.string.oneui_secure_verified
    ConversationSecurityState.KEY_CHANGED -> R.string.oneui_secure_key_changed
    ConversationSecurityState.RECOVERY_REQUIRED -> R.string.oneui_secure_recovery
})

internal fun TimelineItem.canGroupWith(other: TimelineItem?): Boolean {
    if(this !is TimelineItem.Text || other !is TimelineItem.Text) return false
    return direction == other.direction &&
        isSecure == other.isSecure &&
        author?.address == other.author?.address &&
        abs(timestampMillis - other.timestampMillis) <= 2 * 60 * 1000L
}

private fun Long.isDifferentDayFrom(other: Long?): Boolean {
    if(other == null) return true
    val first = Calendar.getInstance().apply { timeInMillis = this@isDifferentDayFrom }
    val second = Calendar.getInstance().apply { timeInMillis = other }
    return first.get(Calendar.ERA) != second.get(Calendar.ERA) ||
        first.get(Calendar.YEAR) != second.get(Calendar.YEAR) ||
        first.get(Calendar.DAY_OF_YEAR) != second.get(Calendar.DAY_OF_YEAR)
}

private fun bubbleShape(
    outgoing: Boolean,
    connectedNewer: Boolean,
    connectedOlder: Boolean,
): RoundedCornerShape {
    val outer = 19.dp
    val joined = 6.dp
    return if(outgoing) RoundedCornerShape(
        topStart = outer,
        topEnd = if(connectedOlder) joined else outer,
        bottomStart = outer,
        bottomEnd = if(connectedNewer) joined else joined,
    ) else RoundedCornerShape(
        topStart = if(connectedOlder) joined else outer,
        topEnd = outer,
        bottomStart = if(connectedNewer) joined else joined,
        bottomEnd = outer,
    )
}

private const val VOICE_PLAYBACK_SAMPLE_MILLIS = 100L
private const val VOICE_PLAYBACK_BARS = 24
private val VOICE_BAR_HEIGHTS = intArrayOf(8, 14, 22, 12, 26, 18, 10, 24)
