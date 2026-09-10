package com.afkanerd.deku.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.DefaultSMS.BuildConfig
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.MediaTransport
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.deku.messages.domain.SimTransportSettings
import com.afkanerd.deku.messages.presentation.SettingsViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme
import com.afkanerd.deku.attachments.cloud.CloudProvider

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onDeveloperOptions: () -> Unit,
    onRemoteListeners: () -> Unit,
    onGatewayClients: () -> Unit,
    onRoutingHistory: () -> Unit,
    onAbout: () -> Unit,
    onUpdates: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    val listStates = remember {
        SettingsPage.entries.associateWith { LazyListState() }
    }
    val listState = listStates.getValue(page)
    val density = LocalDensity.current
    val titleCollapseDistancePx = with(density) {
        MessagesTheme.spacing.viewingZone.roundToPx().coerceAtLeast(1)
    }
    val titleCollapseProgress by remember(listState, titleCollapseDistancePx) {
        derivedStateOf {
            if(listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset.toFloat() / titleCollapseDistancePx)
                .coerceIn(0f, 1f)
        }
    }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var transportTarget by remember { mutableStateOf<TransportTarget?>(null) }
    var pendingSecureTransportTarget by remember { mutableStateOf<TransportTarget?>(null) }
    var pendingMediaTransport by remember { mutableStateOf<PendingMediaTransport?>(null) }
    var showCloudSetup by remember { mutableStateOf(false) }
    var cloudSetupError by remember { mutableStateOf<String?>(null) }
    var cloudSelectionTarget by remember { mutableStateOf<TransportTarget?>(null) }
    var cloudSetupSaving by remember { mutableStateOf(false) }
    val transportTargets = state.simTransportSettings.map(::TransportTarget).ifEmpty {
        listOf(TransportTarget.fromLegacy(state))
    }
    var selectedSubscriptionId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(transportTargets.map(TransportTarget::subscriptionId)) {
        if(transportTargets.none { it.subscriptionId == selectedSubscriptionId }) {
            selectedSubscriptionId = transportTargets.firstOrNull()?.subscriptionId
        }
    }
    val selectedTransportTarget = transportTargets.firstOrNull {
        it.subscriptionId == selectedSubscriptionId
    } ?: transportTargets.first()
    val screenTitle = stringResource(page.titleResource)
    val navigateBack = {
        if(page == SettingsPage.ROOT) onBack() else page = SettingsPage.ROOT
    }

    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = screenTitle,
                showTitle = titleCollapseProgress >= COMPACT_TITLE_REVEAL_PROGRESS,
                titleModifier = Modifier.testTag("settings-compact-title"),
                navigation = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.oneui_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .testTag("settings-list"),
            contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        ) {
            item {
                OneUiExpandedTitle(
                    title = screenTitle,
                    modifier = Modifier.alpha(1f - titleCollapseProgress),
                )
            }
            when(page) {
                SettingsPage.ROOT -> {
                    item {
                        SettingsCard {
                            SettingsNavigationRow(
                                title = stringResource(R.string.oneui_settings_appearance),
                                description = stringResource(
                                    R.string.oneui_settings_appearance_summary,
                                    state.themeMode.displayName(),
                                    state.languageName,
                                ),
                                modifier = Modifier.testTag("settings-section-appearance"),
                                onClick = { page = SettingsPage.APPEARANCE },
                            )
                            SettingsDivider()
                            SettingsNavigationRow(
                                title = stringResource(R.string.oneui_settings_transport),
                                description = if(state.simTransportSettings.size > 1) {
                                    stringResource(R.string.oneui_settings_transport_multi_sim_summary)
                                } else {
                                    stringResource(R.string.oneui_settings_transport_summary)
                                },
                                modifier = Modifier.testTag("settings-section-transport"),
                                onClick = { page = SettingsPage.TRANSPORT },
                            )
                            SettingsDivider()
                            SettingsNavigationRow(
                                title = stringResource(R.string.oneui_settings_storage),
                                description = stringResource(R.string.oneui_settings_storage_summary),
                                modifier = Modifier.testTag("settings-section-storage"),
                                onClick = { page = SettingsPage.STORAGE },
                            )
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_preferences)) }
                    item {
                        SettingsCard {
                            SettingsNavigationRow(
                                title = stringResource(R.string.oneui_settings_notifications_and_behavior),
                                description = stringResource(
                                    R.string.oneui_settings_notifications_and_behavior_summary,
                                ),
                                modifier = Modifier.testTag("settings-section-behavior"),
                                onClick = { page = SettingsPage.BEHAVIOR },
                            )
                            SettingsDivider()
                            SettingsNavigationRow(
                                title = stringResource(R.string.oneui_advanced),
                                description = stringResource(R.string.oneui_settings_advanced_summary),
                                modifier = Modifier.testTag("settings-section-advanced"),
                                onClick = { page = SettingsPage.ADVANCED },
                            )
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_about)) }
                    item {
                        SettingsCard {
                            SettingsValueRow(
                                title = stringResource(R.string.updates_title),
                                description = stringResource(R.string.updates_settings_summary),
                                modifier = Modifier.testTag("settings-updates"),
                                onClick = onUpdates,
                            )
                            SettingsDivider()
                            SettingsValueRow(
                                title = stringResource(R.string.oneui_about_messages),
                                description = stringResource(
                                    R.string.oneui_version_value,
                                    BuildConfig.VERSION_NAME,
                                ),
                                onClick = onAbout,
                            )
                        }
                    }
                }

                SettingsPage.APPEARANCE -> {
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_appearance)) }
                    item {
                        SettingsCard {
                            SettingsValueRow(
                                title = stringResource(R.string.theme),
                                description = state.themeMode.displayName(),
                                onClick = { dialog = SettingsDialog.THEME },
                            )
                            SettingsDivider()
                            SettingsValueRow(
                                title = stringResource(R.string.language),
                                description = state.languageName,
                                onClick = { dialog = SettingsDialog.LANGUAGE },
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title = stringResource(R.string.enable_24_hours_time_format),
                                description = stringResource(
                                    R.string.when_turned_off_would_use_your_system_s_default_time_format,
                                ),
                                checked = state.use24HourTime,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.USE_24_HOUR_TIME, it)
                                },
                            )
                        }
                    }
                }

                SettingsPage.TRANSPORT -> {
                    if(transportTargets.size > 1) {
                        item { SettingsSectionTitle(stringResource(R.string.oneui_settings_sim_card)) }
                        item {
                            SimTransportSelector(
                                targets = transportTargets,
                                selectedSubscriptionId = selectedTransportTarget.subscriptionId,
                                onSelected = { selectedSubscriptionId = it.subscriptionId },
                            )
                        }
                    } else {
                        selectedTransportTarget.sim?.let { sim ->
                            item {
                                SettingsSectionTitle(
                                    stringResource(
                                        R.string.oneui_transport_sim_label,
                                        sim.displayName,
                                        sim.slotIndex + 1,
                                    )
                                )
                            }
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_outgoing_transport)) }
                    item {
                        SettingsCard {
                            val target = selectedTransportTarget
                            SettingsValueRow(
                                title = stringResource(R.string.oneui_secure_message_transport),
                                description = target.secureMessageTransport.displayName(),
                                modifier = Modifier.testTag(
                                    "settings-secure-transport-${target.testId}",
                                ),
                                onClick = {
                                    transportTarget = target
                                    dialog = SettingsDialog.SECURE_TRANSPORT
                                },
                            )
                            SettingsDivider()
                            SettingsValueRow(
                                title = stringResource(R.string.oneui_media_transport),
                                description = target.mediaTransport.displayName(),
                                modifier = Modifier.testTag(
                                    "settings-media-transport-${target.testId}",
                                ),
                                onClick = {
                                    transportTarget = target
                                    dialog = SettingsDialog.MEDIA_TRANSPORT
                                },
                            )
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_internet_storage)) }
                    item {
                        SettingsCard {
                            SettingsValueRow(
                                title = stringResource(R.string.oneui_cloud_storage_title),
                                description = stringResource(
                                    if(state.cloudStorageConfigured) {
                                        R.string.oneui_cloud_storage_configured
                                    } else {
                                        R.string.oneui_cloud_storage_not_configured
                                    },
                                ),
                                onClick = {
                                    cloudSelectionTarget = null
                                    showCloudSetup = true
                                },
                            )
                        }
                    }
                    item {
                        SettingsFootnote(stringResource(R.string.oneui_settings_receive_transport_note))
                    }
                }

                SettingsPage.STORAGE -> {
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_system_database)) }
                    item {
                        SettingsCard {
                            SettingsToggleRow(
                                title = stringResource(R.string.save_messages_to_system_s_database),
                                description = stringResource(
                                    R.string.other_messaging_apps_would_have_access_to_the_system_s_database,
                                ),
                                checked = state.storeInSystemDatabase,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.STORE_IN_SYSTEM_DATABASE, it)
                                },
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                title = stringResource(R.string.delete_messages_from_system_s_database),
                                description = stringResource(
                                    R.string.this_would_delete_messages_from_system_s_database_accessible_by_other_apps,
                                ),
                                checked = state.deleteFromSystemDatabase,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.DELETE_FROM_SYSTEM_DATABASE, it)
                                },
                            )
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_archive)) }
                    item {
                        SettingsCard {
                            SettingsToggleRow(
                                title = stringResource(R.string.keep_messages_archived),
                                description = stringResource(
                                    R.string.messages_remain_in_archive_even_when_new_ones_are_sent_or_received,
                                ),
                                checked = state.keepArchived,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.KEEP_ARCHIVED, it)
                                },
                            )
                        }
                    }
                }

                SettingsPage.BEHAVIOR -> {
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_delivery)) }
                    item {
                        SettingsCard {
                            SettingsToggleRow(
                                title = stringResource(R.string.get_sms_delivery_reports),
                                description = stringResource(
                                    R.string.find_out_when_an_sms_message_is_delivered,
                                ),
                                checked = state.deliveryReports,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.DELIVERY_REPORTS, it)
                                },
                            )
                        }
                    }
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_notifications)) }
                    item {
                        SettingsCard {
                            SettingsToggleRow(
                                title = stringResource(R.string.enable_notification_context_replies),
                                description = stringResource(
                                    R.string.your_phone_would_suggest_smart_replies_in_notifications_if_enabled,
                                ),
                                checked = state.contextReplies,
                                onCheckedChange = {
                                    viewModel.setBoolean(BooleanSetting.CONTEXT_REPLIES, it)
                                },
                            )
                        }
                    }
                }

                SettingsPage.ADVANCED -> {
                    item { SettingsSectionTitle(stringResource(R.string.oneui_settings_gateway_tools)) }
                    item {
                        SettingsCard {
                            SettingsActionRow(
                                title = stringResource(R.string.remote_listeners),
                                description = stringResource(R.string.oneui_remote_listeners_description),
                                onClick = onRemoteListeners,
                            )
                            SettingsDivider()
                            SettingsActionRow(
                                title = stringResource(R.string.gateway_clients),
                                description = stringResource(R.string.oneui_gateway_clients_description),
                                onClick = onGatewayClients,
                            )
                            SettingsDivider()
                            SettingsActionRow(
                                title = stringResource(R.string.settings_SMS_routing_title),
                                description = stringResource(R.string.oneui_routing_history_description),
                                onClick = onRoutingHistory,
                            )
                        }
                    }
                    if(BuildConfig.DEBUG) {
                        item { SettingsSectionTitle(stringResource(R.string.developer_options)) }
                        item {
                            SettingsCard {
                                SettingsActionRow(
                                    title = stringResource(R.string.developer_options),
                                    description = stringResource(R.string.oneui_developer_description),
                                    onClick = onDeveloperOptions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when(dialog) {
        SettingsDialog.THEME -> ThemeDialog(
            selected = state.themeMode,
            onDismiss = { dialog = null },
            onSelected = {
                dialog = null
                viewModel.setTheme(it)
            },
        )
        SettingsDialog.LANGUAGE -> LanguageDialog(
            state = state,
            onDismiss = { dialog = null },
            onSelected = {
                dialog = null
                viewModel.setLanguage(it.tag)
            },
        )
        SettingsDialog.SECURE_TRANSPORT -> SecureTransportDialog(
            selected = transportTarget?.secureMessageTransport
                ?: state.secureMessageTransport,
            onDismiss = {
                dialog = null
                transportTarget = null
            },
            onSelected = { transport ->
                val target = transportTarget ?: TransportTarget.fromLegacy(state)
                dialog = null
                if(transport == SecureMessageTransport.DATA_SMS) {
                    pendingSecureTransportTarget = target
                } else {
                    viewModel.setSecureMessageTransport(target, transport)
                }
                transportTarget = null
            },
        )
        SettingsDialog.MEDIA_TRANSPORT -> MediaTransportDialog(
            selected = transportTarget?.mediaTransport ?: state.mediaTransport,
            cloudStorageConfigured = state.cloudStorageConfigured,
            onDismiss = {
                dialog = null
                transportTarget = null
            },
            onSelected = { transport ->
                val target = transportTarget ?: TransportTarget.fromLegacy(state)
                dialog = null
                if(transport == MediaTransport.MMS) {
                    viewModel.setMediaTransport(target, transport)
                } else {
                    pendingMediaTransport = PendingMediaTransport(target, transport)
                }
                transportTarget = null
            },
        )
        null -> Unit
    }
    pendingSecureTransportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSecureTransportTarget = null },
            title = { Text(stringResource(R.string.oneui_data_sms_warning_title)) },
            text = { Text(stringResource(R.string.oneui_data_sms_warning_message)) },
            dismissButton = {
                TextButton(onClick = { pendingSecureTransportTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSecureTransportTarget = null
                        viewModel.setSecureMessageTransport(target, SecureMessageTransport.DATA_SMS)
                    },
                ) {
                    Text(stringResource(R.string.oneui_enable_data_sms))
                }
            },
        )
    }
    pendingMediaTransport?.let { pending ->
        val transport = pending.transport
        val cloudUnavailable = transport == MediaTransport.CLOUD_STORAGE &&
            !state.cloudStorageConfigured
        AlertDialog(
            onDismissRequest = { pendingMediaTransport = null },
            title = {
                Text(
                    stringResource(
                        when {
                            cloudUnavailable -> R.string.oneui_cloud_media_not_configured_title
                            transport == MediaTransport.CLOUD_STORAGE -> R.string.oneui_cloud_media_warning_title
                            transport == MediaTransport.STANDARD_SMS ->
                                R.string.oneui_media_standard_sms_warning_title
                            else -> R.string.oneui_media_data_sms_warning_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        when {
                            cloudUnavailable -> R.string.oneui_cloud_media_not_configured_message
                            transport == MediaTransport.CLOUD_STORAGE -> R.string.oneui_cloud_media_warning_message
                            transport == MediaTransport.STANDARD_SMS ->
                                R.string.oneui_media_standard_sms_warning_message
                            else -> R.string.oneui_media_data_sms_warning_message
                        }
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingMediaTransport = null }) {
                    Text(stringResource(R.string.oneui_cancel))
                }
            },
            confirmButton = {
                if(cloudUnavailable) {
                    TextButton(
                        onClick = {
                            pendingMediaTransport = null
                            cloudSelectionTarget = pending.target
                            showCloudSetup = true
                        },
                    ) {
                        Text(stringResource(R.string.oneui_cloud_storage_configure))
                    }
                } else {
                    TextButton(
                        onClick = {
                            pendingMediaTransport = null
                            viewModel.setMediaTransport(pending.target, transport)
                        },
                    ) {
                        Text(stringResource(
                            if(transport == MediaTransport.CLOUD_STORAGE) {
                                R.string.oneui_enable_cloud_media
                            } else if(transport == MediaTransport.STANDARD_SMS) {
                                R.string.oneui_enable_standard_sms
                            } else {
                                R.string.oneui_enable_data_sms
                            }
                        ))
                    }
                }
            },
        )
    }
    if(showCloudSetup) {
        CloudStorageSetupDialog(
            configured = state.cloudStorageConfigured,
            configuredProvider = state.cloudStorageProvider,
            configuredEndpoint = state.cloudStorageEndpoint,
            configuredFolder = state.cloudStorageFolder,
            error = cloudSetupError,
            saving = cloudSetupSaving,
            onDismiss = {
                showCloudSetup = false
                cloudSetupError = null
                cloudSelectionTarget = null
                cloudSetupSaving = false
            },
            onSave = { provider, endpoint, folder, token ->
                cloudSetupError = null
                cloudSetupSaving = true
                viewModel.configureCloudStorage(
                    provider = provider.name,
                    endpoint = endpoint,
                    folder = folder,
                    accessToken = token,
                ) { result ->
                    cloudSetupSaving = false
                    result.onSuccess {
                        cloudSelectionTarget?.let { target ->
                            viewModel.setMediaTransport(target, MediaTransport.CLOUD_STORAGE)
                        }
                        showCloudSetup = false
                        cloudSetupError = null
                        cloudSelectionTarget = null
                    }.onFailure { error ->
                        cloudSetupError = error.message
                    }
                }
            },
            onClear = {
                cloudSetupSaving = true
                viewModel.clearCloudStorage { result ->
                    cloudSetupSaving = false
                    result.onSuccess {
                        showCloudSetup = false
                        cloudSetupError = null
                        cloudSelectionTarget = null
                    }.onFailure { error ->
                        cloudSetupError = error.message
                    }
                }
            },
        )
    }
}

@Composable
private fun CloudStorageSetupDialog(
    configured: Boolean,
    configuredProvider: String?,
    configuredEndpoint: String,
    configuredFolder: String,
    error: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CloudProvider, String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var provider by remember(configuredProvider) {
        mutableStateOf(
            configuredProvider?.let { runCatching { CloudProvider.valueOf(it) }.getOrNull() }
                ?: CloudProvider.SELF_HOSTED
        )
    }
    var endpoint by remember(configuredEndpoint) { mutableStateOf(configuredEndpoint) }
    var folder by remember(configuredFolder) { mutableStateOf(configuredFolder) }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if(!saving) onDismiss() },
        title = { Text(stringResource(R.string.oneui_cloud_storage_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CloudProvider.entries.forEach { option ->
                    SelectionRow(
                        text = option.displayName(),
                        selected = option == provider,
                        onClick = { if(!saving) provider = option },
                    )
                }
                if(provider == CloudProvider.SELF_HOSTED) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text(stringResource(R.string.oneui_cloud_endpoint)) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text(stringResource(R.string.oneui_cloud_folder)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.oneui_cloud_access_token)) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.oneui_cloud_storage_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MessagesTheme.spacing.sm),
                )
                error?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            Row {
                if(configured) {
                    TextButton(onClick = onClear, enabled = !saving) {
                        Text(stringResource(R.string.oneui_cloud_storage_disconnect))
                    }
                }
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(stringResource(R.string.oneui_cancel))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && token.isNotBlank() &&
                    (provider != CloudProvider.SELF_HOSTED || endpoint.startsWith("https://")),
                onClick = { onSave(provider, endpoint, folder, token) },
            ) {
                Text(stringResource(R.string.oneui_cloud_storage_save))
            }
        },
    )
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) = SettingsValueRow(title, description, onClick)

@Composable
private fun SettingsNavigationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsValueRow(title, description, onClick, modifier)

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            start = MessagesTheme.spacing.xl,
            top = MessagesTheme.spacing.lg,
            end = MessagesTheme.spacing.xl,
            bottom = MessagesTheme.spacing.sm,
        ),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md),
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(content = content)
    }
}

@Composable
private fun ColumnScope.SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = MessagesTheme.spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingsFootnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = MessagesTheme.spacing.xl,
            vertical = MessagesTheme.spacing.md,
        ),
    )
}

@Composable
private fun SimTransportSelector(
    targets: List<TransportTarget>,
    selectedSubscriptionId: Long?,
    onSelected: (TransportTarget) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MessagesTheme.spacing.md),
        shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(MessagesTheme.spacing.xxs)) {
            targets.forEach { target ->
                val selected = target.subscriptionId == selectedSubscriptionId
                Surface(
                    onClick = { onSelected(target) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings-sim-selector-${target.testId}"),
                    shape = RoundedCornerShape(MessagesTheme.dimensions.composerRadius),
                    color = if(selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if(selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    val sim = target.sim
                    Text(
                        text = if(sim == null) {
                            stringResource(R.string.oneui_settings_default_sim)
                        } else {
                            stringResource(
                                R.string.oneui_transport_sim_label,
                                sim.displayName,
                                sim.slotIndex + 1,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(
                            horizontal = MessagesTheme.spacing.sm,
                            vertical = MessagesTheme.spacing.md,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(MessagesTheme.spacing.lg),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(MessagesTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(MessagesTheme.spacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ThemeDialog(
    selected: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    SelectionRow(
                        text = mode.displayName(),
                        selected = mode == selected,
                        onClick = { onSelected(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oneui_cancel)) }
        },
    )
}

@Composable
private fun LanguageDialog(
    state: AppSettingsSnapshot,
    onDismiss: () -> Unit,
    onSelected: (LanguageOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            LazyColumn {
                items(state.languages, key = LanguageOption::tag) { language ->
                    SelectionRow(
                        text = language.displayName,
                        selected = language.tag == state.languageTag,
                        onClick = { onSelected(language) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oneui_cancel)) }
        },
    )
}

@Composable
private fun SecureTransportDialog(
    selected: SecureMessageTransport,
    onDismiss: () -> Unit,
    onSelected: (SecureMessageTransport) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oneui_secure_message_transport)) },
        text = {
            Column {
                SecureMessageTransport.entries.forEach { transport ->
                    SelectionRow(
                        text = transport.displayName(),
                        selected = transport == selected,
                        onClick = { onSelected(transport) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.oneui_cancel))
            }
        },
    )
}

@Composable
private fun MediaTransportDialog(
    selected: MediaTransport,
    cloudStorageConfigured: Boolean,
    onDismiss: () -> Unit,
    onSelected: (MediaTransport) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.oneui_media_transport)) },
        text = {
            Column {
                MediaTransport.entries.forEach { transport ->
                    SelectionRow(
                        text = transport.displayName(),
                        selected = transport == selected,
                        onClick = { onSelected(transport) },
                    )
                    if(transport == MediaTransport.CLOUD_STORAGE && !cloudStorageConfigured) {
                        Text(
                            stringResource(R.string.oneui_cloud_media_setup_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = MessagesTheme.spacing.xl),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.oneui_cancel))
            }
        },
    )
}

@Composable
private fun SelectionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MessagesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, modifier = Modifier.padding(start = MessagesTheme.spacing.sm))
    }
}

@Composable
private fun ThemeMode.displayName(): String = when(this) {
    ThemeMode.SYSTEM -> stringResource(R.string.system_default)
    ThemeMode.LIGHT -> stringResource(R.string.light)
    ThemeMode.DARK -> stringResource(R.string.dark)
}

@Composable
private fun SecureMessageTransport.displayName(): String = when(this) {
    SecureMessageTransport.STANDARD_SMS -> stringResource(R.string.oneui_transport_standard_sms)
    SecureMessageTransport.DATA_SMS -> stringResource(R.string.oneui_transport_data_sms)
}

@Composable
private fun MediaTransport.displayName(): String = when(this) {
    MediaTransport.MMS -> stringResource(R.string.oneui_media_transport_mms)
    MediaTransport.DATA_SMS -> stringResource(R.string.oneui_media_transport_data_sms)
    MediaTransport.STANDARD_SMS -> stringResource(R.string.oneui_media_transport_standard_sms)
    MediaTransport.CLOUD_STORAGE -> stringResource(R.string.oneui_media_transport_cloud)
}

@Composable
private fun CloudProvider.displayName(): String = when(this) {
    CloudProvider.SELF_HOSTED -> stringResource(R.string.oneui_cloud_provider_self_hosted)
    CloudProvider.YANDEX_DISK -> stringResource(R.string.oneui_cloud_provider_yandex)
    CloudProvider.GOOGLE_DRIVE -> stringResource(R.string.oneui_cloud_provider_google)
}

private data class TransportTarget(
    val subscriptionId: Long?,
    val sim: SimTransportSettings?,
    val secureMessageTransport: SecureMessageTransport,
    val mediaTransport: MediaTransport,
) {
    val testId: String get() = subscriptionId?.toString() ?: "legacy"
    constructor(sim: SimTransportSettings) : this(
        subscriptionId = sim.subscriptionId,
        sim = sim,
        secureMessageTransport = sim.secureMessageTransport,
        mediaTransport = sim.mediaTransport,
    )

    companion object {
        fun fromLegacy(state: AppSettingsSnapshot) = TransportTarget(
            subscriptionId = null,
            sim = null,
            secureMessageTransport = state.secureMessageTransport,
            mediaTransport = state.mediaTransport,
        )
    }
}

private data class PendingMediaTransport(
    val target: TransportTarget,
    val transport: MediaTransport,
)

private fun SettingsViewModel.setSecureMessageTransport(
    target: TransportTarget,
    transport: SecureMessageTransport,
) {
    target.subscriptionId?.let { setSecureMessageTransport(it, transport) }
        ?: setSecureMessageTransport(transport)
}

private fun SettingsViewModel.setMediaTransport(
    target: TransportTarget,
    transport: MediaTransport,
) {
    target.subscriptionId?.let { setMediaTransport(it, transport) }
        ?: setMediaTransport(transport)
}

private enum class SettingsDialog {
    THEME,
    LANGUAGE,
    SECURE_TRANSPORT,
    MEDIA_TRANSPORT,
}

private enum class SettingsPage(val titleResource: Int) {
    ROOT(R.string.general_settings),
    APPEARANCE(R.string.oneui_settings_appearance),
    TRANSPORT(R.string.oneui_settings_transport),
    STORAGE(R.string.oneui_settings_storage),
    BEHAVIOR(R.string.oneui_settings_notifications_and_behavior),
    ADVANCED(R.string.oneui_advanced),
}

private const val COMPACT_TITLE_REVEAL_PROGRESS = 0.45f
