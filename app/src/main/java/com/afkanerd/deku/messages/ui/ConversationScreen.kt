package com.afkanerd.deku.messages.ui

import android.text.format.DateFormat
import android.media.MediaPlayer
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import com.afkanerd.deku.messages.domain.MediaTransport
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.SecurityEventKind
import com.afkanerd.deku.messages.domain.SimSubscription
import com.afkanerd.deku.messages.domain.SecureChannel
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.presentation.ConversationError
import com.afkanerd.deku.messages.presentation.ConversationViewModel
import com.afkanerd.deku.messages.presentation.IdentityVerificationResult
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.OneUiConversationCompactHeader
import com.afkanerd.deku.messages.ui.components.OneUiConversationExpandedHeader
import com.afkanerd.deku.messages.ui.components.OneUiMessageComposer
import com.afkanerd.deku.messages.ui.components.ONE_UI_POPUP_MENU_ALPHA
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onVideoCall: (String) -> Unit = {},
    onMore: () -> Unit,
    onAddRecipients: (List<String>) -> Unit = {},
    onNumberSelected: (String) -> Unit = {},
    onCopyMessage: (String) -> Unit = {},
    onForwardMessage: (String) -> Unit = {},
    onShareMessage: (TimelineItem) -> Unit = {},
    onOpenMedia: (TimelineItem.Media) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val messages = viewModel.timeline.collectAsLazyPagingItems()
    val attachments by viewModel.attachments.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSecuritySheet by rememberSaveable { mutableStateOf(false) }
    var showSecurityQr by rememberSaveable { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable { mutableStateOf(false) }
    var voiceDraft by remember { mutableStateOf<PreparedAttachment?>(null) }
    var showHeaderDetails by rememberSaveable { mutableStateOf(false) }
    var showReplyUnavailableInfo by rememberSaveable { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageActionSelection?>(null) }
    var selectedTransfer by remember { mutableStateOf<TransferActionSelection?>(null) }
    var messageMenuMode by remember { mutableStateOf(MessageMenuMode.ACTIONS) }
    var favoriteOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    BackHandler(enabled = showHeaderDetails) { showHeaderDetails = false }

    val localizedError = state.error?.let {
        stringResource(
            when(it) {
                ConversationError.SECURITY_NOT_READY -> R.string.oneui_secure_send_blocked
                ConversationError.SECURE_REQUEST_FAILED -> R.string.oneui_secure_request_failed
                ConversationError.SEND_FAILED -> R.string.oneui_send_failed
                ConversationError.MESSAGE_ACTION_FAILED -> R.string.oneui_message_action_failed
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
    val newestTimelineItemId = messages.itemSnapshotList.items.firstOrNull()?.stableId
    LaunchedEffect(newestTimelineItemId, attachments.size) {
        if(newestTimelineItemId != null) viewModel.markConversationRead()
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
    val density = LocalDensity.current
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                OneUiConversationCompactHeader(
                    header = header,
                    onBack = onBack,
                    onExpand = { showHeaderDetails = true },
                    onMore = onMore,
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    start = MessagesTheme.spacing.sm,
                    top = MessagesTheme.spacing.md,
                    end = MessagesTheme.spacing.sm,
                    bottom = bottomOverlayHeight + MessagesTheme.spacing.md,
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
                            onOpen = attachmentViewerItem(entry.transfer) { path ->
                                val file = File(path)
                                if(!file.isFile) return@attachmentViewerItem null
                                runCatching {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    ).toString()
                                }.getOrNull()
                            }?.let { media -> { onOpenMedia(media) } },
                            onLongClick = { anchorBounds ->
                                selectedTransfer = TransferActionSelection(
                                    entry.transfer,
                                    anchorBounds,
                                )
                            },
                        )
                        is ConversationTimelineEntry.Message -> {
                            // Preserve Paging prefetch while displaying transfers in timestamp order.
                            // Paging can publish a shorter generation while Compose is still
                            // rendering entries remembered from the previous snapshot. Never let
                            // that brief generation mismatch crash the conversation screen.
                            val persistedItem = resolvePagedTimelineItem(
                                entry = entry,
                                currentItemCount = messages.itemCount,
                                itemAt = { index -> messages[index] },
                            )
                            val item = favoriteOverrides[persistedItem.stableId]?.let {
                                persistedItem.withFavorite(it)
                            } ?: persistedItem
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
                                showSecurityLabel = true,
                                onOpenMedia = onOpenMedia,
                                onLongClick = { item, anchorBounds ->
                                    selectedMessage = MessageActionSelection(item, anchorBounds)
                                    messageMenuMode = MessageMenuMode.ACTIONS
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    bottomOverlayHeight = with(density) { size.height.toDp() }
                }
                .imePadding()
                .testTag("oneui-conversation-bottom-overlay"),
        ) {
            header?.takeUnless {
                it.isGroupConversation || !it.canReply
            }?.let {
                SecurityInlineStatus(
                    state = it.securityState,
                    onClick = {
                        viewModel.loadSecurityQrPayload()
                        showSecuritySheet = true
                    },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .testTag("oneui-conversation-composer-background"),
            ) {
                if(header?.canReply == false) {
                    ReplyUnavailableNotice(
                        onLearnMore = { showReplyUnavailableInfo = true },
                    )
                } else {
                    OneUiMessageComposer(
                        value = state.draft,
                        enabled = header != null,
                        isSending = state.isSending,
                        subscriptions = header?.subscriptions.orEmpty(),
                        selectedSubscriptionId = header?.subscriptionId,
                        onValueChange = viewModel::updateDraft,
                        onSubscriptionSelected = viewModel::selectSubscription,
                        onAttachment = { showAttachmentSheet = true },
                        voiceDraft = voiceDraft,
                        onVoiceDraftChanged = { voiceDraft = it },
                        onVoiceSubmit = { showAttachmentSheet = true },
                        onSend = viewModel::send,
                        messageEncrypted = header?.let {
                            !it.isGroupConversation &&
                                it.secureSendingEnabled &&
                                (it.securityState == ConversationSecurityState.SECURE_UNVERIFIED ||
                                    it.securityState == ConversationSecurityState.SECURE_VERIFIED)
                        },
                        onSecurityClick = if(header?.isGroupConversation == true) null else ({
                            viewModel.loadSecurityQrPayload()
                            showSecuritySheet = true
                        }),
                    )
                }
            }
        }
        OneUiConversationExpandedHeader(
            visible = showHeaderDetails,
            header = header,
            onCollapse = { showHeaderDetails = false },
            onCall = onCall,
            onVideoCall = onVideoCall,
            onDetails = onMore,
            onAddRecipients = onAddRecipients,
            onNumberSelected = { selectedAddress ->
                viewModel.selectContactNumber(selectedAddress)
                onNumberSelected(selectedAddress)
            },
        )
    }

    if(showSecuritySheet && header != null) {
        ModalBottomSheet(onDismissRequest = { showSecuritySheet = false }) {
            SecuritySheet(
                state = header.securityState,
                contactName = header.displayName,
                fingerprint = state.securityFingerprint,
                secureSendingEnabled = header.secureSendingEnabled,
                channels = header.securityChannels,
                selectedAddress = header.address,
                selectedSubscriptionId = header.subscriptionId,
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
                onSecureSendingChange = viewModel::setSecureSendingEnabled,
            )
        }
    }
    if(state.showSecuritySendWarning && header != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSecuritySendWarning,
            title = { Text(stringResource(R.string.oneui_secure_send_warning_title)) },
            text = { Text(stringResource(R.string.oneui_secure_send_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSecuritySendWarning()
                    viewModel.requestOrRepairSecureSession(
                        forceRenewal = header.securityState ==
                            ConversationSecurityState.RECOVERY_REQUIRED,
                    )
                    showSecuritySheet = true
                }) {
                    Text(stringResource(R.string.oneui_establish_secure_channel))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::sendWithoutEncryption) {
                    Text(stringResource(R.string.oneui_send_once_unencrypted))
                }
            },
        )
    }
    if(showReplyUnavailableInfo) {
        AlertDialog(
            onDismissRequest = { showReplyUnavailableInfo = false },
            text = { Text(stringResource(R.string.conversation_shortcode_learn_more_text)) },
            confirmButton = {
                TextButton(onClick = { showReplyUnavailableInfo = false }) {
                    Text(stringResource(R.string.conversation_shortcode_learn_more_ok))
                }
            },
        )
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
            secureEstablished = (
                header.securityState == ConversationSecurityState.SECURE_UNVERIFIED ||
                    header.securityState == ConversationSecurityState.SECURE_VERIFIED
                ) && header.secureSendingEnabled,
            initialAttachment = voiceDraft,
            onDismiss = {
                showAttachmentSheet = false
            },
            onAttachmentSent = { voiceDraft = null },
            onSendAttachment = viewModel::prepareAttachment,
        )
    }
    selectedMessage?.let { selection ->
        MessageActionsPopup(
            selection = selection,
            mode = messageMenuMode,
            subscriptions = header?.subscriptions.orEmpty(),
            selectedSubscriptionId = header?.subscriptionId,
            onDismiss = { selectedMessage = null },
            onModeChange = { messageMenuMode = it },
            onCopy = { text ->
                onCopyMessage(text)
                selectedMessage = null
            },
            onForward = { text ->
                onForwardMessage(text)
                selectedMessage = null
            },
            onShare = {
                onShareMessage(selection.item)
                selectedMessage = null
            },
            onFavorite = { favorite ->
                favoriteOverrides = favoriteOverrides +
                    (selection.item.stableId to favorite)
                viewModel.setMessageFavorite(selection.item, favorite)
                selectedMessage = null
            },
            onDelete = {
                viewModel.deleteMessage(selection.item)
                selectedMessage = null
            },
            onRetry = { subscriptionId, forcePlainText ->
                viewModel.resendMessage(selection.item, subscriptionId, forcePlainText)
                selectedMessage = null
            },
        )
    }
    selectedTransfer?.let { selection ->
        AttachmentTransferActionsPopup(
            selection = selection,
            onDismiss = { selectedTransfer = null },
            onAction = { action ->
                viewModel.performAttachmentAction(
                    selection.transfer.stableId.removePrefix("attachment-"),
                    action,
                )
                selectedTransfer = null
            },
        )
    }
}

@Composable
private fun ReplyUnavailableNotice(onLearnMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.lg, vertical = MessagesTheme.spacing.sm)
            .testTag("oneui-reply-unavailable"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.conversation_shortcode_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onLearnMore) {
            Text(stringResource(R.string.conversation_shortcode_action_button))
        }
    }
}

private data class MessageActionSelection(
    val item: TimelineItem,
    val anchorBounds: IntRect,
)

private data class TransferActionSelection(
    val transfer: AttachmentTransfer,
    val anchorBounds: IntRect,
)

@Composable
private fun AttachmentTransferActionsPopup(
    selection: TransferActionSelection,
    onDismiss: () -> Unit,
    onAction: (AttachmentAction) -> Unit,
) {
    val transfer = selection.transfer
    val density = LocalDensity.current
    val positionProvider = remember(selection.anchorBounds, density) {
        MessagePopupPositionProvider(
            selectedBounds = selection.anchorBounds,
            marginPx = with(density) { 12.dp.roundToPx() },
            overlapPx = with(density) { 10.dp.roundToPx() },
        )
    }
    val terminal = transfer.state == AttachmentTransferState.COMPLETED ||
        transfer.state == AttachmentTransferState.FAILED ||
        transfer.state == AttachmentTransferState.CANCELLED
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(280.dp).testTag("oneui-attachment-actions"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = ONE_UI_POPUP_MENU_ALPHA,
            ),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                if(transfer.hasError) {
                    Text(
                        text = attachmentErrorDetailsLabel(transfer.errorMessage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    DashedMessageMenuDivider()
                }
                when {
                    transfer.state == AttachmentTransferState.OFFERED -> {
                        MessagePopupTextAction(
                            label = stringResource(R.string.attachment_accept),
                            testTag = "oneui-attachment-accept",
                            onClick = { onAction(AttachmentAction.ACCEPT) },
                        )
                        MessagePopupTextAction(
                            label = stringResource(R.string.attachment_reject),
                            testTag = "oneui-attachment-reject",
                            onClick = { onAction(AttachmentAction.REJECT) },
                        )
                    }
                    attachmentCanContinue(transfer.state) -> {
                        if(transfer.direction == MessageDirection.OUTGOING &&
                            transfer.transport == MediaTransport.DATA_SMS
                        ) {
                            MessagePopupTextAction(
                                label = stringResource(R.string.attachment_continue_standard_sms),
                                testTag = "oneui-attachment-continue-standard-sms",
                                onClick = {
                                    onAction(AttachmentAction.CONTINUE_WITH_STANDARD_SMS)
                                },
                            )
                        }
                        MessagePopupTextAction(
                            label = stringResource(R.string.attachment_continue_anyway),
                            testTag = "oneui-attachment-continue",
                            onClick = { onAction(AttachmentAction.CONTINUE) },
                        )
                        if(!terminal) {
                            MessagePopupTextAction(
                                label = stringResource(R.string.attachment_cancel),
                                testTag = "oneui-attachment-cancel",
                                onClick = { onAction(AttachmentAction.CANCEL) },
                            )
                        }
                    }
                    !terminal -> MessagePopupTextAction(
                        label = stringResource(R.string.attachment_cancel),
                        testTag = "oneui-attachment-cancel",
                        onClick = { onAction(AttachmentAction.CANCEL) },
                    )
                }
            }
        }
    }
}

private enum class MessageMenuMode {
    ACTIONS,
    SELECT_TEXT,
    DETAILS,
    CONFIRM_DELETE,
    CONFIRM_PLAIN_RESEND,
}

@Composable
private fun MessageActionsPopup(
    selection: MessageActionSelection,
    mode: MessageMenuMode,
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long?,
    onDismiss: () -> Unit,
    onModeChange: (MessageMenuMode) -> Unit,
    onCopy: (String) -> Unit,
    onForward: (String) -> Unit,
    onShare: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRetry: (subscriptionId: Long, forcePlainText: Boolean) -> Unit,
) {
    val item = selection.item
    val actionText = item.messageActionText()
    val density = LocalDensity.current
    val positionProvider = remember(selection.anchorBounds, density) {
        MessagePopupPositionProvider(
            selectedBounds = selection.anchorBounds,
            marginPx = with(density) { 12.dp.roundToPx() },
            overlapPx = with(density) { 10.dp.roundToPx() },
        )
    }
    var pendingPlainSubscription by remember(item.stableId) {
        mutableStateOf<SimSubscription?>(null)
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier
                .width(250.dp)
                .testTag("oneui-message-actions"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = ONE_UI_POPUP_MENU_ALPHA,
            ),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                when(mode) {
                    MessageMenuMode.ACTIONS -> {
                        if(item.isFailedOutgoingMessage()) {
                            val originalSubscriptionId = item.messageSubscriptionId()
                                ?: selectedSubscriptionId
                                ?: subscriptions.firstOrNull()?.id
                            originalSubscriptionId?.let { subscriptionId ->
                                MessagePopupTextAction(
                                    label = stringResource(R.string.oneui_message_action_retry),
                                    testTag = "oneui-message-action-retry",
                                    onClick = { onRetry(subscriptionId, false) },
                                )
                            }
                            subscriptions
                                .filter { it.id != originalSubscriptionId }
                                .forEach { subscription ->
                                    MessagePopupTextAction(
                                        label = stringResource(
                                            R.string.oneui_message_action_send_with_sim,
                                            subscription.displayName,
                                        ),
                                        testTag = "oneui-message-action-resend-sim-${subscription.id}",
                                        onClick = {
                                            if(item.isSecureMessage()) {
                                                pendingPlainSubscription = subscription
                                                onModeChange(MessageMenuMode.CONFIRM_PLAIN_RESEND)
                                            } else {
                                                onRetry(subscription.id, false)
                                            }
                                        },
                                    )
                                }
                            DashedMessageMenuDivider()
                        }
                        if(actionText.isNotBlank()) {
                            MessagePopupTextAction(
                                label = stringResource(R.string.oneui_message_action_select_text),
                                testTag = "oneui-message-action-select-text",
                                onClick = { onModeChange(MessageMenuMode.SELECT_TEXT) },
                            )
                            MessagePopupTextAction(
                                label = stringResource(R.string.oneui_message_action_forward),
                                testTag = "oneui-message-action-forward",
                                onClick = { onForward(actionText) },
                            )
                        }
                        MessagePopupTextAction(
                            label = stringResource(
                                if(item.isFavoriteMessage()) {
                                    R.string.oneui_message_action_remove_favorite
                                } else {
                                    R.string.oneui_message_action_add_favorite
                                }
                            ),
                            testTag = "oneui-message-action-favorite",
                            onClick = { onFavorite(!item.isFavoriteMessage()) },
                        )
                        MessagePopupTextAction(
                            label = stringResource(R.string.oneui_message_action_more),
                            testTag = "oneui-message-action-details",
                            onClick = { onModeChange(MessageMenuMode.DETAILS) },
                        )
                        DashedMessageMenuDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            MessagePopupIconAction(
                                icon = Icons.Default.ContentCopy,
                                label = stringResource(R.string.oneui_message_action_copy),
                                testTag = "oneui-message-action-copy",
                                enabled = actionText.isNotBlank(),
                                onClick = { onCopy(actionText) },
                                modifier = Modifier.weight(1f),
                            )
                            MessagePopupIconAction(
                                icon = Icons.Default.Share,
                                label = stringResource(R.string.oneui_message_action_share),
                                testTag = "oneui-message-action-share",
                                onClick = onShare,
                                modifier = Modifier.weight(1f),
                            )
                            MessagePopupIconAction(
                                icon = Icons.Default.Delete,
                                label = stringResource(R.string.oneui_message_action_delete),
                                testTag = "oneui-message-action-delete",
                                onClick = { onModeChange(MessageMenuMode.CONFIRM_DELETE) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    MessageMenuMode.SELECT_TEXT -> MessageTextSelectionContent(
                        text = actionText,
                        onCopy = { onCopy(actionText) },
                        onBack = { onModeChange(MessageMenuMode.ACTIONS) },
                    )
                    MessageMenuMode.DETAILS -> MessageDetailsContent(
                        item = item,
                        onBack = { onModeChange(MessageMenuMode.ACTIONS) },
                    )
                    MessageMenuMode.CONFIRM_DELETE -> {
                        Text(
                            text = stringResource(R.string.oneui_delete_message_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                        Text(
                            text = stringResource(R.string.oneui_delete_message_confirmation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onModeChange(MessageMenuMode.ACTIONS) }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("oneui-message-delete-confirm"),
                            ) {
                                Text(
                                    stringResource(R.string.oneui_message_action_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    MessageMenuMode.CONFIRM_PLAIN_RESEND -> {
                        Text(
                            text = stringResource(R.string.oneui_unencrypted_resend_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.oneui_unencrypted_resend_message,
                                pendingPlainSubscription?.displayName.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onModeChange(MessageMenuMode.ACTIONS) }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    pendingPlainSubscription?.let { onRetry(it.id, true) }
                                },
                                modifier = Modifier.testTag("oneui-message-plain-resend-confirm"),
                            ) {
                                Text(stringResource(R.string.oneui_send_unencrypted))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePopupTextAction(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun MessagePopupIconAction(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if(enabled) 1f else 0.38f),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if(enabled) 1f else 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashedMessageMenuDivider() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 14.dp),
    ) {
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun MessageTextSelectionContent(
    text: String,
    onCopy: () -> Unit,
    onBack: () -> Unit,
) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.oneui_back)) }
        TextButton(onClick = onCopy) {
            Text(stringResource(R.string.oneui_message_action_copy))
        }
    }
}

@Composable
private fun MessageDetailsContent(item: TimelineItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val date = remember(item.timestampMillis) {
        listOf(
            DateFormat.getMediumDateFormat(context).format(Date(item.timestampMillis)),
            DateFormat.getTimeFormat(context).format(Date(item.timestampMillis)),
        ).joinToString(" ")
    }
    val transport = when(item) {
        is TimelineItem.Text -> stringResource(
            if(item.isSecure) R.string.oneui_message_encrypted
            else R.string.oneui_message_plain
        )
        is TimelineItem.Media -> stringResource(
            if(item.isSecure) R.string.oneui_message_encrypted_mms
            else R.string.oneui_message_plain_mms
        )
        is TimelineItem.SecurityEvent -> stringResource(
            R.string.oneui_message_detail_security_update
        )
    }
    val delivery = when(item) {
        is TimelineItem.Text -> deliveryStateLabel(item.deliveryState)
        is TimelineItem.Media -> deliveryStateLabel(item.deliveryState)
        is TimelineItem.SecurityEvent -> null
    }
    Text(
        text = stringResource(R.string.oneui_message_action_details),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    MessageDetailRow(stringResource(R.string.oneui_message_detail_type), transport)
    MessageDetailRow(stringResource(R.string.oneui_message_detail_time), date)
    delivery?.let {
        MessageDetailRow(stringResource(R.string.oneui_message_detail_status), it)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag("oneui-message-details-back"),
        ) {
            Text(stringResource(R.string.oneui_back))
        }
    }
}

@Composable
private fun MessageDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun TimelineItem.messageActionText(): String = when(this) {
    is TimelineItem.Text -> text
    is TimelineItem.Media -> caption.orEmpty()
    is TimelineItem.SecurityEvent -> ""
}

private fun TimelineItem.isFavoriteMessage(): Boolean = when(this) {
    is TimelineItem.Text -> isFavorite
    is TimelineItem.Media -> isFavorite
    is TimelineItem.SecurityEvent -> false
}

private fun TimelineItem.isFailedOutgoingMessage(): Boolean = when(this) {
    is TimelineItem.Text -> direction == MessageDirection.OUTGOING &&
        deliveryState == DeliveryState.FAILED
    is TimelineItem.Media -> direction == MessageDirection.OUTGOING &&
        deliveryState == DeliveryState.FAILED
    is TimelineItem.SecurityEvent -> false
}

private fun TimelineItem.isSecureMessage(): Boolean = when(this) {
    is TimelineItem.Text -> isSecure
    is TimelineItem.Media -> isSecure
    is TimelineItem.SecurityEvent -> false
}

private fun TimelineItem.messageSubscriptionId(): Long? = when(this) {
    is TimelineItem.Text -> subscriptionId
    is TimelineItem.Media -> subscriptionId
    is TimelineItem.SecurityEvent -> null
}

private class MessagePopupPositionProvider(
    private val selectedBounds: IntRect,
    private val marginPx: Int,
    private val overlapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val alignRight = selectedBounds.center.x >= windowSize.width / 2
        val x = if(alignRight) {
            selectedBounds.right - popupContentSize.width
        } else {
            selectedBounds.left
        }.coerceIn(
            marginPx,
            (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
        )
        val below = selectedBounds.bottom - overlapPx
        val above = selectedBounds.top - popupContentSize.height + overlapPx
        val y = if(below + popupContentSize.height <= windowSize.height - marginPx) {
            below
        } else {
            above.coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

private fun androidx.compose.ui.geometry.Rect.toIntRect(): IntRect = IntRect(
    left = left.roundToInt(),
    top = top.roundToInt(),
    right = right.roundToInt(),
    bottom = bottom.roundToInt(),
)

private fun TimelineItem.withFavorite(favorite: Boolean): TimelineItem = when(this) {
    is TimelineItem.Text -> copy(isFavorite = favorite)
    is TimelineItem.Media -> copy(isFavorite = favorite)
    is TimelineItem.SecurityEvent -> this
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

internal fun resolvePagedTimelineItem(
    entry: ConversationTimelineEntry.Message,
    currentItemCount: Int,
    itemAt: (Int) -> TimelineItem?,
): TimelineItem {
    if(entry.pagingIndex !in 0 until currentItemCount) return entry.item

    // itemCount and get(index) come from a live Paging presenter. A refresh can land between
    // those two reads, so retain the snapshot item if the generation changes mid-composition.
    return runCatching { itemAt(entry.pagingIndex) }.getOrNull() ?: entry.item
}

@Composable
private fun AttachmentTransferBubble(
    transfer: AttachmentTransfer,
    onOpen: (() -> Unit)?,
    onLongClick: (IntRect) -> Unit,
) {
    val outgoing = transfer.direction == MessageDirection.OUTGOING
    val progress = if(transfer.totalSms <= 0) 0f else {
        transfer.completedSms.toFloat() / transfer.totalSms
    }
    val transferState = attachmentStateLabel(transfer.state)
    val context = LocalContext.current
    val transferTime = remember(transfer.timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(transfer.timestampMillis))
    }
    val visualKind = attachmentMediaVisualKind(transfer.kind, transfer.mimeType)
    val actionsLabel = stringResource(R.string.attachment_actions)
    val hasActions = attachmentHasActions(transfer.state)
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = transfer.direction,
        content = listOf(
            transfer.fileName,
            formatTransferBytes(transfer.encodedBytes),
            transferState,
        ),
        timestampMillis = transfer.timestampMillis,
    )
    var bubbleBounds by remember { mutableStateOf(IntRect.Zero) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .testTag("oneui-attachment-transfer-${transfer.stableId}")
                .onGloballyPositioned { coordinates ->
                    bubbleBounds = coordinates.boundsInWindow().toIntRect()
                }
                .then(
                    if(hasActions) Modifier.combinedClickable(
                        onClick = { onOpen?.invoke() },
                        onLongClickLabel = actionsLabel,
                        onLongClick = { onLongClick(bubbleBounds) },
                    ) else if(onOpen != null) Modifier.clickable(onClick = onOpen)
                    else Modifier
                ),
            shape = RoundedCornerShape(MessagesTheme.dimensions.bubbleRadius),
            color = if(outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(MessagesTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
            ) {
                Box(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = accessibilityLabel
                    },
                ) {
                    TransferMediaPreview(transfer, visualKind)
                }
                if(transfer.totalSms > 0 &&
                    transfer.state != AttachmentTransferState.OFFERED &&
                    transfer.state != AttachmentTransferState.PREPARING &&
                    transfer.state != AttachmentTransferState.WAITING
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            when(transfer.transport) {
                                MediaTransport.MMS -> R.string.attachment_progress_mms
                                MediaTransport.CLOUD_STORAGE -> R.string.attachment_progress_cloud
                                MediaTransport.DATA_SMS -> R.string.attachment_progress_sms
                                MediaTransport.STANDARD_SMS ->
                                    R.string.attachment_progress_standard_sms
                            },
                            transfer.completedSms,
                            transfer.totalSms,
                            (progress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        transferState,
                        style = MaterialTheme.typography.labelMedium,
                        color = if(transfer.state == AttachmentTransferState.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        transferTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
                ) {
                    Icon(
                        imageVector = if(transfer.isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if(transfer.isSecure) R.string.attachment_protected
                            else R.string.attachment_unprotected
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if(transfer.hasError) {
                    Text(
                        attachmentErrorLabel(transfer.errorMessage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

internal fun attachmentCanContinue(state: AttachmentTransferState): Boolean =
    state == AttachmentTransferState.FAILED ||
        state == AttachmentTransferState.RETRYING ||
        state == AttachmentTransferState.PAUSED

internal fun attachmentHasActions(state: AttachmentTransferState): Boolean =
    state == AttachmentTransferState.OFFERED ||
        attachmentCanContinue(state) ||
        state !in setOf(
            AttachmentTransferState.COMPLETED,
            AttachmentTransferState.FAILED,
            AttachmentTransferState.CANCELLED,
        )

private enum class AttachmentMediaVisualKind { IMAGE, VIDEO, VOICE, DOCUMENT }

internal fun attachmentViewerItem(
    transfer: AttachmentTransfer,
    uriForPath: (String) -> String?,
): TimelineItem.Media? {
    if(transfer.state != AttachmentTransferState.COMPLETED) return null
    val visualKind = attachmentMediaVisualKind(
        transfer.kind,
        transfer.mimeType,
        transfer.fileName,
    )
    if(visualKind != AttachmentMediaVisualKind.IMAGE &&
        visualKind != AttachmentMediaVisualKind.VIDEO
    ) return null
    val path = transfer.completedPath ?: transfer.previewPath ?: return null
    val uri = uriForPath(path)?.takeIf(String::isNotBlank) ?: return null
    return TimelineItem.Media(
        stableId = "attachment-viewer-${transfer.stableId}",
        timestampMillis = transfer.timestampMillis,
        uri = uri,
        fileName = transfer.fileName,
        mimeType = transfer.mimeType,
        caption = null,
        direction = transfer.direction,
        deliveryState = if(transfer.direction == MessageDirection.INCOMING) {
            DeliveryState.RECEIVED
        } else {
            DeliveryState.DELIVERED
        },
        isSecure = transfer.isSecure,
    )
}

private fun attachmentMediaVisualKind(
    kind: AttachmentKind,
    mimeType: String?,
    fileName: String? = null,
): AttachmentMediaVisualKind = when {
    kind == AttachmentKind.VOICE || mimeType.orEmpty().startsWith("audio/", true) ||
        fileName.orEmpty().endsWith(".ogg", true) ||
        fileName.orEmpty().endsWith(".amr", true) ||
        fileName.orEmpty().endsWith(".m4a", true) ->
        AttachmentMediaVisualKind.VOICE
    kind == AttachmentKind.PHOTO || mimeType.orEmpty().startsWith("image/", true) ->
        AttachmentMediaVisualKind.IMAGE
    mimeType.orEmpty().startsWith("video/", true) -> AttachmentMediaVisualKind.VIDEO
    else -> AttachmentMediaVisualKind.DOCUMENT
}

@Composable
private fun TransferMediaPreview(
    transfer: AttachmentTransfer,
    visualKind: AttachmentMediaVisualKind,
) {
    when(visualKind) {
        AttachmentMediaVisualKind.IMAGE,
        AttachmentMediaVisualKind.VIDEO -> {
            val path = transfer.previewPath
            if(path != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 156.dp, max = 220.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = transfer.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(184.dp),
                    )
                    if(visualKind == AttachmentMediaVisualKind.VIDEO) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.58f)) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.attachment_video),
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp).size(30.dp),
                            )
                        }
                    }
                }
                MediaFileCaption(transfer.fileName, transfer.encodedBytes)
            } else {
                DocumentTransferHeader(
                    fileName = transfer.fileName,
                    bytes = transfer.encodedBytes,
                    icon = if(visualKind == AttachmentMediaVisualKind.VIDEO) {
                        Icons.Default.PlayArrow
                    } else Icons.Default.Photo,
                )
            }
        }
        AttachmentMediaVisualKind.VOICE -> {
            val playbackSource = attachmentVoicePlaybackSource(transfer)
            if(playbackSource != null) {
                AttachmentVoicePlayer(
                    source = playbackSource,
                    expectedDurationMillis = transfer.durationMillis,
                )
            } else {
                VoiceMessageSummary(
                    durationMillis = transfer.durationMillis,
                    enabled = false,
                )
            }
        }
        AttachmentMediaVisualKind.DOCUMENT -> DocumentTransferHeader(
            fileName = transfer.fileName,
            bytes = transfer.encodedBytes,
            icon = Icons.Default.Description,
        )
    }
}

/**
 * Outgoing transfers retain their immutable local source for the entire send/retry lifecycle.
 * Incoming media is playable only after it has been verified and committed.
 */
internal fun attachmentVoicePlaybackSource(transfer: AttachmentTransfer): String? = when {
    transfer.completedPath != null -> transfer.completedPath
    transfer.direction == MessageDirection.OUTGOING -> transfer.previewPath
    else -> null
}

@Composable
private fun DocumentTransferHeader(
    fileName: String,
    bytes: Long,
    icon: ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${documentExtension(fileName)} · ${formatTransferBytes(bytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaFileCaption(fileName: String, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            fileName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatTransferBytes(bytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceMessageSummary(durationMillis: Long, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(10.dp).size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            VoicePlaybackWaveform(0f)
            Text(
                formatVoicePlaybackDuration(durationMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if(enabled) 1f else 0.82f,
                ),
            )
        }
    }
}

@Composable
private fun attachmentErrorLabel(error: String?): String = when {
    error?.contains("rate limit", true) == true ->
        stringResource(R.string.attachment_sms_rate_limited)
    error?.contains("no mobile service", true) == true ->
        stringResource(R.string.attachment_sms_no_service)
    error?.contains("radio is off", true) == true ->
        stringResource(R.string.attachment_sms_radio_off)
    error?.contains("SMS send failed (1)", true) == true ->
        stringResource(R.string.attachment_sms_generic_failure)
    else -> stringResource(R.string.attachment_sms_send_failed)
}

@Composable
private fun attachmentErrorDetailsLabel(error: String?): String = when {
    error?.contains("SMS send failed (1)", true) == true ->
        stringResource(R.string.attachment_sms_generic_failure_details)
    else -> attachmentErrorLabel(error)
}

private fun documentExtension(fileName: String): String = fileName
    .substringAfterLast('.', missingDelimiterValue = "FILE")
    .take(6)
    .uppercase()

@Composable
private fun AttachmentVoicePlayer(
    source: String,
    expectedDurationMillis: Long,
) {
    val context = LocalContext.current
    var player by remember(source) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(source) { mutableStateOf(false) }
    var playing by remember(source) { mutableStateOf(false) }
    var positionMillis by remember(source) { mutableLongStateOf(0L) }
    var durationMillis by remember(source) {
        mutableLongStateOf(expectedDurationMillis.coerceAtLeast(0L))
    }
    DisposableEffect(source) {
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
                    runCatching {
                        MediaPlayer().apply {
                            if(source.contains("://")) {
                                setDataSource(context, android.net.Uri.parse(source))
                            } else {
                                setDataSource(source)
                            }
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
                    }.onSuccess { created ->
                        player = created
                    }.onFailure {
                        player?.release()
                        player = null
                    }
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
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    connectedNewer: Boolean,
    connectedOlder: Boolean,
    showSender: Boolean,
    showMetadata: Boolean,
    showSecurityLabel: Boolean,
    onOpenMedia: (TimelineItem.Media) -> Unit,
    onLongClick: (TimelineItem, IntRect) -> Unit,
) {
    when(item) {
        is TimelineItem.Text -> TextBubble(
            item,
            connectedNewer,
            connectedOlder,
            showSender,
            showMetadata,
            showSecurityLabel,
            onLongClick,
        )
        is TimelineItem.Media -> MediaBubble(
            item,
            showSender,
            showMetadata,
            showSecurityLabel,
            onOpenMedia,
            onLongClick,
        )
        is TimelineItem.SecurityEvent -> SecurityEventCard(item)
    }
}

@Composable
private fun TextBubble(
    item: TimelineItem.Text,
    connectedNewer: Boolean,
    connectedOlder: Boolean,
    showSender: Boolean,
    showMetadata: Boolean,
    showSecurityLabel: Boolean,
    onLongClick: (TimelineItem, IntRect) -> Unit,
) {
    val outgoing = item.direction == MessageDirection.OUTGOING
    val shape = bubbleShape(outgoing, connectedNewer, connectedOlder)
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = item.direction,
        content = listOf(item.text),
        timestampMillis = item.timestampMillis,
        deliveryState = item.deliveryState,
    )
    var bubbleBounds by remember { mutableStateOf(IntRect.Zero) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .testTag("oneui-message-bubble-${item.stableId}")
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                }
                .onGloballyPositioned { coordinates ->
                    bubbleBounds = coordinates.boundsInWindow().toIntRect()
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongClick(item, bubbleBounds) },
                ),
            shape = shape,
            color = if(outgoing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                if(showSender && !outgoing) SenderLabel(item.author)
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    color = if(outgoing) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                )
                AnimatedVisibility(
                    visible = showMetadata,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    MessageMetadata(
                        timestampMillis = item.timestampMillis,
                        state = item.deliveryState,
                        outgoing = outgoing,
                        transportLabel = if(showSecurityLabel && item.isSecure) {
                            stringResource(R.string.oneui_message_encrypted)
                        } else {
                            stringResource(R.string.oneui_message_plain)
                        },
                        contentColor = if(outgoing) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        isFavorite = item.isFavorite,
                        modifier = Modifier.testTag(
                            "oneui-message-metadata-${item.stableId}"
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(if(connectedNewer) 3.dp else 10.dp))
    }
}

@Composable
private fun MediaBubble(
    item: TimelineItem.Media,
    showSender: Boolean,
    showMetadata: Boolean,
    showSecurityLabel: Boolean,
    onOpenMedia: (TimelineItem.Media) -> Unit,
    onLongClick: (TimelineItem, IntRect) -> Unit,
) {
    val outgoing = item.direction == MessageDirection.OUTGOING
    val visualKind = attachmentMediaVisualKind(
        kind = when {
            item.mimeType.orEmpty().startsWith("audio/", true) -> AttachmentKind.VOICE
            item.mimeType.orEmpty().startsWith("image/", true) -> AttachmentKind.PHOTO
            else -> AttachmentKind.FILE
        },
        mimeType = item.mimeType,
        fileName = item.fileName,
    )
    val accessibilityLabel = timelineAccessibilityDescription(
        direction = item.direction,
        content = listOfNotNull(
            item.fileName ?: stringResource(R.string.oneui_attachment),
            item.caption?.takeIf(String::isNotBlank),
        ),
        timestampMillis = item.timestampMillis,
        deliveryState = item.deliveryState,
    )
    var bubbleBounds by remember { mutableStateOf(IntRect.Zero) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if(outgoing) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("oneui-message-bubble-${item.stableId}")
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                }
                .onGloballyPositioned { coordinates ->
                    bubbleBounds = coordinates.boundsInWindow().toIntRect()
                }
                .combinedClickable(
                    onClick = {
                        if(!item.uri.isNullOrBlank()) onOpenMedia(item)
                    },
                    onLongClick = { onLongClick(item, bubbleBounds) },
                ),
            shape = RoundedCornerShape(MessagesTheme.dimensions.bubbleRadius),
            color = if(outgoing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if(showSender && !outgoing) {
                    Box(Modifier.padding(
                        start = MessagesTheme.spacing.md,
                        end = MessagesTheme.spacing.md,
                        top = MessagesTheme.spacing.sm,
                    )) {
                        SenderLabel(item.author)
                    }
                }
                when(visualKind) {
                    AttachmentMediaVisualKind.IMAGE,
                    AttachmentMediaVisualKind.VIDEO -> {
                        if(!item.uri.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.fileName
                                        ?: stringResource(R.string.oneui_attachment),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if(visualKind == AttachmentMediaVisualKind.VIDEO) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.58f),
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = stringResource(R.string.attachment_video),
                                            tint = Color.White,
                                            modifier = Modifier.padding(12.dp).size(32.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            DocumentMessageHeader(
                                item.fileName ?: stringResource(R.string.oneui_attachment),
                                if(visualKind == AttachmentMediaVisualKind.VIDEO) {
                                    Icons.Default.PlayArrow
                                } else Icons.Default.Photo,
                            )
                        }
                        item.caption?.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    horizontal = MessagesTheme.spacing.md,
                                    vertical = MessagesTheme.spacing.xs,
                                ),
                            )
                        }
                    }
                    AttachmentMediaVisualKind.VOICE -> {
                        Box(Modifier.padding(MessagesTheme.spacing.sm)) {
                            if(!item.uri.isNullOrBlank()) {
                                AttachmentVoicePlayer(
                                    source = item.uri,
                                    expectedDurationMillis = 0L,
                                )
                            } else {
                                VoiceMessageSummary(0L, enabled = false)
                            }
                        }
                    }
                    AttachmentMediaVisualKind.DOCUMENT -> DocumentMessageHeader(
                        item.fileName ?: stringResource(R.string.oneui_attachment),
                        Icons.Default.Description,
                    )
                }
                if(showMetadata) {
                    MessageMetadata(
                        timestampMillis = item.timestampMillis,
                        state = item.deliveryState,
                        outgoing = outgoing,
                        transportLabel = stringResource(
                            if(showSecurityLabel && item.isSecure) {
                                R.string.oneui_message_encrypted_mms
                            } else {
                                R.string.oneui_message_plain_mms
                            }
                        ),
                        contentColor = if(outgoing) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        isFavorite = item.isFavorite,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(
                                start = MessagesTheme.spacing.md,
                                end = MessagesTheme.spacing.md,
                                bottom = MessagesTheme.spacing.xs,
                            )
                            .testTag("oneui-message-metadata-${item.stableId}"),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DocumentMessageHeader(fileName: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(MessagesTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                documentExtension(fileName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SenderLabel(author: MessageAuthor?) {
    author ?: return
    Text(
        text = author.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 3.dp),
    )
}

@Composable
private fun SecurityEventCard(item: TimelineItem.SecurityEvent) {
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
        horizontalAlignment = when {
            item.kind != SecurityEventKind.DECRYPTION_FAILED -> Alignment.CenterHorizontally
            item.direction == MessageDirection.OUTGOING -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .then(
                    if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                        Modifier.testTag("oneui-decryption-failure-message")
                    } else Modifier
                ),
            shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
            color = if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                MaterialTheme.colorScheme.errorContainer
            } else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = MessagesTheme.spacing.md,
                    vertical = 10.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
            ) {
                Icon(
                    imageVector = if(item.kind == SecurityEventKind.DECRYPTION_FAILED) {
                        Icons.Default.ErrorOutline
                    } else Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MessageMetadata(
    timestampMillis: Long,
    state: DeliveryState,
    outgoing: Boolean,
    transportLabel: String,
    contentColor: Color,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(timestampMillis) {
        DateFormat.getTimeFormat(context).format(Date(timestampMillis))
    }
    Row(
        modifier = modifier
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$transportLabel · $time",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
        if(isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.oneui_message_favorite),
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        if(outgoing) DeliveryStateIndicator(state, contentColor)
    }
}

@Composable
private fun DeliveryStateIndicator(state: DeliveryState, contentColor: Color) {
    if(state == DeliveryState.RECEIVED) return
    val status = deliveryStateLabel(state)
    val icon = when(state) {
        DeliveryState.QUEUED -> Icons.Default.Schedule
        DeliveryState.SENT -> Icons.Default.Done
        DeliveryState.DELIVERED -> Icons.Default.DoneAll
        DeliveryState.FAILED -> Icons.Default.ErrorOutline
        DeliveryState.RECEIVED -> return
    }
    val tag = when(state) {
        DeliveryState.QUEUED -> "oneui-delivery-queued"
        DeliveryState.SENT -> "oneui-delivery-sent"
        DeliveryState.DELIVERED -> "oneui-delivery-delivered"
        DeliveryState.FAILED -> "oneui-delivery-failed"
        DeliveryState.RECEIVED -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = status,
        tint = when(state) {
            DeliveryState.FAILED -> MaterialTheme.colorScheme.error
            else -> contentColor
        },
        modifier = Modifier
            .size(15.dp)
            .testTag(tag),
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
    val shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius)
    val background = when(state) {
        ConversationSecurityState.KEY_CHANGED,
        ConversationSecurityState.RECOVERY_REQUIRED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.sm)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = MessagesTheme.spacing.md, vertical = 10.dp)
            .testTag("oneui-security-inline-status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(securityLabel(state), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SecuritySheet(
    state: ConversationSecurityState,
    contactName: String,
    fingerprint: String?,
    secureSendingEnabled: Boolean,
    channels: List<SecureChannel> = emptyList(),
    selectedAddress: String = "",
    selectedSubscriptionId: Long = -1,
    busy: Boolean,
    onAction: (Boolean) -> Unit,
    onAcceptChangedIdentity: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onSecureSendingChange: (Boolean) -> Unit,
) {
    val primaryAction = securitySheetPrimaryAction(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MessagesTheme.spacing.xl)
            .padding(bottom = 40.dp),
    ) {
        Icon(
            imageVector = when {
                state == ConversationSecurityState.SECURE_VERIFIED -> Icons.Default.VerifiedUser
                state.isEstablished() -> Icons.Default.Lock
                else -> Icons.Default.LockOpen
            },
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
        if(channels.isNotEmpty()) {
            Spacer(Modifier.height(MessagesTheme.spacing.lg))
            Text(
                stringResource(R.string.oneui_secure_channels),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.xs))
            channels.groupBy(SecureChannel::remoteAddress).forEach { (address, routes) ->
                routes.forEach { channel ->
                    val selected = address == selectedAddress &&
                        channel.subscriptionId == selectedSubscriptionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MessagesTheme.dimensions.groupRadius))
                            .background(
                                if(selected) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .padding(horizontal = MessagesTheme.spacing.sm, vertical = 10.dp)
                            .testTag(
                                "oneui-secure-channel-${channel.subscriptionId}-${address.hashCode()}"
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
                    ) {
                        Icon(
                            imageVector = if(channel.isEstablished) Icons.Default.Lock
                                else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if(channel.isEstablished) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    R.string.oneui_secure_channel_pair,
                                    channel.remoteLabel?.takeIf(String::isNotBlank)
                                        ?.let { "$it · $address" } ?: address,
                                    channel.subscriptionName,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                securityLabel(channel.state),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        run {
            Spacer(Modifier.height(MessagesTheme.spacing.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MessagesTheme.dimensions.groupRadius))
                    .clickable(
                        enabled = state.isEstablished(),
                    ) { onSecureSendingChange(!secureSendingEnabled) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.oneui_encrypt_future_messages),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if(state.isEstablished()) {
                                R.string.oneui_encrypt_future_messages_description
                            } else {
                                R.string.oneui_encrypt_unavailable_description
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = state.isEstablished() && secureSendingEnabled,
                    onCheckedChange = onSecureSendingChange,
                    enabled = state.isEstablished(),
                    modifier = Modifier.testTag("oneui-secure-sending-switch"),
                )
            }
        }
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

private fun ConversationSecurityState.isEstablished(): Boolean =
    this == ConversationSecurityState.SECURE_UNVERIFIED ||
        this == ConversationSecurityState.SECURE_VERIFIED

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
    ConversationSecurityState.RECOVERY_REQUIRED,
    // A request/response Data SMS can be lost by either carrier. Keeping this action
    // available prevents a channel from remaining permanently stuck in negotiation.
    ConversationSecurityState.NEGOTIATING -> SecuritySheetPrimaryAction.REPAIR
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
