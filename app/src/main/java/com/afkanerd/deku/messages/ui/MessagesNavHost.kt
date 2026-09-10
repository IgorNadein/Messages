package com.afkanerd.deku.messages.ui

import android.app.role.RoleManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.provider.Telephony
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.DefaultSMS.BuildConfig
import com.afkanerd.deku.AboutScreen
import com.afkanerd.deku.GatewayClientsListScreen
import com.afkanerd.deku.RemoteForwardingScreen
import com.afkanerd.deku.RemoteListenersScreen
import com.afkanerd.deku.UpdatesScreenRoute
import com.afkanerd.deku.messages.presentation.ConversationViewModel
import com.afkanerd.deku.messages.presentation.ConversationViewModelFactory
import com.afkanerd.deku.messages.presentation.ContactDetailsViewModel
import com.afkanerd.deku.messages.presentation.ContactDetailsViewModelFactory
import com.afkanerd.deku.messages.presentation.InboxViewModel
import com.afkanerd.deku.messages.presentation.InboxViewModelFactory
import com.afkanerd.deku.messages.presentation.SettingsViewModel
import com.afkanerd.deku.messages.presentation.SettingsViewModelFactory
import com.afkanerd.deku.messages.presentation.MessagesSearchViewModel
import com.afkanerd.deku.messages.presentation.MessagesSearchViewModelFactory
import com.afkanerd.deku.messages.presentation.NewMessageViewModel
import com.afkanerd.deku.messages.presentation.NewMessageViewModelFactory
import com.afkanerd.deku.messages.presentation.GatewayViewModel
import com.afkanerd.deku.messages.presentation.GatewayViewModelFactory
import com.afkanerd.deku.messages.presentation.DeveloperToolsViewModel
import com.afkanerd.deku.messages.presentation.DeveloperToolsViewModelFactory
import com.afkanerd.deku.messages.presentation.MediaViewerViewModel
import com.afkanerd.deku.messages.presentation.MediaViewerViewModelFactory
import com.afkanerd.deku.messages.presentation.RemoteListenersViewModel
import com.afkanerd.deku.messages.presentation.RemoteListenersViewModelFactory
import com.afkanerd.deku.messages.presentation.RoutingHistoryViewModel
import com.afkanerd.deku.messages.presentation.RoutingHistoryViewModelFactory
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ComposeNewMessageScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ContactDetailsScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ConversationsScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.DeveloperModeScreen
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.HomeScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ImageViewScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.SearchScreenNav
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.SettingsScreenNav

@Composable
fun MessagesNavHost(
    navController: NavHostController,
    messageService: MessageService,
    appSettingsService: AppSettingsService,
    developerToolsService: DeveloperToolsService,
    extraRoutes: NavGraphBuilder.() -> Unit = {},
) {
    NavHost(navController = navController, startDestination = HomeScreenNav()) {
        extraRoutes()

        composable<HomeScreenNav> {
            InboxRoute(
                messageService = messageService,
                onConversationClick = { thread ->
                    navController.navigate(
                        ConversationsScreenNav(
                            address = thread.address,
                            threadId = thread.id,
                        )
                    )
                },
                onSearchClick = { navController.navigate(SearchScreenNav()) },
                onSettingsClick = { navController.navigate(SettingsScreenNav) },
                onNewMessageClick = { navController.navigate(ComposeNewMessageScreenNav()) },
                onContactsClick = { navController.navigate(ComposeNewMessageScreenNav()) },
            )
        }

        composable<ConversationsScreenNav> { entry ->
            val route: ConversationsScreenNav = entry.toRoute()
            val conversationViewModel: ConversationViewModel = viewModel(
                key = "oneui-conversation-${route.address}-${route.threadId}",
                factory = ConversationViewModelFactory(
                    messageService = messageService,
                    address = route.address,
                    threadId = route.threadId,
                    initialText = route.text,
                ),
            )
            val context = LocalContext.current
            val conversation: @Composable () -> Unit = {
                ConversationScreen(
                    viewModel = conversationViewModel,
                    onBack = { navController.popBackStack() },
                    onCall = { address ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$address".toUri()))
                        }
                    },
                    onVideoCall = { address ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, "tel:$address".toUri()).putExtra(
                                    TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                                    VideoProfile.STATE_BIDIRECTIONAL,
                                )
                            )
                        }
                    },
                    onMore = {
                        val activeHeader = conversationViewModel.state.value.header
                        navController.navigate(
                            ContactDetailsScreenNav(
                                address = activeHeader?.address ?: route.address,
                                encryptionAvailable = false,
                                subscriptionId = activeHeader?.subscriptionId?.toInt() ?: -1,
                                threadId = activeHeader?.threadId ?: route.threadId,
                            )
                        )
                    },
                    onAddRecipients = { participants ->
                        navController.navigate(
                            ComposeNewMessageScreenNav(
                                subscriptionId = conversationViewModel.state.value.header?.subscriptionId,
                                initialAddresses = participants.joinToString(","),
                            )
                        )
                    },
                    onCopyMessage = { text ->
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("message", text))
                    },
                    onForwardMessage = { text ->
                        navController.navigate(ComposeNewMessageScreenNav(text = text))
                    },
                    onShareMessage = { item ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            when(item) {
                                is TimelineItem.Text -> {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, item.text)
                                }
                                is TimelineItem.Media -> {
                                    val contentUri = item.uri?.toUri()
                                    if(contentUri != null) {
                                        type = item.mimeType?.ifBlank { "application/octet-stream" }
                                            ?: "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, item.caption.orEmpty())
                                    }
                                }
                                is TimelineItem.SecurityEvent -> type = "text/plain"
                            }
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        }
                    },
                    onOpenMedia = { media ->
                        val activeAddress = conversationViewModel.state.value.header?.address
                            ?: route.address
                        mediaViewerDestination(
                            item = media,
                            address = activeAddress,
                            formattedDate = DateUtils.formatDateTime(
                                context,
                                media.timestampMillis,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                            ),
                        )?.let(navController::navigate)
                    },
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if(useTwoPaneMessagesLayout(maxWidth.value.toInt())) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(TWO_PANE_LIST_WEIGHT)) {
                            InboxRoute(
                                messageService = messageService,
                                onConversationClick = { thread ->
                                    navController.navigate(
                                        ConversationsScreenNav(
                                            address = thread.address,
                                            threadId = thread.id,
                                        )
                                    ) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                },
                                onSearchClick = { navController.navigate(SearchScreenNav()) },
                                onSettingsClick = { navController.navigate(SettingsScreenNav) },
                                onNewMessageClick = {
                                    navController.navigate(ComposeNewMessageScreenNav())
                                },
                                onContactsClick = {
                                    navController.navigate(ComposeNewMessageScreenNav())
                                },
                            )
                        }
                        VerticalDivider()
                        Box(Modifier.weight(TWO_PANE_CONVERSATION_WEIGHT)) { conversation() }
                    }
                } else {
                    conversation()
                }
            }
        }

        composable<SearchScreenNav> { entry ->
            val route: SearchScreenNav = entry.toRoute()
            val searchViewModel: MessagesSearchViewModel = viewModel(
                key = "oneui-search-${route.address.orEmpty()}",
                factory = MessagesSearchViewModelFactory(messageService, route.address),
            )
            MessagesSearchScreen(
                viewModel = searchViewModel,
                onBack = { navController.popBackStack() },
                onConversationClick = { thread, query ->
                    navController.navigate(
                        ConversationsScreenNav(
                            address = thread.address,
                            threadId = thread.id,
                            query = query,
                        )
                    )
                },
            )
        }
        composable<ContactDetailsScreenNav> { entry ->
            val route: ContactDetailsScreenNav = entry.toRoute()
            val contactDetailsViewModel: ContactDetailsViewModel = viewModel(
                key = "oneui-contact-${route.address}-${route.threadId}",
                factory = ContactDetailsViewModelFactory(
                    messageService,
                    route.address,
                    route.threadId,
                ),
            )
            val context = LocalContext.current
            ContactDetailsScreen(
                viewModel = contactDetailsViewModel,
                onBack = { navController.popBackStack() },
                onCall = { address ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$address".toUri()))
                    }
                },
                onOpenContact = { address ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, address)
                            }
                        )
                    }
                },
                onSearch = { address -> navController.navigate(SearchScreenNav(address = address)) },
                onCopyNumber = { address ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("phone number", address))
                },
                onEditParticipants = { participants ->
                    navController.navigate(
                        ComposeNewMessageScreenNav(
                            subscriptionId = contactDetailsViewModel.state.value.header?.subscriptionId,
                            initialAddresses = participants.joinToString(","),
                        )
                    )
                },
            )
        }
        composable<ComposeNewMessageScreenNav> { entry ->
            val route: ComposeNewMessageScreenNav = entry.toRoute()
            val newMessageViewModel: NewMessageViewModel = viewModel(
                key = "oneui-new-message-${route.text.hashCode()}-${route.subscriptionId}-${route.initialAddresses.hashCode()}",
                factory = NewMessageViewModelFactory(
                    messageService = messageService,
                    initialText = route.text,
                    initialSubscriptionId = route.subscriptionId,
                    initialAddresses = route.initialAddresses,
                ),
            )
            NewMessageScreen(
                viewModel = newMessageViewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { destination ->
                    navController.navigate(
                        ConversationsScreenNav(
                            address = destination.address,
                            threadId = destination.threadId,
                            text = destination.preservedText,
                        )
                    ) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
            )
        }
        composable<SettingsScreenNav> {
            val settingsViewModel: SettingsViewModel = viewModel(
                key = "oneui-settings",
                factory = SettingsViewModelFactory(appSettingsService),
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onDeveloperOptions = { navController.navigate(DeveloperModeScreen) },
                onRemoteListeners = { navController.navigate(RemoteListenersScreen) },
                onGatewayClients = { navController.navigate(GatewayClientsListScreen) },
                onRoutingHistory = { navController.navigate(RemoteForwardingScreen) },
                onUpdates = { navController.navigate(UpdatesScreenRoute) },
                onAbout = { navController.navigate(AboutScreen) },
            )
        }
        composable<DeveloperModeScreen> {
            val developerToolsViewModel: DeveloperToolsViewModel = viewModel(
                key = "oneui-developer-tools",
                factory = DeveloperToolsViewModelFactory(developerToolsService),
            )
            DeveloperToolsScreen(
                viewModel = developerToolsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<GatewayClientsListScreen> {
            val gatewayViewModel: GatewayViewModel = viewModel(
                key = "oneui-gateway-clients",
                factory = GatewayViewModelFactory(messageService),
            )
            GatewayScreen(
                viewModel = gatewayViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<RemoteListenersScreen> {
            val remoteListenersViewModel: RemoteListenersViewModel = viewModel(
                key = "oneui-remote-listeners",
                factory = RemoteListenersViewModelFactory(messageService),
            )
            RemoteListenersOneUiScreen(
                viewModel = remoteListenersViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<RemoteForwardingScreen> {
            val routingHistoryViewModel: RoutingHistoryViewModel = viewModel(
                key = "oneui-routing-history",
                factory = RoutingHistoryViewModelFactory(messageService),
            )
            RoutingHistoryScreen(
                viewModel = routingHistoryViewModel,
                onBack = { navController.popBackStack() },
                onGatewayClients = { navController.navigate(GatewayClientsListScreen) },
            )
        }
        composable<AboutScreen> {
            val context = LocalContext.current
            AboutOneUiScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBack = { navController.popBackStack() },
                onOpenSource = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                },
            )
        }
        composable<UpdatesScreenRoute> {
            UpdatesScreen(onBack = { navController.popBackStack() })
        }
        composable<ImageViewScreenNav> { entry ->
            val route: ImageViewScreenNav = entry.toRoute()
            val mediaViewerViewModel: MediaViewerViewModel = viewModel(
                key = "oneui-media-${route.contentUri.hashCode()}",
                factory = MediaViewerViewModelFactory(messageService, route.contentUri),
            )
            val context = LocalContext.current
            MediaViewerScreen(
                contentUri = route.contentUri,
                address = route.address,
                date = route.date,
                filename = route.filename,
                mimeType = route.mimeType,
                viewModel = mediaViewerViewModel,
                onBack = { navController.popBackStack() },
                onShare = { uri, type ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    this.type = type.ifBlank { "application/octet-stream" }
                                    putExtra(Intent.EXTRA_STREAM, uri.toUri())
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                null,
                            )
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun InboxRoute(
    messageService: MessageService,
    onConversationClick: (com.afkanerd.deku.messages.domain.ConversationThread) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewMessageClick: () -> Unit,
    onContactsClick: () -> Unit,
) {
    val inboxViewModel: InboxViewModel = viewModel(
        key = "oneui-inbox",
        factory = InboxViewModelFactory(messageService),
    )
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { inboxViewModel.onResume() }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { inboxViewModel.completeContactsPrompt() }
    InboxScreen(
        viewModel = inboxViewModel,
        onRequestDefaultSmsRole = {
            roleLauncher.launch(context.defaultSmsRoleIntent())
        },
        onRequestContacts = {
            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        },
        onConversationClick = onConversationClick,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onNewMessageClick = onNewMessageClick,
        onContactsClick = onContactsClick,
    )
}

internal fun useTwoPaneMessagesLayout(widthDp: Int): Boolean =
    widthDp >= TWO_PANE_MIN_WIDTH_DP

private const val TWO_PANE_MIN_WIDTH_DP = 840
private const val TWO_PANE_LIST_WEIGHT = 0.42f
private const val TWO_PANE_CONVERSATION_WEIGHT = 0.58f

private fun Context.defaultSmsRoleIntent(): Intent {
    return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
    }
}
