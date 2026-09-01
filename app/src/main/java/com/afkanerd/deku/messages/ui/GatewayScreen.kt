package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewayPayloadFormat
import com.afkanerd.deku.messages.domain.GatewayProtocol
import com.afkanerd.deku.messages.domain.GatewaySummary
import com.afkanerd.deku.messages.presentation.GatewayEditorState
import com.afkanerd.deku.messages.presentation.GatewayViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.components.PasswordVisibilityButton
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun GatewayScreen(
    viewModel: GatewayViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val failureText = stringResource(R.string.oneui_operation_failed)
    var addMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(failureText)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.gateway_clients),
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
                    if(state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MessagesTheme.spacing.xl),
                            strokeWidth = MessagesTheme.spacing.xxs,
                        )
                    } else {
                        Box {
                            IconButton(onClick = { addMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_new_gateway_server),
                                )
                            }
                            DropdownMenu(
                                expanded = addMenuExpanded,
                                onDismissRequest = { addMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_new_gateway_server_http)) },
                                    onClick = {
                                        addMenuExpanded = false
                                        viewModel.create(GatewayProtocol.HTTPS)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_new_gateway_server_smtp)) },
                                    onClick = {
                                        addMenuExpanded = false
                                        viewModel.create(GatewayProtocol.SMTP)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            item {
                OneUiExpandedTitle(
                    title = stringResource(R.string.gateway_clients),
                    subtitle = stringResource(R.string.oneui_gateway_clients_description),
                )
            }
            if(state.items.isEmpty()) {
                item {
                    OneUiEmptyState(
                        title = stringResource(R.string.gateway_client_no_available_gateway_clients),
                        description = stringResource(R.string.oneui_gateway_clients_description),
                    )
                }
            } else {
                items(state.items, key = GatewaySummary::id) { gateway ->
                    GatewaySummaryCard(
                        gateway = gateway,
                        onClick = { viewModel.edit(gateway.id) },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        GatewayEditorSheet(
            editor = editor,
            busy = state.isBusy,
            onDraftChange = viewModel::updateDraft,
            onSave = viewModel::save,
            onDelete = viewModel::requestDelete,
            onDismiss = viewModel::dismissEditor,
        )
    }

    if(state.deleteCandidateId != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.messages_thread_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.oneui_gateway_delete_confirmation)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.messages_thread_delete_confirmation_cancel))
                }
            },
        )
    }
}

@Composable
private fun GatewaySummaryCard(
    gateway: GatewaySummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(MessagesTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(MessagesTheme.spacing.xs),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        gateway.protocol.name,
                        modifier = Modifier.padding(
                            horizontal = MessagesTheme.spacing.sm,
                            vertical = MessagesTheme.spacing.xxs,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.size(MessagesTheme.spacing.sm))
                Text(
                    gateway.endpoint.ifBlank { gateway.protocol.name },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if(gateway.tag.isNotBlank()) {
                Spacer(Modifier.size(MessagesTheme.spacing.xs))
                Text(
                    "${stringResource(R.string.tag)}: ${gateway.tag}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.size(MessagesTheme.spacing.xxs))
            Text(
                "${stringResource(R.string.encoding)}: ${gateway.payloadFormat.displayName()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GatewayEditorSheet(
    editor: GatewayEditorState,
    busy: Boolean,
    onDraftChange: (GatewayDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var passwordVisible by remember(editor.id) { mutableStateOf(false) }
    val draft = editor.draft
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = MessagesTheme.spacing.lg,
                    end = MessagesTheme.spacing.lg,
                    bottom = MessagesTheme.spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            Text(
                text = if(editor.id == null) stringResource(R.string.add_new_gateway_server)
                    else stringResource(R.string.gateway_server_update),
                style = MaterialTheme.typography.headlineSmall,
            )
            if(draft.protocol == GatewayProtocol.HTTPS) {
                GatewayTextField(
                    value = draft.url,
                    onValueChange = { onDraftChange(draft.copy(url = it)) },
                    label = stringResource(R.string.enter_url),
                    keyboardType = KeyboardType.Uri,
                    isError = editor.validationFailed && draft.url.isBlank(),
                )
                GatewayTextField(
                    value = draft.tag,
                    onValueChange = { onDraftChange(draft.copy(tag = it)) },
                    label = stringResource(R.string.enter_tag_optional),
                )
            } else {
                GatewayTextField(
                    value = draft.smtpHost,
                    onValueChange = { onDraftChange(draft.copy(smtpHost = it)) },
                    label = stringResource(R.string.add_new_gateway_server_smtp_host),
                    keyboardType = KeyboardType.Uri,
                    isError = editor.validationFailed && draft.smtpHost.isBlank(),
                )
                GatewayTextField(
                    value = draft.smtpUsername,
                    onValueChange = { onDraftChange(draft.copy(smtpUsername = it)) },
                    label = stringResource(R.string.enter_username),
                    isError = editor.validationFailed && draft.smtpUsername.isBlank(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = draft.smtpPassword,
                    onValueChange = { onDraftChange(draft.copy(smtpPassword = it)) },
                    label = { Text(stringResource(R.string.enter_password)) },
                    enabled = !busy,
                    isError = editor.validationFailed && draft.smtpPassword.isBlank(),
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
                GatewayTextField(
                    value = draft.smtpRecipient,
                    onValueChange = { onDraftChange(draft.copy(smtpRecipient = it)) },
                    label = stringResource(R.string.enter_recipients_separated_by_comma),
                    keyboardType = KeyboardType.Email,
                    isError = editor.validationFailed && draft.smtpRecipient.isBlank(),
                )
                GatewayTextField(
                    value = draft.smtpFrom,
                    onValueChange = { onDraftChange(draft.copy(smtpFrom = it)) },
                    label = stringResource(R.string.add_new_gateway_server_smtp_from),
                    keyboardType = KeyboardType.Email,
                    isError = editor.validationFailed && draft.smtpFrom.isBlank(),
                )
                GatewayTextField(
                    value = draft.smtpSubject,
                    onValueChange = { onDraftChange(draft.copy(smtpSubject = it)) },
                    label = stringResource(R.string.add_new_gateway_server_smtp_subject),
                )
                GatewayTextField(
                    value = draft.smtpPort,
                    onValueChange = { value ->
                        if(value.all(Char::isDigit)) {
                            onDraftChange(draft.copy(smtpPort = value))
                        }
                    },
                    label = stringResource(R.string.enter_port_number),
                    keyboardType = KeyboardType.Number,
                    isError = editor.validationFailed &&
                        draft.smtpPort.toIntOrNull() !in 1..65535,
                )
            }

            Text(stringResource(R.string.encoding), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.xs)) {
                GatewayPayloadFormat.entries.forEach { format ->
                    FilterChip(
                        selected = draft.payloadFormat == format,
                        onClick = { onDraftChange(draft.copy(payloadFormat = format)) },
                        label = { Text(format.displayName()) },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = onSave,
            ) {
                Text(stringResource(R.string.save))
            }
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
private fun GatewayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun GatewayPayloadFormat.displayName(): String = when(this) {
    GatewayPayloadFormat.ALL -> stringResource(R.string.settings_SMS_routing_type_all_option)
    GatewayPayloadFormat.BASE64_ONLY -> stringResource(R.string.settings_SMS_routing_type_base64_option)
}
