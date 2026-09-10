package com.afkanerd.deku.messages.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.attachments.media.VoiceRecorder
import com.afkanerd.deku.attachments.ui.formatVoiceDuration
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.SimSubscription
import kotlinx.coroutines.delay
import java.io.File

/** Samsung-style message entry shared by new, one-to-one and group conversations. */
@Composable
fun OneUiMessageComposer(
    value: String,
    enabled: Boolean,
    isSending: Boolean,
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long?,
    onValueChange: (String) -> Unit,
    onSubscriptionSelected: (Long) -> Unit,
    onAttachment: (() -> Unit)?,
    voiceDraft: PreparedAttachment?,
    onVoiceDraftChanged: (PreparedAttachment?) -> Unit,
    onVoiceSubmit: (PreparedAttachment) -> Unit,
    onSend: () -> Unit,
    messageEncrypted: Boolean? = null,
    onSecurityClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    composerTestTag: String = "oneui-message-composer",
    inputTestTag: String = "oneui-message-input",
    actionTestTag: String = "oneui-message-action",
) {
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    var voicePhase by remember { mutableStateOf(InlineVoicePhase.IDLE) }
    var voiceElapsed by remember { mutableLongStateOf(0L) }
    var voiceAmplitudes by remember { mutableStateOf(emptyList<Int>()) }
    val messageFieldDescription = stringResource(R.string.oneui_message_hint)
    val canInteract = enabled && !isSending
    val hasText = value.isNotBlank()
    val attachmentEnabled = canInteract && onAttachment != null
    val voiceEnabled = canInteract && !hasText && voiceDraft == null

    fun reportVoiceError() {
        Toast.makeText(context, R.string.attachment_failed_prepare, Toast.LENGTH_LONG).show()
    }

    fun beginOrResumeVoice() {
        runCatching {
            if(voicePhase == InlineVoicePhase.PAUSED) {
                voiceRecorder.resume()
            } else {
                voiceRecorder.start()
                voiceElapsed = 0L
                voiceAmplitudes = emptyList()
            }
            voicePhase = inlineVoiceTransition(voicePhase, InlineVoiceEvent.PRESS)
        }.onFailure { reportVoiceError() }
    }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The user starts recording with the next deliberate hold. */ }

    fun onVoicePress() {
        if(!voiceEnabled && voicePhase == InlineVoicePhase.IDLE) return
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginOrResumeVoice()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onVoiceRelease() {
        if(voicePhase != InlineVoicePhase.RECORDING) return
        runCatching { voiceRecorder.pause() }
            .onSuccess {
                voiceElapsed = voiceRecorder.elapsedMillis()
                voicePhase = inlineVoiceTransition(voicePhase, InlineVoiceEvent.RELEASE)
            }
            .onFailure { reportVoiceError() }
    }

    fun discardVoice() {
        voiceRecorder.cancel()
        voiceDraft?.sourceUri?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path
            ?.let(::File)?.delete()
        voicePhase = inlineVoiceTransition(voicePhase, InlineVoiceEvent.DISCARD)
        voiceElapsed = 0L
        voiceAmplitudes = emptyList()
        onVoiceDraftChanged(null)
    }

    fun finishVoice() {
        runCatching { voiceRecorder.stop() }
            .onSuccess { result ->
                voicePhase = inlineVoiceTransition(voicePhase, InlineVoiceEvent.FINISH)
                onVoiceDraftChanged(
                    PreparedAttachment(
                        sourceUri = Uri.fromFile(result.file).toString(),
                        kind = AttachmentKind.VOICE,
                        mimeType = result.mimeType,
                        fileName = if(result.codec == "opus") "voice.ogg" else "voice.amr",
                        originalBytes = result.encodedBytes,
                        encodedBytes = result.encodedBytes,
                        codec = result.codec,
                        sampleRate = result.sampleRate,
                        durationMillis = result.durationMs,
                    )
                )
            }
            .onFailure { reportVoiceError() }
    }

    DisposableEffect(voiceRecorder) {
        onDispose { voiceRecorder.cancel() }
    }
    LaunchedEffect(voicePhase) {
        while(voicePhase == InlineVoicePhase.RECORDING) {
            voiceElapsed = voiceRecorder.elapsedMillis()
            voiceAmplitudes = (voiceAmplitudes + voiceRecorder.maxAmplitude())
                .takeLast(INLINE_VOICE_WAVEFORM_BARS)
            delay(INLINE_VOICE_SAMPLE_MILLIS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(composerTestTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val encrypted = messageEncrypted == true
            val voiceUiVisible = voiceDraft != null || voicePhase != InlineVoicePhase.IDLE
            if(!voiceUiVisible) {
                SamsungAttachmentButton(
                    icon = { tint ->
                        Icon(
                            if(encrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = stringResource(
                                if(encrypted) R.string.oneui_message_encrypted
                                else R.string.oneui_message_plain
                            ),
                            tint = if(encrypted) MaterialTheme.colorScheme.primary else tint,
                        )
                    },
                    enabled = canInteract && onSecurityClick != null,
                    onClick = { onSecurityClick?.invoke() },
                    modifier = Modifier.testTag(
                        if(encrypted) "oneui-composer-encrypted" else "oneui-composer-plain"
                    ),
                )
                SamsungAttachmentButton(
                    icon = { tint ->
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.attachment_menu),
                            tint = tint,
                        )
                    },
                    enabled = attachmentEnabled,
                    onClick = { onAttachment?.invoke() },
                )
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                when {
                    voiceDraft != null -> InlineVoiceDraftContent(
                        draft = voiceDraft,
                        amplitudes = voiceAmplitudes,
                        onDelete = ::discardVoice,
                    )
                    voicePhase != InlineVoicePhase.IDLE -> InlineVoiceRecordingContent(
                        elapsedMillis = voiceElapsed,
                        amplitudes = voiceAmplitudes,
                        recording = voicePhase == InlineVoicePhase.RECORDING,
                        onDelete = ::discardVoice,
                        onFinish = ::finishVoice,
                    )
                    else -> Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if(value.isEmpty()) {
                                Text(
                                    messageFieldDescription,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if(enabled) 1f else 0.45f,
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                enabled = enabled,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { if(canInteract && hasText) onSend() },
                                ),
                                maxLines = 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        focusRequester?.let { Modifier.focusRequester(it) }
                                            ?: Modifier,
                                    )
                                    .semantics { contentDescription = messageFieldDescription }
                                    .testTag(inputTestTag),
                            )
                        }
                        if(subscriptions.isNotEmpty() && selectedSubscriptionId != null) {
                            OneUiInlineNetworkSelector(
                                subscriptions = subscriptions,
                                selectedSubscriptionId = selectedSubscriptionId,
                                enabled = canInteract,
                                onSubscriptionSelected = onSubscriptionSelected,
                            )
                        }
                    }
                }
            }

            val voiceGestureActive = canInteract && !hasText && voiceDraft == null
            val actionEnabled = canInteract && (hasText || voiceDraft != null || voiceGestureActive)
            val actionModifier = if(voiceGestureActive) {
                Modifier.pointerInput(canInteract) {
                    detectTapGestures(
                        onPress = {
                            onVoicePress()
                            tryAwaitRelease()
                            onVoiceRelease()
                        },
                    )
                }
            } else {
                Modifier.clickable(enabled = actionEnabled) {
                    when {
                        hasText -> onSend()
                        voiceDraft != null -> onVoiceSubmit(voiceDraft)
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .testTag(actionTestTag)
                    .then(actionModifier),
                shape = CircleShape,
                color = if((hasText || voiceDraft != null) && actionEnabled) {
                    MaterialTheme.colorScheme.primary
                } else if(voicePhase == InlineVoicePhase.RECORDING) {
                    MaterialTheme.colorScheme.errorContainer
                }
                    else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if(isSending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        AnimatedContent(
                            targetState = hasText || voiceDraft != null,
                            label = "voice-send",
                        ) { showSend ->
                            Icon(
                                if(showSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                contentDescription = stringResource(
                                    if(showSend) R.string.oneui_send else R.string.attachment_voice,
                                ),
                                modifier = Modifier.size(22.dp),
                                tint = if(showSend && actionEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else if(voicePhase == InlineVoicePhase.RECORDING) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if(actionEnabled) 1f else 0.45f,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal enum class InlineVoicePhase { IDLE, RECORDING, PAUSED }
internal enum class InlineVoiceEvent { PRESS, RELEASE, FINISH, DISCARD }

internal fun inlineVoiceTransition(
    phase: InlineVoicePhase,
    event: InlineVoiceEvent,
): InlineVoicePhase = when(event) {
    InlineVoiceEvent.PRESS -> InlineVoicePhase.RECORDING
    InlineVoiceEvent.RELEASE -> if(phase == InlineVoicePhase.RECORDING) {
        InlineVoicePhase.PAUSED
    } else {
        phase
    }
    InlineVoiceEvent.FINISH,
    InlineVoiceEvent.DISCARD -> InlineVoicePhase.IDLE
}

@Composable
private fun InlineVoiceRecordingContent(
    elapsedMillis: Long,
    amplitudes: List<Int>,
    recording: Boolean,
    onDelete: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("oneui-voice-recording"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.voice_delete_recording),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if(recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    CircleShape,
                )
        )
        InlineVoiceWaveform(
            amplitudes = amplitudes,
            active = recording,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatVoiceDuration(elapsedMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = onFinish,
            enabled = elapsedMillis >= MIN_INLINE_VOICE_MILLIS,
            modifier = Modifier.size(36.dp).testTag("oneui-voice-finish"),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.voice_finish_recording),
            )
        }
    }
}

@Composable
private fun InlineVoiceWaveform(
    amplitudes: List<Int>,
    active: Boolean,
    playbackProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = if(active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    Canvas(modifier = modifier.height(28.dp)) {
        val barWidth = 2.dp.toPx()
        val minimumStep = 5.dp.toPx()
        val barCount = (size.width / minimumStep).toInt().coerceIn(18, 72)
        val samples = amplitudes.takeLast(barCount)
        val firstSample = barCount - samples.size
        val gap = if(barCount <= 1) 0f else {
            ((size.width - barWidth * barCount) / (barCount - 1)).coerceAtLeast(1f)
        }
        repeat(barCount) { index ->
            val amplitude = samples.getOrNull(index - firstSample) ?: 0
            val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
            val barHeight = 4.dp.toPx() + normalized * 20.dp.toPx()
            val barProgress = (index + 1f) / barCount
            drawRoundRect(
                color = if(playbackProgress != null && barProgress <= playbackProgress) {
                    activeColor
                } else {
                    inactiveColor
                },
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * (barWidth + gap),
                    y = (size.height - barHeight) / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun InlineVoiceDraftContent(
    draft: PreparedAttachment,
    amplitudes: List<Int>,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val uri = remember(draft.sourceUri) { Uri.parse(draft.sourceUri) }
    val player = remember(uri) { MediaPlayer() }
    var prepared by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var positionMillis by remember(uri) { mutableLongStateOf(0L) }
    var durationMillis by remember(uri) {
        mutableLongStateOf(draft.durationMillis.coerceAtLeast(0L))
    }

    DisposableEffect(player, uri) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnPreparedListener {
            prepared = true
            durationMillis = it.duration.toLong().coerceAtLeast(durationMillis)
        }
        player.setOnCompletionListener {
            playing = false
            positionMillis = 0L
        }
        player.setOnErrorListener { _, _, _ ->
            prepared = false
            playing = false
            true
        }
        runCatching {
            player.setDataSource(context, uri)
            player.prepareAsync()
        }
        onDispose {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.release()
        }
    }
    LaunchedEffect(playing, player) {
        while(playing) {
            positionMillis = runCatching { player.currentPosition.toLong() }
                .getOrDefault(positionMillis)
            delay(INLINE_VOICE_SAMPLE_MILLIS)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("oneui-voice-draft"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {
                if(prepared) {
                    if(player.isPlaying) {
                        player.pause()
                        playing = false
                    } else {
                        player.start()
                        playing = true
                    }
                }
            },
            enabled = prepared,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                if(playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.attachment_play_voice),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            InlineVoiceWaveform(
                amplitudes = amplitudes,
                active = false,
                playbackProgress = if(durationMillis <= 0L) 0f else {
                    (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                formatVoiceDuration(durationMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.voice_delete_recording),
            )
        }
    }
}

@Composable
private fun SamsungAttachmentButton(
    icon: @Composable (tint: Color) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = if(enabled) 0.9f else 0.38f,
            ),
        )
    }
}

private const val INLINE_VOICE_WAVEFORM_BARS = 72
private const val INLINE_VOICE_SAMPLE_MILLIS = 80L
private const val MIN_INLINE_VOICE_MILLIS = 350L

@Composable
private fun OneUiInlineNetworkSelector(
    subscriptions: List<SimSubscription>,
    selectedSubscriptionId: Long,
    enabled: Boolean,
    onSubscriptionSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = subscriptions.firstOrNull { it.id == selectedSubscriptionId }
        ?: subscriptions.first()
    val chooserDescription = stringResource(R.string.choose_sim_card)
    val canChoose = enabled && subscriptions.size > 1

    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .widthIn(max = 96.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = canChoose) { expanded = true }
                .semantics(mergeDescendants = true) {
                    contentDescription = chooserDescription
                    stateDescription = selected.displayName
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selected.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if(enabled) 1f else 0.45f,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = ONE_UI_POPUP_MENU_ALPHA,
            ),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            subscriptions.forEach { subscription ->
                DropdownMenuItem(
                    text = { Text(subscription.displayName) },
                    onClick = {
                        expanded = false
                        onSubscriptionSelected(subscription.id)
                    },
                )
            }
        }
    }
}
