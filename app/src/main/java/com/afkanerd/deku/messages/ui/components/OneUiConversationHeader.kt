package com.afkanerd.deku.messages.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun OneUiConversationCompactHeader(
    header: ConversationHeader?,
    onBack: () -> Unit,
    onExpand: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarInsetPx = WindowInsets.statusBars.getTop(density)
    val totalHeight = with(density) {
        (statusBarInsetPx + MessagesTheme.dimensions.compactTopBarHeight.roundToPx()).toDp()
    }
    val statusBarPadding = with(density) { statusBarInsetPx.toDp() }
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .testTag("oneui-conversation-header-compact"),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = MessagesTheme.spacing.xs,
                top = statusBarPadding,
                end = MessagesTheme.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.oneui_back),
                    tint = accent,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(MessagesTheme.dimensions.minimumTouchTarget)
                    .clickable(enabled = header != null, onClick = onExpand)
                    .testTag("oneui-conversation-header-expand"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
            ) {
                header?.let {
                    ContactAvatar(
                        displayName = it.displayName,
                        avatarUri = it.avatarUri,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = header?.displayName ?: header?.address.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if(header != null) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.oneui_expand_conversation_details),
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.oneui_more_options),
                    tint = accent,
                )
            }
        }
    }
}

@Composable
fun OneUiConversationExpandedHeader(
    visible: Boolean,
    header: ConversationHeader?,
    onCollapse: () -> Unit,
    onCall: (String) -> Unit,
    onVideoCall: (String) -> Unit,
    onDetails: () -> Unit,
    onAddRecipients: (List<String>) -> Unit,
    onNumberSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && header != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
        modifier = modifier.fillMaxSize(),
    ) {
        val resolvedHeader = requireNotNull(header)
        val density = LocalDensity.current
        val statusBarInsetPx = WindowInsets.statusBars.getTop(density)
        val statusBarPadding = with(density) { statusBarInsetPx.toDp() }
        val expandedHeight = statusBarPadding + 194.dp
        val accent = MaterialTheme.colorScheme.primary
        var numberMenuExpanded by remember { mutableStateOf(false) }
        val recipients = resolvedHeader.participants
            .map { it.address }
            .ifEmpty { listOf(resolvedHeader.address) }

        Box(Modifier.fillMaxSize().testTag("oneui-conversation-header-expanded")) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(onClick = onCollapse)
                    .testTag("oneui-conversation-header-scrim"),
            )
            Surface(
                modifier = Modifier.fillMaxWidth().height(expandedHeight),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(top = statusBarPadding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier.padding(start = MessagesTheme.spacing.lg),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.oneui_collapse_conversation_details),
                                tint = accent,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if(!resolvedHeader.isGroupConversation && resolvedHeader.canReply) {
                            IconButton(onClick = { onCall(resolvedHeader.address) }) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = stringResource(R.string.call),
                                    tint = accent,
                                )
                            }
                            IconButton(onClick = { onVideoCall(resolvedHeader.address) }) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = stringResource(R.string.oneui_video_call),
                                    tint = accent,
                                )
                            }
                        }
                        IconButton(
                            onClick = onDetails,
                            modifier = Modifier.padding(end = MessagesTheme.spacing.lg),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.oneui_contact_details),
                                tint = accent,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 68.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.md),
                    ) {
                        ContactAvatar(
                            displayName = resolvedHeader.displayName,
                            avatarUri = resolvedHeader.avatarUri,
                            modifier = Modifier.size(58.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = resolvedHeader.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if(resolvedHeader.isGroupConversation) {
                                Text(
                                    text =
                                    stringResource(
                                        R.string.oneui_group_participants_count,
                                        resolvedHeader.participants.size,
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clickable(
                                                enabled = resolvedHeader.availableContactNumbers.size > 1,
                                                onClick = { numberMenuExpanded = true },
                                            )
                                            .testTag("oneui-conversation-number-selector"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = resolvedHeader.address,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if(resolvedHeader.availableContactNumbers.size > 1) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = stringResource(
                                                    R.string.oneui_choose_contact_number
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = numberMenuExpanded,
                                        onDismissRequest = { numberMenuExpanded = false },
                                        modifier = Modifier
                                            .width(282.dp)
                                            .testTag("oneui-conversation-number-menu"),
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = MessagesTheme.semanticColors.inboxControlSurface.copy(
                                            alpha = ONE_UI_POPUP_MENU_ALPHA,
                                        ),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 8.dp,
                                    ) {
                                        resolvedHeader.availableContactNumbers.forEach { number ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = number.address,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            maxLines = 1,
                                                        )
                                                        number.label?.takeIf(String::isNotBlank)?.let { label ->
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    numberMenuExpanded = false
                                                    onCollapse()
                                                    onNumberSelected(number.address)
                                                },
                                                trailingIcon = if(number.address == resolvedHeader.address) ({
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = accent,
                                                    )
                                                }) else null,
                                                modifier = Modifier.height(58.dp),
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if(resolvedHeader.canReply) {
                        OutlinedButton(
                            onClick = { onAddRecipients(recipients) },
                            modifier = Modifier
                                .padding(
                                    start = 88.dp,
                                    top = MessagesTheme.spacing.xs,
                                    end = 88.dp,
                                )
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("oneui-add-recipients"),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = MessagesTheme.spacing.md),
                        ) {
                            Text(
                                stringResource(R.string.oneui_add_recipients),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
