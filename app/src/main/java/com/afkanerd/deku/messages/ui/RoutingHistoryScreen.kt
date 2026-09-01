package com.afkanerd.deku.messages.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.RoutingHistoryItem
import com.afkanerd.deku.messages.domain.RoutingHistoryState
import com.afkanerd.deku.messages.presentation.RoutingHistoryViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun RoutingHistoryScreen(
    viewModel: RoutingHistoryViewModel,
    onBack: () -> Unit,
    onGatewayClients: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.routed_messages),
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
                    IconButton(onClick = onGatewayClients) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.list_gateway_clients),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
        ) {
            item {
                OneUiExpandedTitle(
                    title = stringResource(R.string.routed_messages),
                    subtitle = stringResource(R.string.oneui_routing_history_description),
                )
            }
            when {
                state.isLoading -> item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(MessagesTheme.spacing.xxl),
                    )
                }
                state.items.isEmpty() -> item {
                    OneUiEmptyState(
                        title = stringResource(R.string.oneui_no_routed_messages),
                        description = stringResource(
                            if(state.failed) R.string.oneui_routing_history_failed
                            else R.string.oneui_no_routed_messages_description,
                        ),
                    )
                }
                else -> items(state.items, key = RoutingHistoryItem::workId) { item ->
                    RoutingHistoryCard(item)
                }
            }
        }
    }
}

@Composable
private fun RoutingHistoryCard(item: RoutingHistoryItem) {
    val context = LocalContext.current
    val date = DateUtils.formatDateTime(
        context,
        item.timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md),
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(MessagesTheme.spacing.lg)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if(item.displayName != item.address) {
                        Text(
                            text = item.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(MessagesTheme.spacing.sm))
                RoutingStateBadge(item.state)
            }
            Spacer(Modifier.size(MessagesTheme.spacing.sm))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(MessagesTheme.spacing.sm))
            Row {
                Text(
                    text = date,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.oneui_gateway_number, item.gatewayId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoutingStateBadge(state: RoutingHistoryState) {
    val label = stringResource(
        when(state) {
            RoutingHistoryState.ENQUEUED -> R.string.oneui_route_enqueued
            RoutingHistoryState.RUNNING -> R.string.oneui_route_running
            RoutingHistoryState.SUCCEEDED -> R.string.oneui_route_succeeded
            RoutingHistoryState.FAILED -> R.string.oneui_route_failed
            RoutingHistoryState.BLOCKED -> R.string.oneui_route_blocked
            RoutingHistoryState.CANCELLED -> R.string.oneui_route_cancelled
            RoutingHistoryState.UNKNOWN -> R.string.oneui_route_unknown
        }
    )
    Surface(
        shape = RoundedCornerShape(MessagesTheme.spacing.xs),
        color = if(state == RoutingHistoryState.FAILED || state == RoutingHistoryState.CANCELLED) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = MessagesTheme.spacing.sm,
                vertical = MessagesTheme.spacing.xxs,
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
