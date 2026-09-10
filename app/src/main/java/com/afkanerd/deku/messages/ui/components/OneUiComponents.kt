package com.afkanerd.deku.messages.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun OneUiCompactBar(
    title: String,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val statusBarInsetPx = WindowInsets.statusBars.getTop(density)
    val compactBarPx = with(density) {
        MessagesTheme.dimensions.compactTopBarHeight.roundToPx()
    }
    val totalHeight = with(density) {
        OneUiInsetLayout.totalHeight(compactBarPx, statusBarInsetPx).toDp()
    }
    val contentTopPadding = with(density) { statusBarInsetPx.toDp() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = MessagesTheme.spacing.xs,
                top = contentTopPadding,
                end = MessagesTheme.spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(MessagesTheme.dimensions.minimumTouchTarget)) {
                navigation?.invoke()
            }
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = titleModifier.padding(
                            horizontal = MessagesTheme.spacing.xs,
                        ),
                    )
                }
            }
            actions()
        }
    }
}

internal object OneUiInsetLayout {
    fun totalHeight(compactBarHeightPx: Int, statusBarInsetPx: Int): Int {
        require(compactBarHeightPx >= 0 && statusBarInsetPx >= 0)
        return compactBarHeightPx + statusBarInsetPx
    }
}

@Composable
fun OneUiExpandedTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = MessagesTheme.spacing.xl,
                end = MessagesTheme.spacing.xl,
                top = MessagesTheme.spacing.viewingZone,
                bottom = MessagesTheme.spacing.lg,
            ),
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall)
        subtitle?.let {
            Spacer(Modifier.height(MessagesTheme.spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun OneUiSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md)
            .clip(RoundedCornerShape(MessagesTheme.dimensions.composerRadius))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.oneui_search),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun PasswordVisibilityButton(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if(visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = stringResource(
                if(visible) R.string.hide_password else R.string.show_password
            ),
        )
    }
}

@Composable
fun ContactAvatar(
    displayName: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if(!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = displayName.trim().firstOrNull()?.uppercase() ?: "•",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
fun OneUiEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(MessagesTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(MessagesTheme.spacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
