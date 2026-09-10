package com.afkanerd.deku.messages.ui

import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.MessageDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentBubblePolicyTest {
    @Test
    fun failedPausedAndBackoffTransfersExposeManualContinuation() {
        assertTrue(attachmentCanContinue(AttachmentTransferState.FAILED))
        assertTrue(attachmentCanContinue(AttachmentTransferState.PAUSED))
        assertTrue(attachmentCanContinue(AttachmentTransferState.RETRYING))
    }

    @Test
    fun activeAndCompletedTransfersDoNotExposeDuplicateContinuation() {
        assertFalse(attachmentCanContinue(AttachmentTransferState.PREPARING))
        assertFalse(attachmentCanContinue(AttachmentTransferState.WAITING))
        assertFalse(attachmentCanContinue(AttachmentTransferState.SENDING))
        assertFalse(attachmentCanContinue(AttachmentTransferState.RECEIVING))
        assertFalse(attachmentCanContinue(AttachmentTransferState.VERIFYING))
        assertFalse(attachmentCanContinue(AttachmentTransferState.COMPLETED))
        assertFalse(attachmentCanContinue(AttachmentTransferState.CANCELLED))
    }

    @Test
    fun longPressMenuExistsOnlyWhenThereIsAnAvailableAction() {
        assertTrue(attachmentHasActions(AttachmentTransferState.OFFERED))
        assertTrue(attachmentHasActions(AttachmentTransferState.SENDING))
        assertTrue(attachmentHasActions(AttachmentTransferState.FAILED))
        assertFalse(attachmentHasActions(AttachmentTransferState.COMPLETED))
        assertFalse(attachmentHasActions(AttachmentTransferState.CANCELLED))
    }

    @Test
    fun outgoingVoiceRemainsPlayableWhileSendingAndRetrying() {
        listOf(
            AttachmentTransferState.PREPARING,
            AttachmentTransferState.WAITING,
            AttachmentTransferState.SENDING,
            AttachmentTransferState.RETRYING,
            AttachmentTransferState.PAUSED,
            AttachmentTransferState.FAILED,
        ).forEach { state ->
            assertEquals(
                "/files/voice.ogg",
                attachmentVoicePlaybackSource(voiceTransfer(state = state)),
            )
        }
    }

    @Test
    fun incompleteIncomingVoiceDoesNotExposePartialFile() {
        assertEquals(
            null,
            attachmentVoicePlaybackSource(
                voiceTransfer(
                    state = AttachmentTransferState.RECEIVING,
                    direction = MessageDirection.INCOMING,
                )
            ),
        )
    }

    @Test
    fun completedVoiceUsesCommittedFileForBothDirections() {
        assertEquals(
            "/files/complete.ogg",
            attachmentVoicePlaybackSource(
                voiceTransfer(
                    state = AttachmentTransferState.COMPLETED,
                    direction = MessageDirection.INCOMING,
                    completedPath = "/files/complete.ogg",
                )
            ),
        )
    }

    @Test
    fun completedIncomingPhotoOpensThroughResolvedContentUri() {
        val photo = voiceTransfer(
            state = AttachmentTransferState.COMPLETED,
            direction = MessageDirection.INCOMING,
            completedPath = "/files/photo.jpg",
        ).copy(
            kind = AttachmentKind.PHOTO,
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
        )

        val item = attachmentViewerItem(photo) { "content://messages/received/photo.jpg" }

        requireNotNull(item)
        assertEquals("content://messages/received/photo.jpg", item.uri)
        assertEquals("photo.jpg", item.fileName)
        assertEquals(MessageDirection.INCOMING, item.direction)
    }

    @Test
    fun partialPhotoAndVoiceNeverOpenAsVisualMedia() {
        val partialPhoto = voiceTransfer(AttachmentTransferState.RECEIVING).copy(
            kind = AttachmentKind.PHOTO,
            mimeType = "image/jpeg",
        )
        assertNull(attachmentViewerItem(partialPhoto) { "content://unexpected" })
        assertNull(
            attachmentViewerItem(
                voiceTransfer(
                    state = AttachmentTransferState.COMPLETED,
                    completedPath = "/files/voice.ogg",
                )
            ) { "content://unexpected" }
        )
    }

    private fun voiceTransfer(
        state: AttachmentTransferState,
        direction: MessageDirection = MessageDirection.OUTGOING,
        completedPath: String? = null,
    ) = AttachmentTransfer(
        stableId = "voice",
        timestampMillis = 1L,
        direction = direction,
        kind = AttachmentKind.VOICE,
        fileName = "voice.ogg",
        mimeType = "audio/ogg",
        encodedBytes = 100,
        completedSms = 0,
        totalSms = 2,
        state = state,
        completedPath = completedPath,
        previewPath = "/files/voice.ogg",
        durationMillis = 3_000,
        hasError = false,
    )
}
