package com.afkanerd.deku.messages.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.presentation.MessagesSearchViewModel
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun MessagesSearchScreen(
    viewModel: MessagesSearchViewModel,
    onBack: () -> Unit,
    onConversationClick: (ConversationThread, String) -> Unit,
) {
    val query by viewModel.query.collectAsState()
    val results = viewModel.results.collectAsLazyPagingItems()
    val focusRequester = remember { FocusRequester() }
    val handleBack = {
        if(query.isNotEmpty()) viewModel.clear() else onBack()
    }
    BackHandler(onBack = handleBack)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.search_messages_text),
                showTitle = false,
                navigation = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                placeholder = { Text(stringResource(R.string.search_messages_text)) },
                singleLine = true,
                trailingIcon = {
                    if(query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel_search),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MessagesTheme.spacing.md)
                    .focusRequester(focusRequester),
            )
            when {
                query.length < 2 -> OneUiEmptyState(
                    title = stringResource(R.string.search_messages_text),
                    description = stringResource(R.string.oneui_search),
                    modifier = Modifier.fillMaxSize(),
                )
                results.loadState.refresh is LoadState.NotLoading && results.itemCount == 0 ->
                    OneUiEmptyState(
                        title = stringResource(R.string.search_nothing_found),
                        description = query,
                        modifier = Modifier.fillMaxSize(),
                    )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = MessagesTheme.spacing.sm)) {
                    items(
                        count = results.itemCount,
                        key = results.itemKey(ConversationThread::id),
                    ) { index ->
                        results[index]?.let { thread ->
                            SearchResultRow(
                                thread = thread,
                                onClick = { onConversationClick(thread, query) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(thread: ConversationThread, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(
            horizontal = MessagesTheme.spacing.md,
            vertical = MessagesTheme.spacing.sm,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            displayName = thread.displayName,
            avatarUri = thread.avatarUri,
            modifier = Modifier.size(MessagesTheme.dimensions.minimumTouchTarget),
        )
        Column(Modifier.weight(1f).padding(start = MessagesTheme.spacing.md)) {
            Text(thread.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                thread.snippet,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(MessagesTheme.spacing.sm))
        Text(
            DateUtils.getRelativeTimeSpanString(thread.timestampMillis).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = MessagesTheme.spacing.xxl * 2))
}
