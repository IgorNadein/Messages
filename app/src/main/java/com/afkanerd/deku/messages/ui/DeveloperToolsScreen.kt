package com.afkanerd.deku.messages.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.presentation.DeveloperToolsResult
import com.afkanerd.deku.messages.presentation.DeveloperToolsViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

@Composable
fun DeveloperToolsScreen(
    viewModel: DeveloperToolsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val sampleCreated = stringResource(R.string.oneui_developer_sample_created)
    val historyCleared = stringResource(R.string.oneui_developer_history_cleared)
    val failed = stringResource(R.string.oneui_operation_failed)
    val nativeExported = stringResource(R.string.conversations_exported_complete)
    val nativeCleared = stringResource(R.string.oneui_developer_native_cleared)
    val nativeImported = state.importSummary?.let { summary ->
        stringResource(
            R.string.oneui_developer_native_imported,
            summary.mmsCount,
            summary.mmsPartCount,
            summary.mmsAddressCount,
        )
    } ?: failed
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportNativeMessageDatabase(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importNativeMessageDatabase(it.toString()) }
    }

    LaunchedEffect(state.result) {
        state.result?.let { result ->
            snackbar.showSnackbar(
                when(result) {
                    DeveloperToolsResult.SAMPLE_NOTIFICATION_CREATED -> sampleCreated
                    DeveloperToolsResult.LOCAL_HISTORY_CLEARED -> historyCleared
                    DeveloperToolsResult.NATIVE_DATABASE_EXPORTED -> nativeExported
                    DeveloperToolsResult.NATIVE_DATABASE_IMPORTED -> nativeImported
                    DeveloperToolsResult.NATIVE_DATABASE_CLEARED -> nativeCleared
                    DeveloperToolsResult.FAILED -> failed
                }
            )
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.developer_options),
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
                    if(state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MessagesTheme.spacing.xl),
                            strokeWidth = MessagesTheme.spacing.xxs,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = MessagesTheme.spacing.xxl),
        ) {
            item {
                OneUiExpandedTitle(
                    title = stringResource(R.string.developer_options),
                    subtitle = stringResource(R.string.oneui_developer_warning),
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MessagesTheme.spacing.md),
                    shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column {
                        DeveloperToolRow(
                            icon = Icons.Default.Download,
                            title = stringResource(R.string.export_sms_mms_database),
                            description = stringResource(
                                R.string.this_exports_all_sms_and_mms_databases_can_be_useful_if_you_need_to_capture_all_fields_as_stored_by_the_default_sms_apps
                            ),
                            enabled = !state.isBusy,
                            onClick = {
                                exportLauncher.launch(
                                    resources.getString(
                                        R.string.deku_sms_dev_mode_export,
                                        System.currentTimeMillis(),
                                    )
                                )
                            },
                        )
                        DeveloperToolRow(
                            icon = Icons.Default.UploadFile,
                            title = stringResource(R.string.oneui_developer_import_native),
                            description = stringResource(
                                R.string.oneui_developer_import_native_description
                            ),
                            enabled = !state.isBusy,
                            onClick = { importLauncher.launch("application/json") },
                        )
                        DeveloperToolRow(
                            icon = Icons.Default.DeleteForever,
                            title = stringResource(R.string.oneui_developer_clear_native),
                            description = stringResource(
                                R.string.oneui_developer_clear_native_description
                            ),
                            enabled = !state.isBusy,
                            destructive = true,
                            onClick = viewModel::requestClearNativeMessageDatabase,
                        )
                        DeveloperToolRow(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.oneui_developer_trigger_mms),
                            description = stringResource(R.string.oneui_developer_trigger_mms_description),
                            enabled = !state.isBusy,
                            onClick = viewModel::triggerSampleMmsNotification,
                        )
                        DeveloperToolRow(
                            icon = Icons.Default.DeleteForever,
                            title = stringResource(R.string.oneui_developer_clear_history),
                            description = stringResource(R.string.oneui_developer_clear_history_description),
                            enabled = !state.isBusy,
                            destructive = true,
                            onClick = viewModel::requestClearLocalHistory,
                        )
                    }
                }
            }
        }
    }

    if(state.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearLocalHistory,
            title = { Text(stringResource(R.string.oneui_developer_clear_confirmation_title)) },
            text = { Text(stringResource(R.string.oneui_developer_clear_confirmation_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearLocalHistory) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearLocalHistory) {
                    Text(stringResource(R.string.messages_thread_delete_confirmation_cancel))
                }
            },
        )
    }

    if(state.showClearNativeConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearNativeMessageDatabase,
            title = {
                Text(stringResource(R.string.oneui_developer_clear_native_confirmation_title))
            },
            text = {
                Text(stringResource(R.string.oneui_developer_clear_native_confirmation_text))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmClearNativeMessageDatabase) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearNativeMessageDatabase) {
                    Text(stringResource(R.string.messages_thread_delete_confirmation_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeveloperToolRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(MessagesTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if(destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(MessagesTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if(destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
