package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.MessageRecipient
import com.afkanerd.deku.messages.presentation.ContactDetailsViewModel
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.ONE_UI_POPUP_MENU_ALPHA
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun ContactDetailsScreen(
    viewModel: ContactDetailsViewModel,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onOpenContact: (String) -> Unit,
    onSearch: (String) -> Unit,
    onCopyNumber: (String) -> Unit,
    onEditParticipants: (List<String>) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val header = state.header
    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.oneui_contact_details),
                showTitle = true,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if(header == null) {
            Box(Modifier.padding(innerPadding).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ContactDetailsContent(
                header = header,
                fingerprint = state.fingerprint,
                blocked = state.isBlocked,
                operationFailed = state.operationFailed,
                photoCount = state.photoCount,
                voiceCount = state.voiceCount,
                fileCount = state.fileCount,
                onCall = { onCall(header.address) },
                onOpenContact = { onOpenContact(header.address) },
                onSearch = { onSearch(header.address) },
                onCopyNumber = { onCopyNumber(header.address) },
                onEditParticipants = {
                    onEditParticipants(header.participants.map(MessageRecipient::address))
                },
                onSelectSubscription = viewModel::selectSubscription,
                onToggleBlocked = viewModel::toggleBlocked,
                onToggleMuted = viewModel::toggleMuted,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun ContactDetailsContent(
    header: ConversationHeader,
    fingerprint: String?,
    blocked: Boolean,
    operationFailed: Boolean,
    photoCount: Int,
    voiceCount: Int,
    fileCount: Int,
    onCall: () -> Unit,
    onOpenContact: () -> Unit,
    onSearch: () -> Unit,
    onCopyNumber: () -> Unit,
    onEditParticipants: () -> Unit,
    onSelectSubscription: (Long) -> Unit,
    onToggleBlocked: () -> Unit,
    onToggleMuted: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(
                    top = MessagesTheme.spacing.xl,
                    bottom = MessagesTheme.spacing.lg,
                ),
            ) {
                ContactAvatar(
                    displayName = header.displayName,
                    avatarUri = header.avatarUri,
                    modifier = Modifier.size(MessagesTheme.spacing.xxl * 2),
                )
                Text(
                    text = header.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = MessagesTheme.spacing.md),
                )
                if(!header.isGroupConversation) {
                    Text(
                        text = header.address,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.lg),
                modifier = Modifier.padding(bottom = MessagesTheme.spacing.lg),
            ) {
                if(!header.isGroupConversation) {
                    ContactAction(Icons.Default.Call, stringResource(R.string.call), onCall)
                    ContactAction(Icons.Default.PersonAdd, stringResource(R.string.oneui_add_contact), onOpenContact)
                }
                ContactAction(Icons.Default.Search, stringResource(R.string.oneui_search), onSearch)
            }
        }
        if(header.isGroupConversation) {
            item {
                DetailsCard {
                    Column(Modifier.fillMaxWidth().padding(MessagesTheme.spacing.lg)) {
                        Text(
                            text = stringResource(
                                R.string.oneui_group_participants_count,
                                header.participants.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        header.participants.forEach { participant ->
                            ParticipantRow(participant)
                        }
                        TextButton(
                            onClick = onEditParticipants,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null)
                            Text(
                                text = stringResource(R.string.oneui_edit_group_participants),
                                modifier = Modifier.padding(start = MessagesTheme.spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        if(header.subscriptions.size > 1) {
            item {
                DetailsCard {
                    SubscriptionRow(header, onSelectSubscription)
                }
            }
        }
        if(!header.isGroupConversation) {
            item {
                DetailsCard {
                    Row(
                        modifier = Modifier.padding(MessagesTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Column(Modifier.padding(start = MessagesTheme.spacing.md)) {
                            Text(stringResource(R.string.end_to_end_encryption))
                            Text(
                                text = header.securityState.securityLabel(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            fingerprint?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = MessagesTheme.spacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            DetailsCard {
                DetailsValueRow(
                    icon = Icons.Default.Photo,
                    title = stringResource(R.string.attachment_photo),
                    value = photoCount.toString(),
                )
                DetailsValueRow(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.attachment_voice),
                    value = voiceCount.toString(),
                )
                DetailsValueRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.attachment_file),
                    value = fileCount.toString(),
                )
            }
        }
        item {
            DetailsCard {
                TextButton(
                    onClick = onToggleMuted,
                    modifier = Modifier.fillMaxWidth().padding(MessagesTheme.spacing.sm),
                ) {
                    Icon(
                        if(header.isMuted) Icons.Default.NotificationsOff
                        else Icons.Default.Notifications,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            if(header.isMuted) R.string.oneui_unmute else R.string.oneui_mute
                        ),
                        modifier = Modifier.padding(start = MessagesTheme.spacing.sm),
                    )
                }
            }
        }
        if(!header.isGroupConversation) item {
            DetailsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCopyNumber)
                        .padding(MessagesTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.oneui_phone_number))
                        Text(
                            header.address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.conversation_menu_copy))
                }
            }
        }
        if(!header.isGroupConversation) {
            item {
                DetailsCard {
                    TextButton(
                        onClick = onToggleBlocked,
                        modifier = Modifier.fillMaxWidth().padding(MessagesTheme.spacing.sm),
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = stringResource(if(blocked) R.string.unblock else R.string.block),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = MessagesTheme.spacing.sm),
                        )
                    }
                }
            }
        }
        if(operationFailed) {
            item {
                Text(
                    text = stringResource(R.string.oneui_operation_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(MessagesTheme.spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: MessageRecipient) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MessagesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            displayName = participant.displayName,
            avatarUri = participant.avatarUri,
            modifier = Modifier.size(MessagesTheme.dimensions.minimumTouchTarget),
        )
        Column(Modifier.padding(start = MessagesTheme.spacing.md)) {
            Text(participant.displayName, style = MaterialTheme.typography.bodyLarge)
            if(participant.displayName != participant.address) {
                Text(
                    participant.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailsValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(MessagesTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(title, modifier = Modifier.weight(1f).padding(start = MessagesTheme.spacing.md))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(MessagesTheme.dimensions.minimumTouchTarget),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DetailsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = MessagesTheme.spacing.md,
            vertical = MessagesTheme.spacing.xs,
        ),
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        content = content,
    )
}

@Composable
private fun SubscriptionRow(
    header: ConversationHeader,
    onSelectSubscription: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = header.subscriptions.firstOrNull { it.id == header.subscriptionId }
    Row(
        modifier = Modifier.fillMaxWidth().padding(MessagesTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.SimCard, contentDescription = null)
        Column(Modifier.weight(1f).padding(start = MessagesTheme.spacing.md)) {
            Text(stringResource(R.string.send_with))
            Text(
                selected?.displayName ?: stringResource(R.string.sim, selected?.slotIndex?.plus(1) ?: 1),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string._switch)) }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                    alpha = ONE_UI_POPUP_MENU_ALPHA,
                ),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
            ) {
                header.subscriptions.forEach { subscription ->
                    DropdownMenuItem(
                        text = { Text(subscription.displayName) },
                        onClick = {
                            expanded = false
                            onSelectSubscription(subscription.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSecurityState.securityLabel(): String = when(this) {
    ConversationSecurityState.PLAIN -> stringResource(R.string.oneui_secure_plain)
    ConversationSecurityState.NEGOTIATING -> stringResource(R.string.oneui_secure_negotiating)
    ConversationSecurityState.REQUEST_RECEIVED ->
        stringResource(R.string.oneui_security_event_request_received)
    ConversationSecurityState.SECURE_UNVERIFIED -> stringResource(R.string.oneui_secure_unverified)
    ConversationSecurityState.SECURE_VERIFIED -> stringResource(R.string.oneui_secure_verified)
    ConversationSecurityState.KEY_CHANGED -> stringResource(R.string.oneui_secure_key_changed)
    ConversationSecurityState.RECOVERY_REQUIRED -> stringResource(R.string.oneui_secure_recovery)
}
