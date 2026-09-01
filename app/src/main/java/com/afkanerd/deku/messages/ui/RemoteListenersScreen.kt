package com.afkanerd.deku.messages.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerProtocol
import com.afkanerd.deku.messages.domain.RemoteListenerSummary
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import com.afkanerd.deku.messages.domain.RemoteQueueSummary
import com.afkanerd.deku.messages.presentation.RemoteListenerEditorState
import com.afkanerd.deku.messages.presentation.RemoteListenerNotice
import com.afkanerd.deku.messages.presentation.RemoteListenersViewModel
import com.afkanerd.deku.messages.presentation.RemoteQueueEditorState
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.components.PasswordVisibilityButton
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun RemoteListenersOneUiScreen(
    viewModel: RemoteListenersViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val failureText = stringResource(R.string.oneui_operation_failed)
    val missingQueuesText = stringResource(R.string.no_queues_added)
    val permissionText = stringResource(R.string.oneui_remote_permissions_required)
    val activatedText = stringResource(R.string.activated)
    val deactivatedText = stringResource(R.string.deactivated)
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val criticalGranted = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
        ).all { permission -> results[permission] != false }
        viewModel.onPermissionsResult(criticalGranted)
    }

    LaunchedEffect(state.pendingToggleId) {
        if(state.pendingToggleId != null) permissionLauncher.launch(requiredPermissions)
    }
    LaunchedEffect(state.notice) {
        val message = when(state.notice) {
            RemoteListenerNotice.FAILED -> failureText
            RemoteListenerNotice.MISSING_QUEUES -> missingQueuesText
            RemoteListenerNotice.PERMISSION_REQUIRED -> permissionText
            RemoteListenerNotice.ACTIVATED -> activatedText
            RemoteListenerNotice.DEACTIVATED -> deactivatedText
            null -> null
        }
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    val inQueues = state.selectedListenerId != null
    BackHandler(enabled = inQueues) { viewModel.closeQueues() }
    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = if(inQueues) stringResource(R.string.oneui_remote_queues)
                    else stringResource(R.string.remote_listeners),
                showTitle = true,
                navigation = {
                    IconButton(onClick = if(inQueues) viewModel::closeQueues else onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
                actions = {
                    if(state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MessagesTheme.spacing.xl),
                            strokeWidth = MessagesTheme.spacing.xxs,
                        )
                    } else {
                        IconButton(
                            onClick = if(inQueues) viewModel::createQueue else viewModel::create,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if(inQueues) stringResource(R.string.new_queues)
                                    else stringResource(R.string.new_remote_listener),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        if(inQueues) {
            RemoteQueuesList(
                queues = state.queues,
                modifier = Modifier.padding(innerPadding),
                onEdit = viewModel::editQueue,
            )
        } else {
            RemoteListenersList(
                listeners = state.items,
                modifier = Modifier.padding(innerPadding),
                onQueues = viewModel::openQueues,
                onEdit = viewModel::edit,
                onToggle = viewModel::toggle,
            )
        }
    }

    state.editor?.let { editor ->
        RemoteListenerEditorSheet(
            editor = editor,
            busy = state.isBusy,
            onDraftChange = viewModel::updateDraft,
            onSave = viewModel::save,
            onDelete = { editor.id?.let(viewModel::requestDelete) },
            onDismiss = viewModel::dismissEditor,
        )
    }
    state.queueEditor?.let { editor ->
        RemoteQueueEditorSheet(
            editor = editor,
            busy = state.isBusy,
            onExchangeChange = viewModel::updateQueueExchange,
            onDraftChange = viewModel::updateQueueDraft,
            onSave = viewModel::saveQueue,
            onDelete = viewModel::requestQueueDelete,
            onDismiss = viewModel::dismissQueueEditor,
        )
    }
    if(state.deleteCandidateId != null) {
        DeleteConfirmationDialog(
            text = stringResource(R.string.oneui_remote_listener_delete_confirmation),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
    if(state.queueDeleteCandidateId != null) {
        DeleteConfirmationDialog(
            text = stringResource(R.string.oneui_remote_queue_delete_confirmation),
            onConfirm = viewModel::confirmQueueDelete,
            onDismiss = viewModel::dismissQueueDelete,
        )
    }
}

@Composable
private fun RemoteListenersList(
    listeners: List<RemoteListenerSummary>,
    modifier: Modifier,
    onQueues: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
    ) {
        item {
            OneUiExpandedTitle(
                title = stringResource(R.string.remote_listeners),
                subtitle = stringResource(R.string.oneui_remote_listeners_description),
            )
        }
        if(listeners.isEmpty()) {
            item {
                OneUiEmptyState(
                    title = stringResource(R.string.no_remote_listeners),
                    description = stringResource(R.string.oneui_remote_listeners_description),
                )
            }
        } else {
            items(listeners, key = RemoteListenerSummary::id) { listener ->
                RemoteListenerCard(listener, onQueues, onEdit, onToggle)
            }
        }
    }
}

@Composable
private fun RemoteListenerCard(
    listener: RemoteListenerSummary,
    onQueues: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md)
            .clickable { onQueues(listener.id) },
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(MessagesTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listener.displayName.ifBlank { listener.username },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${listener.protocol.name.lowercase()}://${listener.host}:${listener.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(listener.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
            Text(
                text = when {
                    listener.connected -> stringResource(R.string.connected)
                    listener.activated -> stringResource(R.string.disconnected)
                    else -> stringResource(R.string.deactivated)
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    listener.connected -> MaterialTheme.colorScheme.primary
                    listener.activated -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(MessagesTheme.spacing.sm))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onToggle(listener.id) },
            ) {
                Text(
                    stringResource(
                        if(listener.activated) R.string.oneui_deactivate
                        else R.string.oneui_activate
                    )
                )
            }
        }
    }
}

@Composable
private fun RemoteQueuesList(
    queues: List<RemoteQueueSummary>,
    modifier: Modifier,
    onEdit: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
    ) {
        item {
            OneUiExpandedTitle(
                title = stringResource(R.string.oneui_remote_queues),
                subtitle = stringResource(R.string.editing_a_queue_would_restart_the_connection),
            )
        }
        if(queues.isEmpty()) {
            item {
                OneUiEmptyState(
                    title = stringResource(R.string.no_queues_added),
                    description = stringResource(R.string.click_to_add_queues),
                )
            }
        } else {
            items(queues, key = RemoteQueueSummary::id) { queue ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MessagesTheme.spacing.md)
                        .clickable { onEdit(queue.id) },
                    shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        Modifier.padding(MessagesTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs),
                    ) {
                        Text(queue.exchange, style = MaterialTheme.typography.titleMedium)
                        QueueBinding(stringResource(R.string.sim_1), queue.sim1Binding)
                        if(queue.sim2Binding.isNotBlank()) {
                            QueueBinding(stringResource(R.string.sim_2), queue.sim2Binding)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueBinding(label: String, binding: String) {
    Text(
        text = "$label $binding (${binding.replace('.', '_')})",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteListenerEditorSheet(
    editor: RemoteListenerEditorState,
    busy: Boolean,
    onDraftChange: (RemoteListenerDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = editor.draft
    var passwordVisible by remember(editor.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MessagesTheme.spacing.lg,
                    end = MessagesTheme.spacing.lg,
                    bottom = MessagesTheme.spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            Text(
                if(editor.id == null) stringResource(R.string.new_remote_listener)
                else stringResource(R.string.edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            RemoteListenerTextField(
                value = draft.host,
                onValueChange = { onDraftChange(draft.copy(host = it)) },
                label = stringResource(R.string.host_url),
                keyboardType = KeyboardType.Uri,
                enabled = !busy,
                isError = editor.validationFailed && draft.host.isBlank(),
            )
            RemoteListenerTextField(
                value = draft.username,
                onValueChange = { onDraftChange(draft.copy(username = it)) },
                label = stringResource(R.string.username),
                enabled = !busy,
                isError = editor.validationFailed && draft.username.isBlank(),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.password,
                onValueChange = { onDraftChange(draft.copy(password = it)) },
                label = { Text(stringResource(R.string.password)) },
                enabled = !busy,
                isError = editor.validationFailed && draft.password.isBlank(),
                singleLine = true,
                visualTransformation = if(passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    PasswordVisibilityButton(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )
            RemoteListenerTextField(
                value = draft.displayName,
                onValueChange = { onDraftChange(draft.copy(displayName = it)) },
                label = stringResource(R.string.friendly_name),
                enabled = !busy,
            )
            RemoteListenerTextField(
                value = draft.virtualHost,
                onValueChange = { onDraftChange(draft.copy(virtualHost = it)) },
                label = stringResource(R.string.virtual_host),
                enabled = !busy,
                isError = editor.validationFailed && draft.virtualHost.isBlank(),
            )
            RemoteListenerTextField(
                value = draft.port,
                onValueChange = { value ->
                    if(value.all(Char::isDigit)) onDraftChange(draft.copy(port = value))
                },
                label = stringResource(R.string.port),
                keyboardType = KeyboardType.Number,
                enabled = !busy,
                isError = editor.validationFailed && draft.port.toIntOrNull() !in 1..65535,
            )
            Text(stringResource(R.string.choose_protocol), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs)) {
                RemoteListenerProtocol.entries.forEach { protocol ->
                    FilterChip(
                        selected = draft.protocol == protocol,
                        onClick = { onDraftChange(draft.copy(protocol = protocol)) },
                        enabled = !busy && (
                            protocol == RemoteListenerProtocol.AMQP || draft.protocol == protocol
                        ),
                        label = { Text(protocol.name.lowercase()) },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onSave,
            ) { Text(stringResource(R.string.save)) }
            if(editor.id != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(MessagesTheme.spacing.xs))
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteQueueEditorSheet(
    editor: RemoteQueueEditorState,
    busy: Boolean,
    onExchangeChange: (String) -> Unit,
    onDraftChange: (RemoteQueueDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = editor.draft
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MessagesTheme.spacing.lg,
                    end = MessagesTheme.spacing.lg,
                    bottom = MessagesTheme.spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            Text(
                if(editor.id == null) stringResource(R.string.new_queues)
                else stringResource(R.string.edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            RemoteListenerTextField(
                value = draft.exchange,
                onValueChange = onExchangeChange,
                label = stringResource(R.string.exchange),
                enabled = !busy,
                isError = editor.validationFailed && draft.exchange.isBlank(),
            )
            RemoteListenerTextField(
                value = draft.sim1Binding,
                onValueChange = { onDraftChange(draft.copy(sim1Binding = it)) },
                label = stringResource(R.string.sim_1_binding),
                enabled = !busy,
                isError = editor.validationFailed && draft.sim1Binding.isBlank(),
            )
            RemoteListenerTextField(
                value = draft.sim2Binding,
                onValueChange = { onDraftChange(draft.copy(sim2Binding = it)) },
                label = stringResource(R.string.sim_2_binding),
                enabled = !busy,
            )
            Text(
                stringResource(R.string.editing_a_queue_would_restart_the_connection),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onSave,
            ) { Text(stringResource(R.string.save)) }
            if(editor.id != null) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.size(MessagesTheme.spacing.xs))
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun RemoteListenerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun DeleteConfirmationDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.messages_thread_delete_confirmation_title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.messages_thread_delete_confirmation_cancel))
            }
        },
    )
}
