package com.afkanerd.deku.messages.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.ConversationGroup
import com.afkanerd.deku.messages.domain.ConversationFolder
import com.afkanerd.deku.messages.domain.ConversationThreadAction
import com.afkanerd.deku.messages.presentation.InboxViewModel
import com.afkanerd.deku.messages.ui.components.ContactAvatar
import com.afkanerd.deku.messages.ui.components.OneUiEmptyState
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme
import com.afkanerd.deku.messages.ui.components.ONE_UI_POPUP_MENU_ALPHA
import kotlinx.coroutines.flow.Flow

@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onRequestDefaultSmsRole: () -> Unit,
    onRequestContacts: () -> Unit,
    onConversationClick: (ConversationThread) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewMessageClick: () -> Unit,
    onContactsClick: () -> Unit = onNewMessageClick,
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AnimatedContent(
        targetState = when {
            state.roleRequired -> InboxPane.ROLE
            state.isImporting -> InboxPane.IMPORT
            state.showContactsPrompt -> InboxPane.CONTACTS
            state.showReady -> InboxPane.READY
            else -> InboxPane.LIST
        },
        label = "inbox-pane",
    ) { pane ->
        when(pane) {
            InboxPane.ROLE -> DefaultSmsOnboarding(onRequestDefaultSmsRole)
            InboxPane.IMPORT -> MessageImportScreen(
                progress = state.importProgress,
                hasError = state.error != null,
                onRetry = viewModel::retryImport,
            )
            InboxPane.CONTACTS -> ContactsOnboarding(
                onRequestContacts = onRequestContacts,
                onSkip = viewModel::completeContactsPrompt,
            )
            InboxPane.READY -> ReadyOnboarding(viewModel::finishOnboarding)
            InboxPane.LIST -> ConversationList(
                viewModel = viewModel,
                onConversationClick = onConversationClick,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNewMessageClick = onNewMessageClick,
                onContactsClick = onContactsClick,
            )
        }
    }
}

private enum class InboxPane { ROLE, IMPORT, CONTACTS, READY, LIST }

@Composable
private fun DefaultSmsOnboarding(onRequestDefaultSmsRole: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MessagesTheme.spacing.xl),
        ) {
            OneUiExpandedTitle(
                title = stringResource(R.string.oneui_messages_title),
                subtitle = stringResource(R.string.oneui_messages_subtitle),
                modifier = Modifier.padding(horizontal = 0.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.oneui_default_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.sm))
            Text(
                text = stringResource(R.string.oneui_default_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.xl))
            Button(
                onClick = onRequestDefaultSmsRole,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.oneui_default_action))
            }
            Spacer(Modifier.height(MessagesTheme.spacing.md))
            Text(
                text = stringResource(R.string.oneui_default_privacy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.xxl))
        }
    }
}

@Composable
private fun ContactsOnboarding(
    onRequestContacts: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MessagesTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.oneui_contacts_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.sm))
            Text(
                stringResource(R.string.oneui_contacts_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(MessagesTheme.spacing.xl))
            Button(onClick = onRequestContacts, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.oneui_contacts_action))
            }
            Spacer(Modifier.height(MessagesTheme.spacing.xs))
            androidx.compose.material3.TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.oneui_not_now))
            }
        }
    }
}

@Composable
private fun ReadyOnboarding(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MessagesTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.oneui_ready_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(MessagesTheme.spacing.sm))
        Text(
            stringResource(R.string.oneui_ready_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MessagesTheme.spacing.xl))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.oneui_continue))
        }
    }
}

@Composable
private fun MessageImportScreen(
    progress: com.afkanerd.deku.messages.domain.ImportProgress?,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MessagesTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if(hasError) stringResource(R.string.oneui_import_failed)
                else stringResource(R.string.oneui_import_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(MessagesTheme.spacing.md))
        if(hasError) {
            Button(onClick = onRetry) { Text(stringResource(R.string.oneui_retry)) }
        } else {
            val current = progress?.completedThreads ?: 0
            val total = progress?.totalThreads ?: 0
            if(total > 0) {
                LinearProgressIndicator(
                    progress = { current.toFloat() / total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MessagesTheme.spacing.sm))
                Text(stringResource(R.string.oneui_import_progress, current, total))
            } else {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(MessagesTheme.spacing.md))
            Text(
                text = stringResource(R.string.oneui_import_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationList(
    viewModel: InboxViewModel,
    onConversationClick: (ConversationThread) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewMessageClick: () -> Unit,
    onContactsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val unreadMessageCount by viewModel.unreadMessageCount.collectAsState(initial = 0)
    val conversationGroups by viewModel.conversationGroups.collectAsState(initial = emptyList())
    val threads = viewModel.threads.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedThreads by remember {
        mutableStateOf<Map<Int, ConversationThread>>(emptyMap())
    }
    var selectionOptionsExpanded by remember { mutableStateOf(false) }
    var categoryDialogStep by remember { mutableStateOf(CategoryDialogStep.NONE) }
    var categoryManagementStep by remember { mutableStateOf(CategoryManagementStep.NONE) }
    var managedCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var categoryMembers by remember { mutableStateOf(emptySet<Int>()) }
    var dismissedUnreadCount by rememberSaveable { mutableIntStateOf(-1) }
    val listScopeKey = "${state.folder.name}:${state.selectedGroupId.orEmpty()}"
    var restoredListScopeKey by rememberSaveable { mutableStateOf(listScopeKey) }
    val folderTitle = folderTitle(state.folder)
    val density = LocalDensity.current
    val rootWindowInsets = ViewCompat.getRootWindowInsets(LocalView.current)
    val rawSystemBarInsets = rootWindowInsets?.getInsetsIgnoringVisibility(
        WindowInsetsCompat.Type.systemBars(),
    )
    val statusBarPadding = with(density) {
        (rawSystemBarInsets?.top ?: 0).toDp()
    }
    val navigationBarPadding = with(density) {
        (rawSystemBarInsets?.bottom ?: 0).toDp()
    }
    val useLargeTextNavigation = density.fontScale >= 1.5f
    val bottomNavigationWidth = if(useLargeTextNavigation) 288.dp else 202.dp
    val bottomNavigationHeight = if(useLargeTextNavigation) 72.dp
        else MessagesTheme.dimensions.inboxBottomNavigationHeight
    val fabBottomPadding = navigationBarPadding + bottomNavigationHeight + 8.dp
    val viewingZoneHeight = MessagesTheme.dimensions.inboxViewingZoneHeight
    val expandedControlTop = statusBarPadding + viewingZoneHeight
    val controlTopOffset by remember(listState) {
        derivedStateOf {
            if(listState.firstVisibleItemIndex == 0) {
                expandedControlTop -
                    with(density) { listState.firstVisibleItemScrollOffset.toDp() }
            } else {
                statusBarPadding
            }.coerceAtLeast(statusBarPadding)
        }
    }
    val controlHeight = MessagesTheme.dimensions.inboxControlHeight
    val controlsCapsuled by remember(listState, threads.itemCount, density) {
        derivedStateOf {
            val firstConversation = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.index in 2 until (2 + threads.itemCount)
            }
            val controlBottom = with(density) {
                (controlTopOffset + controlHeight).roundToPx()
            }
            firstConversation != null && firstConversation.offset < controlBottom - 1
        }
    }
    var hideChromeForForwardFling by remember { mutableStateOf(false) }
    var expandAfterReverseFling by remember { mutableStateOf(false) }
    val scrollChromeConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if(available.y > 0.5f) {
                    hideChromeForForwardFling = false
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                hideChromeForForwardFling = shouldHideInboxChromeForFling(
                    velocityYPx = available.y,
                    density = density.density,
                )
                expandAfterReverseFling = shouldExpandInboxHeaderAfterReverseFling(
                    velocityYPx = available.y,
                    density = density.density,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                )
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                hideChromeForForwardFling = false
                if(
                    expandAfterReverseFling &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset > 0
                ) {
                    listState.animateScrollToItem(0)
                }
                expandAfterReverseFling = false
                return Velocity.Zero
            }
        }
    }
    val controlsHidden by remember(listState) {
        derivedStateOf {
            controlsCapsuled && hideChromeForForwardFling && listState.isScrollInProgress
        }
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if(controlsHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "inbox-control-visibility-alpha",
    )
    val operationFailedMessage = stringResource(R.string.oneui_operation_failed)
    val exportCompleteMessage = stringResource(R.string.conversations_exported_complete)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportMessages(it.toString()) }
    }

    BackHandler(enabled = selectedThreads.isNotEmpty()) {
        selectionOptionsExpanded = false
        selectedThreads = emptyMap()
    }

    LaunchedEffect(state.threadActionFailed) {
        if(state.threadActionFailed) {
            snackbarHostState.showSnackbar(
                message = operationFailedMessage,
            )
        }
    }
    LaunchedEffect(unreadMessageCount) {
        if(unreadMessageCount == 0) dismissedUnreadCount = -1
    }
    LaunchedEffect(listScopeKey) {
        if(restoredListScopeKey != listScopeKey) {
            listState.scrollToItem(0)
            restoredListScopeKey = listScopeKey
        }
    }
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { succeeded ->
            snackbarHostState.showSnackbar(
                if(succeeded) exportCompleteMessage else operationFailedMessage
            )
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollChromeConnection)
                    .testTag("oneui-inbox-list"),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = if(threads.itemCount > MAX_UNPADDED_INBOX_ROWS) {
                        navigationBarPadding + bottomNavigationHeight + 40.dp
                    } else {
                        0.dp
                    },
                ),
            ) {
                item(key = "viewing-zone") {
                    Column {
                        Spacer(Modifier.height(statusBarPadding))
                        AnimatedContent(
                            targetState = selectedThreads.isNotEmpty(),
                            label = "inbox-selection-title",
                        ) { selecting ->
                            if(selecting) {
                                InboxSelectionViewingZone(selectionCount = selectedThreads.size)
                            }
                            else InboxViewingZone(
                                title = folderTitle,
                                unreadMessageCount = unreadMessageCount.takeIf {
                                    state.folder == ConversationFolder.INBOX &&
                                        it != dismissedUnreadCount
                                } ?: 0,
                                onViewUnread = {
                                    viewModel.selectFolder(ConversationFolder.UNREAD)
                                },
                                onDismissUnread = {
                                    dismissedUnreadCount = unreadMessageCount
                                },
                            )
                        }
                    }
                }
                item(key = "controls-space") {
                    Spacer(Modifier.height(controlHeight))
                }
                items(
                    count = threads.itemCount,
                    key = threads.itemKey { it.id },
                ) { index ->
                    threads[index]?.let { thread ->
                        ConversationRow(
                            thread = thread,
                            roundedTop = index == 0,
                            roundedBottom = state.selectedGroupId == null &&
                                index == threads.itemCount - 1,
                            selected = thread.id in selectedThreads,
                            onClick = {
                                if(selectedThreads.isEmpty()) {
                                    onConversationClick(thread)
                                } else {
                                    selectedThreads = if(thread.id in selectedThreads) {
                                        selectedThreads - thread.id
                                    } else {
                                        selectedThreads + (thread.id to thread)
                                    }
                                }
                            },
                            onLongClick = {
                                selectionOptionsExpanded = false
                                selectedThreads = selectedThreads + (thread.id to thread)
                            },
                        )
                    }
                }
                if(state.selectedGroupId != null) {
                    if(threads.itemCount == 0 &&
                        threads.loadState.refresh !is LoadState.NotLoading
                    ) {
                        item(key = "category-load-state") {
                            InboxEmptySurface(
                                loadState = threads.loadState.refresh,
                                onRetry = threads::retry,
                            )
                        }
                    }
                    item(key = "add-conversations-to-category") {
                        AddConversationsToCategoryRow(
                            roundedTop = threads.itemCount == 0 &&
                                threads.loadState.refresh is LoadState.NotLoading,
                            onClick = {
                                conversationGroups
                                    .firstOrNull { it.id == state.selectedGroupId }
                                    ?.let { group ->
                                        managedCategoryId = group.id
                                        categoryName = group.name
                                        categoryMembers = group.threadIds
                                        categoryDialogStep = CategoryDialogStep.MEMBERS
                                    }
                            },
                        )
                    }
                } else if(threads.itemCount == 0) {
                    item(key = "empty-list-surface") {
                        InboxEmptySurface(
                            loadState = threads.loadState.refresh,
                            onRetry = threads::retry,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarPadding + 28.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                            0.56f to MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            1f to androidx.compose.ui.graphics.Color.Transparent,
                        )
                    )
                    .zIndex(1f)
                    .testTag("oneui-inbox-top-scrim"),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navigationBarPadding + 72.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.52f to MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                        )
                    )
                    .zIndex(0.5f)
                    .testTag("oneui-inbox-bottom-scrim"),
            )

            if(state.selectedGroupId == null && threads.itemCount > 0) {
                InboxAppendStatus(
                    appendState = threads.loadState.append,
                    onRetry = threads::retry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = navigationBarPadding + bottomNavigationHeight + 16.dp,
                        )
                        .zIndex(1.5f),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = controlTopOffset)
                    .alpha(chromeAlpha)
                    .zIndex(2f)
                    .testTag(
                        if(controlsHidden) "oneui-inbox-controls-hidden"
                        else "oneui-inbox-controls-visible"
                    ),
            ) {
                AnimatedContent(
                    targetState = selectedThreads.isNotEmpty(),
                    label = "inbox-selection-controls",
                ) { selecting ->
                    if(selecting) {
                        InboxSelectionControls(
                            onCancel = {
                                selectionOptionsExpanded = false
                                selectedThreads = emptyMap()
                            },
                            onSearchClick = {
                                selectionOptionsExpanded = false
                                selectedThreads = emptyMap()
                                onSearchClick()
                            },
                        )
                    } else {
                        InboxControlIsland(
                            selectedFolder = state.folder,
                            selectedFolderTitle = folderTitle,
                            selectedGroupId = state.selectedGroupId,
                            groups = conversationGroups,
                            capsuled = controlsCapsuled,
                            filterExpanded = filterExpanded,
                            overflowExpanded = menuExpanded,
                            onSelectInbox = { viewModel.selectGroup(null) },
                            onSelectGroup = viewModel::selectGroup,
                            onManageGroup = { groupId ->
                                managedCategoryId = groupId
                                categoryManagementStep = CategoryManagementStep.OVERVIEW
                            },
                            onAddGroup = {
                                managedCategoryId = null
                                categoryName = ""
                                categoryMembers = emptySet()
                                categoryDialogStep = CategoryDialogStep.NAME
                            },
                            onFilterClick = { filterExpanded = true },
                            onFilterDismiss = { filterExpanded = false },
                            onFolderSelected = { folder ->
                                filterExpanded = false
                                viewModel.selectFolder(folder)
                            },
                            onSearchClick = onSearchClick,
                            onOverflowClick = { menuExpanded = true },
                            onOverflowDismiss = { menuExpanded = false },
                            onMarkAllRead = {
                                menuExpanded = false
                                viewModel.markAllRead()
                            },
                            onExport = {
                                menuExpanded = false
                                exportLauncher.launch(
                                    "Messages_Backup_${System.currentTimeMillis()}.json"
                                )
                            },
                            onSettingsClick = {
                                menuExpanded = false
                                onSettingsClick()
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedThreads.isEmpty(),
                modifier = Modifier
                    .zIndex(2f)
                    .alpha(chromeAlpha)
                    .testTag(
                        if(controlsHidden) "oneui-inbox-bottom-chrome-hidden"
                        else "oneui-inbox-bottom-chrome-visible"
                    ),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    FloatingActionButton(
                        onClick = onNewMessageClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = fabBottomPadding)
                            .size(MessagesTheme.dimensions.inboxFabSize)
                            .testTag("oneui-inbox-compose-fab"),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        containerColor = MessagesTheme.semanticColors.inboxFloatingActionSurface,
                        contentColor = MessagesTheme.semanticColors.onInboxFloatingAction,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chat_add_on),
                            contentDescription = stringResource(R.string.oneui_new_message),
                        )
                    }
                    InboxBottomNavigation(
                        unreadMessageCount = unreadMessageCount,
                        onConversationsClick = {
                            viewModel.selectFolder(ConversationFolder.INBOX)
                        },
                        onContactsClick = onContactsClick,
                        width = bottomNavigationWidth,
                        height = bottomNavigationHeight,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = navigationBarPadding + 8.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = selectedThreads.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                selectedThreads.values.toList().takeIf { it.isNotEmpty() }?.let { threads ->
                    InboxSelectionActions(
                        threads = threads,
                        currentFolder = state.folder,
                        inProgress = state.threadActionInProgress,
                        optionsExpanded = selectionOptionsExpanded,
                        onOptionsExpandedChange = { selectionOptionsExpanded = it },
                        onDeleteRequest = {
                            selectionOptionsExpanded = false
                            selectedThreads = emptyMap()
                            viewModel.requestThreadDeletion(threads)
                        },
                        onAction = { action ->
                            selectionOptionsExpanded = false
                            selectedThreads = emptyMap()
                            viewModel.performThreadActions(
                                threads.mapTo(linkedSetOf(), ConversationThread::id),
                                action,
                            )
                        },
                        modifier = Modifier.padding(bottom = navigationBarPadding + 8.dp),
                    )
                }
            }
        }
    }

    when(categoryDialogStep) {
        CategoryDialogStep.NONE -> Unit
        CategoryDialogStep.NAME -> AlertDialog(
            onDismissRequest = { categoryDialogStep = CategoryDialogStep.NONE },
            title = { Text(stringResource(R.string.oneui_add_category)) },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it.take(40) },
                    label = { Text(stringResource(R.string.oneui_category_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("oneui-category-name"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { categoryDialogStep = CategoryDialogStep.MEMBERS },
                    enabled = categoryName.isNotBlank(),
                ) { Text(stringResource(R.string.oneui_add_category_action)) }
            },
            dismissButton = {
                TextButton(onClick = { categoryDialogStep = CategoryDialogStep.NONE }) {
                    Text(stringResource(R.string.oneui_cancel_category))
                }
            },
        )
        CategoryDialogStep.MEMBERS -> CategoryMemberSelectionDialog(
            threadsFlow = viewModel.allInboxThreads,
            selectedThreadIds = categoryMembers,
            inProgress = state.groupActionInProgress,
            onToggle = { threadId ->
                categoryMembers = if(threadId in categoryMembers) {
                    categoryMembers - threadId
                } else {
                    categoryMembers + threadId
                }
            },
            onDismiss = { categoryDialogStep = CategoryDialogStep.NONE },
            onDone = {
                managedCategoryId?.let { groupId ->
                    viewModel.updateConversationGroup(groupId, categoryName, categoryMembers)
                } ?: viewModel.createConversationGroup(categoryName, categoryMembers)
                managedCategoryId = null
                categoryDialogStep = CategoryDialogStep.NONE
            },
        )
    }

    when(categoryManagementStep) {
        CategoryManagementStep.NONE -> Unit
        CategoryManagementStep.OVERVIEW -> AlertDialog(
            onDismissRequest = { categoryManagementStep = CategoryManagementStep.NONE },
            title = { Text(stringResource(R.string.oneui_edit_categories)) },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    conversationGroups.forEach { group ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MessagesTheme.semanticColors.inboxSelectedControlSurface,
                        ) {
                            Text(
                                text = group.name,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = MessagesTheme.semanticColors.onInboxSelectedControl,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryManagementStep = CategoryManagementStep.MANAGE
                    },
                ) { Text(stringResource(R.string.oneui_additional_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryManagementStep = CategoryManagementStep.NONE },
                ) { Text(stringResource(R.string.oneui_done)) }
            },
        )
        CategoryManagementStep.MANAGE -> AlertDialog(
            onDismissRequest = { categoryManagementStep = CategoryManagementStep.NONE },
            title = { Text(stringResource(R.string.oneui_conversation_categories)) },
            text = {
                Column {
                    conversationGroups.forEach { group ->
                        val selected = managedCategoryId == group.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("oneui-category-manage-${group.id}")
                                .clickable { managedCategoryId = group.id }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.name, modifier = Modifier.weight(1f))
                            if(selected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        managedCategoryId?.let(viewModel::deleteConversationGroup)
                        managedCategoryId = null
                        categoryManagementStep = CategoryManagementStep.NONE
                    },
                    enabled = managedCategoryId != null && !state.groupActionInProgress,
                ) {
                    Text(
                        stringResource(R.string.oneui_delete_all_categories),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryManagementStep = CategoryManagementStep.NONE },
                ) { Text(stringResource(R.string.oneui_done)) }
            },
        )
    }

    state.pendingThreadDeletions.takeIf { it.isNotEmpty() }?.let { threads ->
        AlertDialog(
            onDismissRequest = viewModel::cancelThreadDeletion,
            title = { Text(stringResource(R.string.messages_thread_delete_confirmation_title)) },
            text = {
                Text(
                    stringResource(R.string.messages_thread_delete_confirmation_text) +
                        "\n\n" + threads.joinToString("\n") { thread ->
                            thread.displayName.ifBlank { thread.address }
                        }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.threadActionInProgress,
                    onClick = viewModel::confirmThreadDeletion,
                ) {
                    Text(
                        text = stringResource(R.string.messages_thread_delete_confirmation_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.threadActionInProgress,
                    onClick = viewModel::cancelThreadDeletion,
                ) {
                    Text(stringResource(R.string.messages_thread_delete_confirmation_cancel))
                }
            },
        )
    }
}

private enum class CategoryDialogStep { NONE, NAME, MEMBERS }
private enum class CategoryManagementStep { NONE, OVERVIEW, MANAGE }

private const val MAX_UNPADDED_INBOX_ROWS = 4
private const val FORWARD_FLING_HIDE_VELOCITY_DP = 1_200f
private const val REVERSE_FLING_EXPAND_VELOCITY_DP = 1_200f

internal fun shouldHideInboxChromeForFling(velocityYPx: Float, density: Float): Boolean =
    velocityYPx < -(FORWARD_FLING_HIDE_VELOCITY_DP * density)

internal fun shouldExpandInboxHeaderAfterReverseFling(
    velocityYPx: Float,
    density: Float,
    firstVisibleItemIndex: Int,
): Boolean = firstVisibleItemIndex == 0 &&
    velocityYPx > REVERSE_FLING_EXPAND_VELOCITY_DP * density

@Composable
private fun CategoryMemberSelectionDialog(
    threadsFlow: Flow<PagingData<ConversationThread>>,
    selectedThreadIds: Set<Int>,
    inProgress: Boolean,
    onToggle: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val threads = threadsFlow.collectAsLazyPagingItems()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("oneui-category-member-screen"),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 34.dp)
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                    AnimatedContent(
                        targetState = selectedThreadIds.size,
                        label = "category-member-selection-count",
                        modifier = Modifier.weight(1f),
                    ) { count ->
                        Text(
                            text = if(count == 0) {
                                stringResource(R.string.oneui_select_conversations)
                            } else {
                                stringResource(R.string.oneui_selection_count, count)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = if(selectedThreadIds.isEmpty()) onDismiss else onDone,
                        enabled = !inProgress,
                    ) {
                        Text(
                            text = if(selectedThreadIds.isEmpty()) {
                                stringResource(R.string.oneui_cancel_category)
                            } else {
                                stringResource(R.string.oneui_done)
                            },
                            color = MessagesTheme.semanticColors.inboxSelectionAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.oneui_select_conversations_description),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(
                        count = threads.itemCount,
                        key = threads.itemKey { it.id },
                    ) { index ->
                        val thread = threads[index] ?: return@items
                        val selected = thread.id in selectedThreadIds
                        val rowColor by animateColorAsState(
                            targetValue = if(selected) {
                                MessagesTheme.semanticColors.inboxSelectionSurface
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "category-member-${thread.id}-selection",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(rowColor)
                                .testTag("oneui-category-member-${thread.id}")
                                .clickable { onToggle(thread.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ContactAvatar(
                                    displayName = thread.displayName.ifBlank { thread.address },
                                    avatarUri = thread.avatarUri,
                                    modifier = Modifier.size(48.dp),
                                )
                                if(selected) {
                                    Surface(
                                        modifier = Modifier.size(48.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MessagesTheme.semanticColors.inboxSelectionAccent,
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.padding(12.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = thread.displayName.ifBlank { thread.address },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = thread.snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if(threads.loadState.append is LoadState.Loading) {
                        item(key = "category-members-append-loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxViewingZone(
    title: String,
    unreadMessageCount: Int,
    onViewUnread: () -> Unit,
    onDismissUnread: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MessagesTheme.dimensions.inboxViewingZoneHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = unreadMessageCount > 0,
            label = "inbox-unread-summary",
        ) { showUnread ->
            if(showUnread) {
                InboxUnreadSummary(
                    unreadMessageCount = unreadMessageCount,
                    onViewUnread = onViewUnread,
                    onDismiss = onDismissUnread,
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = MessagesTheme.spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun InboxUnreadSummary(
    unreadMessageCount: Int,
    onViewUnread: () -> Unit,
    onDismiss: () -> Unit,
) {
    val useLargeText = LocalDensity.current.fontScale >= 1.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if(useLargeText) 20.dp else 56.dp)
            .height(if(useLargeText) 196.dp else 152.dp)
            .testTag("oneui-inbox-unread-summary"),
        shape = RoundedCornerShape(32.dp),
        color = MessagesTheme.semanticColors.inboxControlSurface,
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.oneui_dismiss_unread_summary
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 28.dp,
                        top = if(useLargeText) 42.dp else 34.dp,
                        end = 28.dp,
                        bottom = 16.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.oneui_unread_messages,
                        unreadMessageCount,
                        unreadMessageCount,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onViewUnread) {
                    Text(
                        text = stringResource(R.string.oneui_view_unread),
                        color = MessagesTheme.semanticColors.inboxSelectionAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxSelectionViewingZone(selectionCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MessagesTheme.dimensions.inboxViewingZoneHeight)
            .testTag("oneui-inbox-selection-header"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.oneui_selection_count, selectionCount),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MessagesTheme.semanticColors.inboxSelectionAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = MessagesTheme.spacing.xl),
        )
    }
}

@Composable
private fun InboxSelectionControls(
    onCancel: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MessagesTheme.dimensions.inboxControlHeight)
            .padding(horizontal = MessagesTheme.dimensions.inboxOuterMargin)
            .testTag("oneui-inbox-selection-controls"),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.oneui_cancel),
                color = MessagesTheme.semanticColors.inboxSelectionAccent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.oneui_search),
                tint = MessagesTheme.semanticColors.inboxSelectionAccent,
            )
        }
    }
}

@Composable
private fun InboxControlIsland(
    selectedFolder: ConversationFolder,
    selectedFolderTitle: String,
    selectedGroupId: String?,
    groups: List<ConversationGroup>,
    capsuled: Boolean,
    filterExpanded: Boolean,
    overflowExpanded: Boolean,
    onSelectInbox: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onManageGroup: (String) -> Unit,
    onAddGroup: () -> Unit,
    onFilterClick: () -> Unit,
    onFilterDismiss: () -> Unit,
    onFolderSelected: (ConversationFolder) -> Unit,
    onSearchClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onExport: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val selectedSurface = MessagesTheme.semanticColors.inboxSelectedControlSurface
    val panelColor by animateColorAsState(
        targetValue = if(capsuled) MessagesTheme.semanticColors.inboxControlSurface
            else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "inbox-control-panel-color",
    )
    val panelShadow by animateDpAsState(
        targetValue = if(capsuled) 1.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "inbox-control-panel-shadow",
    )
    val selectedCategoryRequester = remember { BringIntoViewRequester() }
    val lastGroupSelected = groups.lastOrNull()?.id == selectedGroupId
    LaunchedEffect(selectedGroupId, selectedFolder, groups.size) {
        kotlinx.coroutines.delay(80)
        selectedCategoryRequester.bringIntoView()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.dimensions.inboxOuterMargin)
            .height(MessagesTheme.dimensions.inboxControlHeight)
            .testTag("oneui-inbox-controls"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(48.dp)
                .testTag(
                    if(capsuled) "oneui-inbox-category-capsule"
                    else "oneui-inbox-category-plain"
                ),
            shape = RoundedCornerShape(24.dp),
            color = panelColor,
            tonalElevation = 0.dp,
            shadowElevation = panelShadow,
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val allSelected = selectedFolder == ConversationFolder.INBOX &&
                    selectedGroupId == null
                val allControlColor by animateColorAsState(
                    targetValue = if(allSelected) selectedSurface
                        else androidx.compose.ui.graphics.Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "inbox-all-category-color",
                )
                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("oneui-inbox-folder-${selectedFolder.name.lowercase()}")
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if(allSelected) Modifier.bringIntoViewRequester(
                                selectedCategoryRequester
                            ) else Modifier
                        )
                        .clickable(onClick = onSelectInbox),
                    shape = RoundedCornerShape(20.dp),
                    color = allControlColor,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if(selectedFolder == ConversationFolder.INBOX) {
                                stringResource(R.string.oneui_all_messages)
                            } else {
                                selectedFolderTitle
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if(allSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if(allSelected) {
                                MessagesTheme.semanticColors.onInboxSelectedControl
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
                groups.forEach { group ->
                    val selected = selectedGroupId == group.id
                    val groupControlColor by animateColorAsState(
                        targetValue = if(selected) selectedSurface
                            else androidx.compose.ui.graphics.Color.Transparent,
                        animationSpec = tween(durationMillis = 180),
                        label = "inbox-category-${group.id}-color",
                    )
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .animateContentSize(tween(durationMillis = 180))
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if(selected && !lastGroupSelected) Modifier.bringIntoViewRequester(
                                    selectedCategoryRequester
                                ) else Modifier
                            )
                            .testTag("oneui-inbox-group-${group.id}")
                            .combinedClickable(
                                onClick = { onSelectGroup(group.id) },
                                onLongClick = { onManageGroup(group.id) },
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = groupControlColor,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if(selected) {
                                    MessagesTheme.semanticColors.onInboxSelectedControl
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onAddGroup,
                    modifier = Modifier
                        .then(
                            if(lastGroupSelected) Modifier.bringIntoViewRequester(
                                selectedCategoryRequester
                            ) else Modifier
                        )
                        .testTag("oneui-inbox-group-add"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.oneui_add_category),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .height(48.dp)
                .testTag(
                    if(capsuled) "oneui-inbox-actions-capsule"
                    else "oneui-inbox-actions-plain"
                ),
            shape = RoundedCornerShape(24.dp),
            color = panelColor,
            tonalElevation = 0.dp,
            shadowElevation = panelShadow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = onFilterClick) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.oneui_filter_folders),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OneUiFolderFilterDialog(
                        expanded = filterExpanded,
                        onDismissRequest = onFilterDismiss,
                        selectedFolder = selectedFolder,
                        onFolderSelected = onFolderSelected,
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.oneui_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = onOverflowClick) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.oneui_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OneUiPopupMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = onOverflowDismiss,
                        testTag = "oneui-popup-overflow",
                    ) {
                        OneUiPopupMenuItem(
                            text = stringResource(R.string.oneui_mark_all_read),
                            onClick = onMarkAllRead,
                        )
                        OneUiPopupMenuItem(
                            text = stringResource(R.string.conversation_menu_export),
                            onClick = onExport,
                        )
                        OneUiPopupMenuItem(
                            text = stringResource(R.string.settings),
                            onClick = onSettingsClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OneUiPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .width(282.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        containerColor = MessagesTheme.semanticColors.inboxControlSurface.copy(
            alpha = ONE_UI_POPUP_MENU_ALPHA,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}

@Composable
private fun OneUiPopupMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    )
}

@Composable
private fun OneUiFolderFilterDialog(
    expanded: Boolean,
    selectedFolder: ConversationFolder,
    onDismissRequest: () -> Unit,
    onFolderSelected: (ConversationFolder) -> Unit,
) {
    if(!expanded) return
    var pendingFolder by remember(selectedFolder) { mutableStateOf(selectedFolder) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val blockerInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("oneui-filter-sheet")
                    .clickable(
                        interactionSource = blockerInteraction,
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MessagesTheme.semanticColors.inboxControlSurface,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
            ) {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.oneui_filter_folders),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                    )
                    ConversationFolder.entries.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { pendingFolder = folder }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = pendingFolder == folder,
                                onClick = { pendingFolder = folder },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                            Text(
                                text = folderTitle(folder),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.oneui_cancel),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        TextButton(
                            onClick = { onFolderSelected(pendingFolder) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(android.R.string.ok),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddConversationsToCategoryRow(
    roundedTop: Boolean,
    onClick: () -> Unit,
) {
    val radius = MessagesTheme.dimensions.inboxSurfaceRadius
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.dimensions.inboxOuterMargin)
            .clip(
                RoundedCornerShape(
                    topStart = if(roundedTop) radius else 0.dp,
                    topEnd = if(roundedTop) radius else 0.dp,
                    bottomStart = radius,
                    bottomEnd = radius,
                )
            )
            .background(MessagesTheme.semanticColors.inboxListSurface)
            .clickable(onClick = onClick)
            .testTag("oneui-add-conversations-to-category")
            .heightIn(min = 80.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MessagesTheme.semanticColors.inboxSelectionAccent,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = stringResource(R.string.oneui_add_conversations),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InboxEmptySurface(
    loadState: LoadState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.dimensions.inboxOuterMargin)
            .height(420.dp)
            .clip(RoundedCornerShape(MessagesTheme.dimensions.inboxSurfaceRadius))
            .background(MessagesTheme.semanticColors.inboxListSurface)
            .testTag("oneui-inbox-list-surface"),
        contentAlignment = Alignment.Center,
    ) {
        when(loadState) {
            is LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OneUiEmptyState(
                    title = stringResource(R.string.oneui_import_failed),
                    description = loadState.error.localizedMessage
                        ?: stringResource(R.string.oneui_operation_failed),
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.oneui_retry))
                }
            }
            is LoadState.NotLoading -> OneUiEmptyState(
                title = stringResource(R.string.oneui_no_conversations),
                description = stringResource(R.string.oneui_no_conversations_description),
            )
        }
    }
}

@Composable
private fun InboxAppendStatus(
    appendState: LoadState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when(appendState) {
        is LoadState.Loading -> Surface(
            modifier = modifier.size(48.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MessagesTheme.semanticColors.inboxControlSurface.copy(alpha = 0.9f),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("oneui-inbox-append-loading"),
                )
            }
        }
        is LoadState.Error -> Surface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = MessagesTheme.semanticColors.inboxControlSurface.copy(alpha = 0.9f),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
        ) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag("oneui-inbox-append-retry"),
            ) {
                Text(stringResource(R.string.oneui_retry))
            }
        }
        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun InboxBottomNavigation(
    unreadMessageCount: Int,
    onConversationsClick: () -> Unit,
    onContactsClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val unreadBadgeDescription = if(unreadMessageCount > 0) {
        pluralStringResource(
            R.plurals.oneui_unread_messages,
            unreadMessageCount,
            unreadMessageCount,
        )
    } else {
        ""
    }
    Surface(
        modifier = modifier
            .width(width)
            .height(height)
            .testTag("oneui-inbox-bottom-navigation"),
        shape = RoundedCornerShape(height / 2),
        color = MessagesTheme.semanticColors.inboxControlSurface,
        shadowElevation = 5.dp,
    ) {
        Row(Modifier.padding(3.dp)) {
            InboxNavigationItem(
                selected = true,
                testTag = "oneui-inbox-conversations",
                label = stringResource(R.string.oneui_conversations_navigation),
                icon = {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("oneui-inbox-conversations-icon"),
                        )
                        if(unreadMessageCount > 0) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-5).dp)
                                    .height(17.dp)
                                    .widthIn(min = 17.dp)
                                    .testTag("oneui-inbox-unread-badge")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = unreadBadgeDescription
                                    },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MessagesTheme.semanticColors.unreadBadge,
                                contentColor = MessagesTheme.semanticColors.onUnreadBadge,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if(unreadMessageCount > 99) "99+"
                                            else unreadMessageCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                },
                onClick = onConversationsClick,
                modifier = Modifier.weight(1f),
            )
            InboxNavigationItem(
                selected = false,
                testTag = "oneui-inbox-contacts",
                label = stringResource(R.string.oneui_contacts_navigation),
                icon = { Icon(Icons.Outlined.PersonOutline, contentDescription = null) },
                onClick = onContactsClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InboxNavigationItem(
    selected: Boolean,
    testTag: String,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if(selected) MessagesTheme.semanticColors.inboxSelectedControlSurface
            else androidx.compose.ui.graphics.Color.Transparent,
        label = "inbox-navigation-selection",
    )
    val contentColor by animateColorAsState(
        targetValue = if(selected) MessagesTheme.semanticColors.onInboxSelectedControl
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "inbox-navigation-content",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(25.dp))
            .background(backgroundColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(testTag)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    thread: ConversationThread,
    roundedTop: Boolean,
    roundedBottom: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val surfaceShape = RoundedCornerShape(
        topStart = if(roundedTop) MessagesTheme.dimensions.inboxSurfaceRadius else 0.dp,
        topEnd = if(roundedTop) MessagesTheme.dimensions.inboxSurfaceRadius else 0.dp,
        bottomStart = if(roundedBottom) MessagesTheme.dimensions.inboxSurfaceRadius else 0.dp,
        bottomEnd = if(roundedBottom) MessagesTheme.dimensions.inboxSurfaceRadius else 0.dp,
    )
    val rowColor by animateColorAsState(
        targetValue = if(selected) MessagesTheme.semanticColors.inboxSelectionSurface
            else MessagesTheme.semanticColors.inboxListSurface,
        label = "conversation-selection",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.dimensions.inboxOuterMargin)
            .clip(surfaceShape)
            .background(
                color = MessagesTheme.semanticColors.inboxListSurface,
                shape = surfaceShape,
            )
            .then(if(roundedTop) Modifier.testTag("oneui-inbox-list-surface") else Modifier)
            .then(if(roundedBottom) Modifier.testTag("oneui-inbox-list-bottom") else Modifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("oneui-thread-row-${thread.id}"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MessagesTheme.dimensions.inboxRowMinimumHeight)
                    .background(rowColor)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .then(
                        if(selected) Modifier.testTag("oneui-inbox-selected-${thread.id}")
                        else Modifier
                    )
                    .padding(start = 20.dp, top = 13.dp, end = 24.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(MessagesTheme.dimensions.inboxAvatarSize)) {
                    ContactAvatar(
                        displayName = thread.displayName,
                        avatarUri = thread.avatarUri,
                        modifier = Modifier.fillMaxSize(),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if(selected) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MessagesTheme.semanticColors.inboxSelectionAccent,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(
                                        R.string.oneui_conversation_selected
                                    ),
                                    tint = MessagesTheme.semanticColors.onInboxSelectionAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = thread.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if(thread.unreadCount > 0) FontWeight.Bold
                                else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if(thread.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if(thread.isMuted) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = stringResource(R.string.oneui_muted),
                                modifier = Modifier
                                    .padding(start = MessagesTheme.spacing.xxs, top = 2.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatConversationTimestamp(thread.timestampMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = MessagesTheme.spacing.xs, top = 1.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = thread.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if(thread.unreadCount > 0) {
                            Surface(
                                modifier = Modifier.padding(start = MessagesTheme.spacing.xs),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MessagesTheme.semanticColors.unreadBadge,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 20.dp)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if(thread.unreadCount > 99) "99+"
                                            else thread.unreadCount.toString(),
                                        color = MessagesTheme.semanticColors.onUnreadBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if(!roundedBottom) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp, end = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun formatConversationTimestamp(timestampMillis: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val flags = if(DateUtils.isToday(timestampMillis)) {
        DateUtils.FORMAT_SHOW_TIME
    } else {
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    }
    return DateUtils.formatDateTime(context, timestampMillis, flags)
}

@Composable
private fun InboxSelectionActions(
    threads: List<ConversationThread>,
    currentFolder: ConversationFolder,
    inProgress: Boolean,
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
    onAction: (ConversationThreadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allMuted = threads.all(ConversationThread::isMuted)
    val allPinned = threads.all(ConversationThread::isPinned)
    val allArchived = currentFolder == ConversationFolder.ARCHIVED ||
        threads.all(ConversationThread::isArchived)
    val useLargeText = LocalDensity.current.fontScale >= 1.5f
    Surface(
        modifier = modifier
            .width(if(useLargeText) 344.dp else 252.dp)
            .height(if(useLargeText) 80.dp else 64.dp)
            .testTag("oneui-inbox-selection-actions"),
        shape = RoundedCornerShape(if(useLargeText) 40.dp else 32.dp),
        color = MessagesTheme.semanticColors.inboxControlSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InboxSelectionActionItem(
                label = if(useLargeText) {
                    stringResource(R.string.oneui_notifications_compact)
                } else {
                    stringResource(
                        if(allMuted) R.string.oneui_unmute else R.string.notifications
                    )
                },
                enabled = !inProgress,
                onClick = {
                    onAction(
                        if(allMuted) ConversationThreadAction.UNMUTE
                        else ConversationThreadAction.MUTE
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if(allMuted) Icons.Default.Notifications
                    else Icons.Default.NotificationsOff,
                    contentDescription = null,
                )
            }
            InboxSelectionActionItem(
                label = stringResource(R.string.message_threads_menu_delete),
                enabled = !inProgress,
                onClick = onDeleteRequest,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
            Box(Modifier.weight(1f)) {
                InboxSelectionActionItem(
                    label = stringResource(R.string.oneui_options),
                    enabled = !inProgress,
                    onClick = { onOptionsExpandedChange(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("oneui-inbox-selection-more"),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                OneUiPopupMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { onOptionsExpandedChange(false) },
                    testTag = "oneui-popup-selection",
                ) {
                    OneUiPopupMenuItem(
                        text = stringResource(
                            if(allPinned) R.string.oneui_unpin else R.string.oneui_pin
                        ),
                        onClick = {
                            onAction(
                                if(allPinned) ConversationThreadAction.UNPIN
                                else ConversationThreadAction.PIN
                            )
                        },
                    )
                    OneUiPopupMenuItem(
                        text = stringResource(
                            if(allArchived) R.string.oneui_unarchive else R.string.oneui_archive
                        ),
                        onClick = {
                            onAction(
                                if(allArchived) ConversationThreadAction.UNARCHIVE
                                else ConversationThreadAction.ARCHIVE
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InboxSelectionActionItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun folderTitle(folder: ConversationFolder): String = stringResource(
    when(folder) {
        ConversationFolder.INBOX -> R.string.oneui_messages_title
        ConversationFolder.ARCHIVED -> R.string.oneui_archived
        ConversationFolder.DRAFTS -> R.string.oneui_drafts
        ConversationFolder.MUTED -> R.string.oneui_muted
        ConversationFolder.BLOCKED -> R.string.oneui_blocked
        ConversationFolder.UNREAD -> R.string.oneui_unread
    }
)

@Composable
private fun FolderIcon(folder: ConversationFolder) {
    val icon = when(folder) {
        ConversationFolder.INBOX -> Icons.Default.Inbox
        ConversationFolder.ARCHIVED -> Icons.Default.Archive
        ConversationFolder.DRAFTS -> Icons.Default.Drafts
        ConversationFolder.MUTED -> Icons.Default.NotificationsOff
        ConversationFolder.BLOCKED -> Icons.Default.Block
        ConversationFolder.UNREAD -> Icons.Default.Notifications
    }
    Icon(icon, contentDescription = null)
}
