package com.afkanerd.deku.messages.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.attachments.ui.AttachmentComposer
import com.afkanerd.deku.messages.domain.MessageRecipient
import com.afkanerd.deku.messages.domain.SimSubscription
import com.afkanerd.deku.messages.presentation.NewMessageDestination
import com.afkanerd.deku.messages.presentation.NewMessageViewModel
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.components.OneUiMessageComposer
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun NewMessageScreen(
    viewModel: NewMessageViewModel,
    onBack: () -> Unit,
    onOpenConversation: (NewMessageDestination) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    var pickerSelectionSnapshot by remember { mutableStateOf<List<MessageRecipient>>(emptyList()) }
    var showAttachmentSheet by rememberSaveable { mutableStateOf(false) }
    var startVoiceRecording by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun cancelPicker() {
        viewModel.replaceRecipients(pickerSelectionSnapshot)
        pickerVisible = false
    }

    LaunchedEffect(state.destination) {
        state.destination?.let { destination ->
            viewModel.consumeDestination()
            onOpenConversation(destination)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OneUiCompactBar(
                title = stringResource(
                    when {
                        !pickerVisible -> R.string.compose_new_message_title
                        state.selectedRecipients.isEmpty() -> R.string.oneui_select_recipient
                        else -> R.string.oneui_selected_recipients_count
                    },
                    *if(pickerVisible && state.selectedRecipients.isNotEmpty()) {
                        arrayOf(state.selectedRecipients.size)
                    } else emptyArray(),
                ),
                showTitle = true,
                modifier = Modifier.padding(top = 8.dp),
                navigation = {
                    IconButton(
                        onClick = {
                            if(pickerVisible) cancelPicker() else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
                actions = {
                    if(pickerVisible) {
                        TextButton(
                            onClick = {
                                if(state.selectedRecipients.isEmpty()) cancelPicker()
                                else pickerVisible = false
                            },
                        ) {
                            Text(
                                stringResource(
                                    if(state.selectedRecipients.isEmpty()) R.string.oneui_cancel
                                    else R.string.oneui_done
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = pickerVisible,
            transitionSpec = {
                if(targetState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 5 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "new-message-mode",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) { showPicker ->
            if(showPicker) {
                RecipientPicker(
                    query = state.query,
                    recipients = state.recipients,
                    selectedRecipients = state.selectedRecipients,
                    isLoading = state.isLoading,
                    operationFailed = state.operationFailed,
                    onQueryChanged = viewModel::updateQuery,
                    onRecipientClick = viewModel::toggleRecipient,
                )
            } else {
                NewConversationComposer(
                    query = state.query,
                    draft = state.draftText,
                    selectedRecipients = state.selectedRecipients,
                    recipients = state.recipients,
                    subscriptions = state.subscriptions,
                    selectedSubscriptionId = state.selectedSubscriptionId,
                    isLoading = state.isLoading,
                    isCreating = state.isCreating,
                    operationFailed = state.operationFailed,
                    onQueryChanged = viewModel::updateQuery,
                    onDraftChanged = viewModel::updateDraft,
                    onSubscriptionSelected = viewModel::selectSubscription,
                    onRecipientClick = viewModel::selectRecipient,
                    onAttachment = { showAttachmentSheet = true },
                    onVoice = {
                        startVoiceRecording = true
                        showAttachmentSheet = true
                    },
                    onCreateConversation = viewModel::createConversation,
                    onOpenPicker = {
                        pickerSelectionSnapshot = state.selectedRecipients
                        focusManager.clearFocus(force = true)
                        pickerVisible = true
                    },
                )
            }
        }
    }

    val attachmentAddress = state.selectedRecipients
        .joinToString(",", transform = MessageRecipient::address)
    val attachmentSubscriptionId = state.selectedSubscriptionId
    if(showAttachmentSheet && attachmentAddress.isNotEmpty() && attachmentSubscriptionId != null) {
        AttachmentComposer(
            show = true,
            address = attachmentAddress,
            subscriptionId = attachmentSubscriptionId.toInt(),
            secureEstablished = false,
            startVoiceRecording = startVoiceRecording,
            onDismiss = {
                startVoiceRecording = false
                showAttachmentSheet = false
            },
            onSendAttachment = viewModel::prepareAttachment,
        )
    }
}

@Composable
private fun NewConversationComposer(
    query: String,
    draft: String,
    selectedRecipients: List<MessageRecipient>,
    recipients: List<MessageRecipient>,
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long?,
    isLoading: Boolean,
    isCreating: Boolean,
    operationFailed: Boolean,
    onQueryChanged: (String) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubscriptionSelected: (Long) -> Unit,
    onRecipientClick: (MessageRecipient) -> Unit,
    onAttachment: () -> Unit,
    onVoice: () -> Unit,
    onCreateConversation: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    val recipientFocusRequester = remember { FocusRequester() }
    val messageFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedRecipients.isEmpty()) {
        if(selectedRecipients.isEmpty()) recipientFocusRequester.requestFocus()
        else messageFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .testTag("oneui-new-conversation"),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MessagesTheme.semanticColors.inboxListSurface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedContent(
                targetState = selectedRecipients.isEmpty() && query.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 6 }) togetherWith
                        (fadeOut() + slideOutVertically { it / 6 })
                },
                label = "recipient-suggestions",
                modifier = Modifier.fillMaxSize(),
            ) { suggestionsVisible ->
                if(suggestionsVisible) {
                    RecipientSuggestions(
                        recipients = recipients,
                        isLoading = isLoading,
                        operationFailed = operationFailed,
                        onRecipientClick = onRecipientClick,
                    )
                } else {
                    Spacer(Modifier.fillMaxSize())
                }
            }
            RecipientField(
                query = query,
                selectedRecipients = selectedRecipients,
                onQueryChanged = onQueryChanged,
                onOpenPicker = onOpenPicker,
                focusRequester = recipientFocusRequester,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
        OneUiMessageComposer(
            value = draft,
            enabled = selectedRecipients.isNotEmpty(),
            isSending = isCreating,
            subscriptions = subscriptions,
            selectedSubscriptionId = selectedSubscriptionId,
            onValueChange = onDraftChanged,
            onSubscriptionSelected = onSubscriptionSelected,
            onAttachment = onAttachment.takeIf {
                selectedRecipients.isNotEmpty() && selectedSubscriptionId != null
            },
            onVoice = onVoice.takeIf {
                selectedRecipients.isNotEmpty() && selectedSubscriptionId != null
            },
            onSend = onCreateConversation,
            focusRequester = messageFocusRequester,
            composerTestTag = "oneui-new-message-composer",
            inputTestTag = "oneui-new-message-input",
            actionTestTag = "oneui-create-conversation",
        )
    }
}

@Composable
private fun RecipientSuggestions(
    recipients: List<MessageRecipient>,
    isLoading: Boolean,
    operationFailed: Boolean,
    onRecipientClick: (MessageRecipient) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 74.dp)
            .testTag("oneui-recipient-suggestions"),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            recipients.isEmpty() -> OneUiEmptyState(
                title = stringResource(R.string.search_nothing_found),
                description = stringResource(R.string.type_names_or_phone_numbers),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) {
                items(
                    items = recipients,
                    key = { recipient -> "suggestion-${recipient.id}-${recipient.address}" },
                ) { recipient ->
                    RecipientRow(recipient = recipient, onClick = { onRecipientClick(recipient) })
                }
                if(operationFailed) item { OperationFailedText() }
            }
        }
    }
}

@Composable
private fun RecipientField(
    query: String,
    selectedRecipients: List<MessageRecipient>,
    onQueryChanged: (String) -> Unit,
    onOpenPicker: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.add_new_gateway_server_smtp_recipient)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("oneui-recipient-field"),
        shape = RoundedCornerShape(28.dp),
        color = MessagesTheme.semanticColors.inboxControlSurface,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if(selectedRecipients.isNotEmpty()) {
                    Text(
                        text = if(selectedRecipients.size == 1) {
                            selectedRecipients.first().displayName
                        } else {
                            stringResource(
                                R.string.oneui_recipient_summary,
                                selectedRecipients.first().displayName,
                                selectedRecipients.size - 1,
                            )
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPicker),
                    )
                } else {
                    if(query.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = placeholder }
                            .testTag("oneui-recipient-input"),
                    )
                }
            }
            IconButton(onClick = onOpenPicker) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.oneui_select_recipient),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedRecipientChip(
    recipient: MessageRecipient,
    onRemove: () -> Unit,
    primary: Boolean,
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .testTag(
                if(primary) "oneui-selected-recipient"
                else "oneui-selected-recipient-${recipient.id}"
            ),
        shape = RoundedCornerShape(20.dp),
        color = MessagesTheme.semanticColors.inboxControlSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = recipient.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.oneui_remove_recipient),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RecipientPicker(
    query: String,
    recipients: List<MessageRecipient>,
    selectedRecipients: List<MessageRecipient>,
    isLoading: Boolean,
    operationFailed: Boolean,
    onQueryChanged: (String) -> Unit,
    onRecipientClick: (MessageRecipient) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .testTag("oneui-recipient-picker"),
    ) {
        if(selectedRecipients.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("oneui-recipient-picker-selection"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                items(
                    items = selectedRecipients,
                    key = MessageRecipient::address,
                ) { recipient ->
                    SelectedRecipientChip(
                        recipient = recipient,
                        onRemove = { onRecipientClick(recipient) },
                        primary = recipient == selectedRecipients.first(),
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            shape = RoundedCornerShape(30.dp),
            color = MessagesTheme.semanticColors.inboxListSurface,
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                recipients.isEmpty() -> OneUiEmptyState(
                    title = stringResource(R.string.search_nothing_found),
                    description = stringResource(R.string.type_names_or_phone_numbers),
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                ) {
                    items(
                        items = recipients,
                        key = { recipient -> "picker-${recipient.id}-${recipient.address}" },
                    ) { recipient ->
                        RecipientRow(
                            recipient = recipient,
                            selected = selectedRecipients.any {
                                it.address == recipient.address
                            },
                            onClick = { onRecipientClick(recipient) },
                        )
                    }
                    if(operationFailed) item { OperationFailedText() }
                }
            }
        }
        RecipientPickerSearch(query = query, onQueryChanged = onQueryChanged)
    }
}

@Composable
private fun RecipientPickerSearch(query: String, onQueryChanged: (String) -> Unit) {
    val searchLabel = stringResource(R.string.oneui_search)
    val expandedSearchLabel = stringResource(R.string.oneui_find_contact_or_enter_number)
    val voiceSearchLabel = stringResource(R.string.oneui_voice_search)
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(query.isNotEmpty()) }
    val horizontalPadding by animateDpAsState(
        targetValue = if(expanded) 10.dp else 66.dp,
        label = "recipient-search-width",
    )
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { recognized ->
                    expanded = true
                    onQueryChanged(recognized)
                }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 10.dp)
            .height(52.dp)
            .clickable { focusRequester.requestFocus() }
            .testTag("oneui-recipient-picker-search"),
        shape = RoundedCornerShape(26.dp),
        color = MessagesTheme.semanticColors.inboxControlSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = !expanded, enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if(expanded) 0.dp else 12.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if(query.isEmpty()) {
                    Text(
                        if(expanded) expandedSearchLabel else searchLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if(state.isFocused) expanded = true
                        }
                        .semantics { contentDescription = searchLabel },
                )
            }
            if(query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel_search),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        expanded = true
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, expandedSearchLabel)
                        }
                        if(intent.resolveActivity(context.packageManager) != null) {
                            voiceSearchLauncher.launch(intent)
                        } else {
                            focusRequester.requestFocus()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = voiceSearchLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(
    recipient: MessageRecipient,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick)
            .testTag("oneui-recipient-${recipient.address}"),
        color = if(selected) MessagesTheme.semanticColors.inboxSelectedControlSurface
            else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ContactAvatar(
                    displayName = recipient.displayName,
                    avatarUri = recipient.avatarUri,
                    modifier = Modifier.size(36.dp),
                )
                if(selected) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(
                                    R.string.oneui_recipient_selected
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
            ) {
                Text(
                    recipient.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if(recipient.displayName != recipient.address) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        recipient.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationFailedText() {
    Text(
        text = stringResource(R.string.oneui_operation_failed),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(MessagesTheme.spacing.md),
    )
}
