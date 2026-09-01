package com.afkanerd.deku.messages.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.SimSubscription

/** Samsung-style message entry shared by new, one-to-one and group conversations. */
@Composable
fun OneUiMessageComposer(
    value: String,
    enabled: Boolean,
    isSending: Boolean,
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long?,
    onValueChange: (String) -> Unit,
    onSubscriptionSelected: (Long) -> Unit,
    onAttachment: (() -> Unit)?,
    onVoice: (() -> Unit)?,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    composerTestTag: String = "oneui-message-composer",
    inputTestTag: String = "oneui-message-input",
    actionTestTag: String = "oneui-message-action",
) {
    val messageFieldDescription = stringResource(R.string.oneui_message_hint)
    val canInteract = enabled && !isSending
    val hasText = value.isNotBlank()
    val attachmentEnabled = canInteract && onAttachment != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(composerTestTag),
    ) {
        if(subscriptions.isNotEmpty() && selectedSubscriptionId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OneUiSimSelector(
                    subscriptions = subscriptions,
                    selectedSubscriptionId = selectedSubscriptionId,
                    enabled = canInteract,
                    onSubscriptionSelected = onSubscriptionSelected,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SamsungAttachmentButton(
                icon = { tint ->
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = stringResource(R.string.attachment_photo),
                        tint = tint,
                    )
                },
                enabled = attachmentEnabled,
                onClick = { onAttachment?.invoke() },
            )
            SamsungAttachmentButton(
                icon = { tint ->
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.attachment_camera),
                        tint = tint,
                    )
                },
                enabled = attachmentEnabled,
                onClick = { onAttachment?.invoke() },
            )
            SamsungAttachmentButton(
                icon = { tint ->
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.attachment_menu),
                        tint = tint,
                    )
                },
                enabled = attachmentEnabled,
                onClick = { onAttachment?.invoke() },
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if(value.isEmpty()) {
                            Text(
                                messageFieldDescription,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if(enabled) 1f else 0.45f,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { if(canInteract && hasText) onSend() },
                            ),
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                                )
                                .semantics { contentDescription = messageFieldDescription }
                                .testTag(inputTestTag),
                        )
                    }
                    Icon(
                        Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if(enabled) 0.9f else 0.45f,
                        ),
                    )
                }
            }

            val actionEnabled = canInteract && (hasText || onVoice != null)
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .testTag(actionTestTag)
                    .clickable(enabled = actionEnabled) {
                        if(hasText) onSend() else onVoice?.invoke()
                    },
                shape = CircleShape,
                color = if(hasText && actionEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if(isSending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        AnimatedContent(targetState = hasText, label = "voice-send") { showSend ->
                            Icon(
                                if(showSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = stringResource(
                                    if(showSend) R.string.oneui_send else R.string.attachment_voice,
                                ),
                                modifier = Modifier.size(22.dp),
                                tint = if(showSend && actionEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if(actionEnabled) 1f else 0.45f,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungAttachmentButton(
    icon: @Composable (tint: Color) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = if(enabled) 0.9f else 0.38f,
            ),
        )
    }
}

@Composable
private fun OneUiSimSelector(
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long,
    enabled: Boolean,
    onSubscriptionSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = subscriptions.firstOrNull { it.id == selectedSubscriptionId }
        ?: subscriptions.first()
    val chooserDescription = stringResource(R.string.choose_sim_card)
    val canChoose = enabled && subscriptions.size > 1

    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = canChoose) { expanded = true }
                .semantics(mergeDescendants = true) {
                    contentDescription = chooserDescription
                    stateDescription = selected.displayName
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.SimCard,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if(enabled) 1f else 0.45f,
                    ),
                )
                Text(
                    text = selected.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if(enabled) 1f else 0.45f,
                    ),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            subscriptions.forEach { subscription ->
                DropdownMenuItem(
                    text = { Text(subscription.displayName) },
                    onClick = {
                        expanded = false
                        onSubscriptionSelected(subscription.id)
                    },
                )
            }
        }
    }
}
