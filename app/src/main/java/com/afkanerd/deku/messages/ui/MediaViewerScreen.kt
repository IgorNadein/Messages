package com.afkanerd.deku.messages.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.messages.presentation.MediaViewerNotice
import com.afkanerd.deku.messages.presentation.MediaViewerViewModel
import com.afkanerd.deku.messages.ui.components.OneUiCompactBar
import com.afkanerd.deku.messages.ui.theme.MessagesTheme
import com.afkanerd.smswithoutborders_libsmsmms.ui.navigation.ImageViewScreenNav

internal enum class MediaViewerKind { IMAGE, VIDEO, UNSUPPORTED }

internal fun mediaViewerKind(mimeType: String): MediaViewerKind = when {
    mimeType.startsWith("image/", ignoreCase = true) -> MediaViewerKind.IMAGE
    mimeType.startsWith("video/", ignoreCase = true) -> MediaViewerKind.VIDEO
    else -> MediaViewerKind.UNSUPPORTED
}

internal fun mediaViewerDestination(
    item: TimelineItem.Media,
    address: String,
    formattedDate: String,
): ImageViewScreenNav? {
    val uri = item.uri?.takeIf(String::isNotBlank) ?: return null
    return ImageViewScreenNav(
        contentUri = uri,
        address = address,
        date = formattedDate,
        filename = item.fileName?.takeIf(String::isNotBlank) ?: "attachment",
        mimeType = item.mimeType?.takeIf(String::isNotBlank) ?: "application/octet-stream",
    )
}

@Composable
fun MediaViewerScreen(
    contentUri: String,
    address: String,
    date: String,
    filename: String,
    mimeType: String,
    viewModel: MediaViewerViewModel,
    onBack: () -> Unit,
    onShare: (String, String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val savedMessage = stringResource(R.string.oneui_media_saved)
    val failedMessage = stringResource(R.string.oneui_operation_failed)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType.ifBlank { "application/octet-stream" })
    ) { destination ->
        viewModel.save(destination?.toString())
    }

    LaunchedEffect(state.notice) {
        when(state.notice) {
            MediaViewerNotice.SAVED -> snackbarHost.showSnackbar(savedMessage)
            MediaViewerNotice.FAILED -> snackbarHost.showSnackbar(failedMessage)
            null -> Unit
        }
        if(state.notice != null) viewModel.clearNotice()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            AnimatedVisibility(chromeVisible) {
                OneUiCompactBar(
                    title = address,
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
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = MessagesTheme.spacing.sm),
                        )
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(chromeVisible) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = MessagesTheme.spacing.md),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { saveLauncher.launch(filename) },
                            enabled = !state.isSaving,
                            modifier = Modifier.size(MessagesTheme.dimensions.minimumTouchTarget),
                        ) {
                            if(state.isSaving) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.DownloadForOffline,
                                    contentDescription = stringResource(R.string.save),
                                )
                            }
                        }
                        IconButton(
                            onClick = { onShare(contentUri, mimeType) },
                            modifier = Modifier.size(MessagesTheme.dimensions.minimumTouchTarget),
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.share_message),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            when(mediaViewerKind(mimeType)) {
                MediaViewerKind.IMAGE -> AsyncImage(
                    model = contentUri,
                    contentDescription = stringResource(R.string.mms_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { chromeVisible = !chromeVisible },
                )
                MediaViewerKind.VIDEO -> VideoViewer(contentUri.toUri())
                MediaViewerKind.UNSUPPORTED -> Text(
                    text = filename,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MessagesTheme.spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun VideoViewer(uri: Uri) {
    val context = LocalContext.current
    val videoView = remember(uri) {
        VideoView(context).apply {
            setVideoURI(uri)
            setMediaController(MediaController(context).also { controller ->
                controller.setAnchorView(this)
            })
            setOnPreparedListener { player ->
                player.isLooping = false
            }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { videoView },
    )
    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
    }
}
