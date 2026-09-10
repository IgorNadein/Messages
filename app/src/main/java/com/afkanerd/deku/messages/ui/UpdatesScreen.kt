package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme
import com.afkanerd.deku.updates.AppUpdates
import com.afkanerd.deku.updates.GitHubUpdates
import java.io.File

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates = remember(context, scope) { AppUpdates(context.applicationContext, scope) }
    val state by updates.state.collectAsState()
    val current = remember(context) { GitHubUpdates.currentVersion(context) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val release = state.release
    val apkPath = state.apkPath
    val available = release != null && release.versionCode > current.second

    LaunchedEffect(Unit) {
        if(!state.checked && !state.checking) updates.check()
    }

    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.updates_title),
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
            verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.md),
        ) {
            item {
                OneUiExpandedTitle(
                    title = stringResource(R.string.updates_title),
                    subtitle = stringResource(R.string.updates_subtitle),
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MessagesTheme.spacing.md)
                        .testTag("updates-card"),
                    shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.padding(MessagesTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm),
                    ) {
                        Text(
                            text = when {
                                state.checking -> stringResource(R.string.updates_checking)
                                available -> stringResource(
                                    R.string.updates_available,
                                    release.versionName,
                                )
                                state.checked && state.error == null ->
                                    stringResource(R.string.updates_current)
                                else -> stringResource(R.string.updates_from_github)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.updates_installed, current.first, current.second),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if(state.checking) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        state.error?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        installMessage?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall)
                        }
                        when {
                            state.downloading -> {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row {
                                    Text(
                                        text = stringResource(
                                            R.string.updates_download_progress,
                                            (state.progress * 100).toInt(),
                                        ),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = updates::cancel) {
                                        Text(stringResource(R.string.oneui_cancel))
                                    }
                                }
                            }
                            available && apkPath != null -> {
                                Button(
                                    modifier = Modifier.testTag("install-update"),
                                    onClick = {
                                        runCatching {
                                            GitHubUpdates.install(context, File(apkPath))
                                        }.onSuccess { opened ->
                                            installMessage = if(opened) null else {
                                                context.getString(R.string.updates_allow_install)
                                            }
                                        }.onFailure {
                                            installMessage = it.message
                                                ?: context.getString(R.string.updates_install_failed)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.updates_install))
                                }
                            }
                            available -> {
                                Button(
                                    onClick = updates::download,
                                    enabled = !state.checking,
                                    modifier = Modifier.testTag("download-update"),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(MessagesTheme.spacing.sm))
                                    Text(stringResource(R.string.updates_download))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(MessagesTheme.spacing.sm)) {
                            TextButton(
                                onClick = updates::check,
                                enabled = !state.checking && !state.downloading,
                                modifier = Modifier.testTag("check-updates"),
                            ) {
                                Text(stringResource(R.string.updates_check))
                            }
                            TextButton(
                                onClick = {
                                    runCatching { GitHubUpdates.openRelease(context, release) }
                                        .onFailure {
                                            installMessage = context.getString(
                                                R.string.updates_browser_failed
                                            )
                                        }
                                },
                            ) {
                                Text(stringResource(R.string.updates_whats_new))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.updates_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MessagesTheme.spacing.xl),
                )
            }
        }
    }
}
