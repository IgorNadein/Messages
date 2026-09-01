package com.afkanerd.deku.messages.ui

import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaViewerNavigationRegressionTest {
    @Test
    fun mmsBubbleWithContentUriOpensTheOneUiViewer() {
        val media = media(
            uri = "content://mms/part/42",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
        )

        val destination = mediaViewerDestination(media, "+15550000000", "1 Sep, 12:30")

        requireNotNull(destination)
        assertEquals("content://mms/part/42", destination.contentUri)
        assertEquals("+15550000000", destination.address)
        assertEquals("photo.jpg", destination.filename)
        assertEquals("image/jpeg", destination.mimeType)
    }

    @Test
    fun mediaWithoutAReadableUriDoesNotPretendToBeOpenable() {
        assertNull(mediaViewerDestination(media(uri = null), "+1", "today"))
        assertNull(mediaViewerDestination(media(uri = "  "), "+1", "today"))
    }

    @Test
    fun viewerUsesSafeFallbackMetadata() {
        val destination = requireNotNull(
            mediaViewerDestination(
                media(uri = "content://mms/part/7", fileName = null, mimeType = null),
                "+1",
                "today",
            )
        )

        assertEquals("attachment", destination.filename)
        assertEquals("application/octet-stream", destination.mimeType)
        assertEquals(MediaViewerKind.IMAGE, mediaViewerKind("image/webp"))
        assertEquals(MediaViewerKind.VIDEO, mediaViewerKind("video/mp4"))
        assertEquals(MediaViewerKind.UNSUPPORTED, mediaViewerKind("application/pdf"))
    }

    private fun media(
        uri: String?,
        fileName: String? = "file",
        mimeType: String? = "image/jpeg",
    ) = TimelineItem.Media(
        stableId = "media-42",
        timestampMillis = 42,
        uri = uri,
        fileName = fileName,
        mimeType = mimeType,
        caption = null,
        direction = MessageDirection.INCOMING,
        deliveryState = DeliveryState.RECEIVED,
    )
}
