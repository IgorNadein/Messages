package com.afkanerd.deku.messages.ui

import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.DeliveryState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTimelineMergeTest {
    @Test
    fun attachmentTransfersAreInterleavedWithSmsByTimestamp() {
        val newest = text("newest", 3_000)
        val oldest = text("oldest", 1_000)
        val transfer = attachment("middle", 2_000)

        val result = mergeConversationTimeline(
            messages = listOf(newest, oldest),
            attachments = listOf(transfer),
        )

        assertEquals(
            listOf("message-newest", "transfer-middle", "message-oldest"),
            result.map(ConversationTimelineEntry::stableId),
        )
        assertTrue(result[1] is ConversationTimelineEntry.Transfer)
        assertEquals(0, (result[0] as ConversationTimelineEntry.Message).pagingIndex)
        assertEquals(1, (result[2] as ConversationTimelineEntry.Message).pagingIndex)
    }

    @Test
    fun equalTimestampsHaveDeterministicStableOrdering() {
        val result = mergeConversationTimeline(
            messages = listOf(text("b", 1_000)),
            attachments = listOf(attachment("a", 1_000)),
        )

        assertEquals(result.map { it.stableId }.sorted(), result.map { it.stableId })
    }

    @Test
    fun maximumPagingWindowKeepsUniqueStableKeysAndNewestFirstOrdering() {
        val messages = List(500) { index ->
            text("large-$index", index.toLong())
        }

        val result = mergeConversationTimeline(messages, emptyList())

        assertEquals(500, result.size)
        assertEquals(500, result.map { it.stableId }.toSet().size)
        assertEquals("message-large-499", result.first().stableId)
        assertEquals("message-large-0", result.last().stableId)
    }

    @Test
    fun adjacentIncomingMessagesFromDifferentGroupMembersNeverMerge() {
        val alice = text("alice", 2_000, "+15550000001")
        val bob = text("bob", 1_900, "+15550000002")

        assertTrue(!alice.canGroupWith(bob))
    }

    private fun text(id: String, timestamp: Long, authorAddress: String? = null) = TimelineItem.Text(
        stableId = id,
        timestampMillis = timestamp,
        text = id,
        direction = MessageDirection.INCOMING,
        deliveryState = DeliveryState.RECEIVED,
        isSecure = false,
        author = authorAddress?.let {
            MessageAuthor(address = it, displayName = it, avatarUri = null)
        },
    )

    private fun attachment(id: String, timestamp: Long) = AttachmentTransfer(
        stableId = id,
        timestampMillis = timestamp,
        direction = MessageDirection.OUTGOING,
        kind = AttachmentKind.FILE,
        fileName = "$id.bin",
        mimeType = "application/octet-stream",
        encodedBytes = 10,
        completedSms = 1,
        totalSms = 1,
        state = AttachmentTransferState.COMPLETED,
        completedPath = null,
        durationMillis = 0,
        hasError = false,
    )
}
