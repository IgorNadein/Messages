package com.afkanerd.deku.attachments.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkTrackerTest {
    @Test
    fun duplicateAndRandomOrderAreIdempotentAndPersistent() {
        val tracker = ChunkTracker.empty(8)
        listOf(3, 1, 3, 7, 0, 5, 2, 6, 4).forEach(tracker::mark)
        assertTrue(tracker.isComplete())
        assertEquals(8, tracker.count())
        val restored = ChunkTracker.restore(8, tracker.serialize())
        assertTrue(restored.isComplete())
        assertArrayEquals(IntArray(0), restored.missing())
    }

    @Test
    fun missingChunksAreReportedOnly() {
        val tracker = ChunkTracker.empty(8)
        listOf(0, 1, 2, 3, 5, 7).forEach(tracker::mark)
        assertFalse(tracker.isComplete())
        assertArrayEquals(intArrayOf(4, 6), tracker.missing())
    }
}
