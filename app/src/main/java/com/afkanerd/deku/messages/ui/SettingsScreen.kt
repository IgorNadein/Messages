package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.DefaultSMS.BuildConfig
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.deku.messages.presentation.SettingsViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onDeveloperOptions: () -> Unit,
    onRemoteListeners: () -> Unit,
    onGatewayClients: () -> Unit,
    onRoutingHistory: () -> Unit,
    onAbout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.general_settings),
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
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        ) {
            item {
                OneUiExpandedTitle(title = stringResource(R.string.general_settings))
            }
            item { SettingsSectionTitle(stringResource(R.string.theme)) }
            item {
                SettingsCard {
                    SettingsValueRow(
                        title = stringResource(R.string.theme),
                        description = state.themeMode.displayName(),
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.language),
                        description = state.languageName,
                        onClick = { dialog = SettingsDialog.LANGUAGE },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.enable_24_hours_time_format),
                        description = stringResource(R.string.when_turned_off_would_use_your_system_s_default_time_format),
                        checked = state.use24HourTime,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.USE_24_HOUR_TIME, it)
                        },
                    )
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.oneui_messages_title)) }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.save_messages_to_system_s_database),
                        description = stringResource(R.string.other_messaging_apps_would_have_access_to_the_system_s_database),
                        checked = state.storeInSystemDatabase,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.STORE_IN_SYSTEM_DATABASE, it)
                        },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.delete_messages_from_system_s_database),
                        description = stringResource(R.string.this_would_delete_messages_from_system_s_database_accessible_by_other_apps),
                        checked = state.deleteFromSystemDatabase,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.DELETE_FROM_SYSTEM_DATABASE, it)
                        },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.get_sms_delivery_reports),
                        description = stringResource(R.string.find_out_when_an_sms_message_is_delivered),
                        checked = state.deliveryReports,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.DELIVERY_REPORTS, it)
                        },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.enable_swipe),
                        description = stringResource(R.string.messages_can_be_deleted_or_archived_by_swiping),
                        checked = state.swipeActions,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.SWIPE_ACTIONS, it)
                        },
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.keep_messages_archived),
                        description = stringResource(R.string.messages_remain_in_archive_even_when_new_ones_are_sent_or_received),
                        checked = state.keepArchived,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.KEEP_ARCHIVED, it)
                        },
                    )
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.enable_notification_context_replies)) }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        title = stringResource(R.string.enable_notification_context_replies),
                        description = stringResource(R.string.your_phone_would_suggest_smart_replies_in_notifications_if_enabled),
                        checked = state.contextReplies,
                        onCheckedChange = {
                            viewModel.setBoolean(BooleanSetting.CONTEXT_REPLIES, it)
                        },
                    )
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.oneui_advanced)) }
            item {
                SettingsCard {
                    SettingsActionRow(
                        title = stringResource(R.string.remote_listeners),
                        description = stringResource(R.string.oneui_remote_listeners_description),
                        onClick = onRemoteListeners,
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.gateway_clients),
                        description = stringResource(R.string.oneui_gateway_clients_description),
                        onClick = onGatewayClients,
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.settings_SMS_routing_title),
                        description = stringResource(R.string.oneui_routing_history_description),
                        onClick = onRoutingHistory,
                    )
                    if(BuildConfig.DEBUG) {
                        SettingsActionRow(
                            title = stringResource(R.string.developer_options),
                            description = stringResource(R.string.oneui_developer_description),
                            onClick = onDeveloperOptions,
                        )
                    }
                }
            }
            item { SettingsSectionTitle(stringResource(R.string.oneui_about)) }
            item {
                SettingsCard {
                    SettingsValueRow(
                        title = stringResource(R.string.oneui_version),
                        description = BuildConfig.VERSION_NAME,
                        onClick = onAbout,
                    )
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
        null -> Unit
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) = SettingsValueRow(title, description, onClick)

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
private fun SettingsValueRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_cancel)) }
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_cancel)) }
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

private enum class SettingsDialog {
    THEME,
    LANGUAGE,
}
