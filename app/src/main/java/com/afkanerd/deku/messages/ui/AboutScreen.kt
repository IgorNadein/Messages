package com.afkanerd.deku.messages.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.components.OneUiExpandedTitle
import com.afkanerd.deku.messages.ui.theme.MessagesTheme

const val OPEN_SOURCE_PROJECT_URL = "https://github.com/deku-messaging/Deku-SMS-Android"

@Composable
fun AboutOneUiScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            OneUiCompactBar(
                title = stringResource(R.string.about_deku),
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
                    title = stringResource(R.string.about_deku),
                    subtitle = stringResource(R.string.about_deku_motto),
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        // Adaptive-icon XML cannot be decoded by painterResource on Android 8+.
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                    )
                    Spacer(Modifier.size(MessagesTheme.spacing.sm))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${stringResource(R.string.oneui_version)} $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MessagesTheme.spacing.md)
                        .testTag("about-open-source")
                        .clickable { onOpenSource(OPEN_SOURCE_PROJECT_URL) },
                    shape = RoundedCornerShape(MessagesTheme.dimensions.groupRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(MessagesTheme.spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.github_mark),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.size(MessagesTheme.spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.about_deku_github_titile),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.size(MessagesTheme.spacing.xs))
                            Text(
                                stringResource(R.string.about_deku_github_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(MessagesTheme.spacing.sm))
                            Text(
                                stringResource(R.string.about_deku_github_btn),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
