package com.afkanerd.deku.messages.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageImportBatchTest {
    @Test
    fun malformedThreadDoesNotRestartOrAbortTheWholeImport() = runTest {
        val attempted = mutableListOf<Int>()
        val progress = mutableListOf<Int>()

        val result = MessageImportBatch.run(
            items = listOf(1, 2, 3),
            importOne = { threadId ->
                attempted += threadId
                if(threadId == 2) error("malformed MMS")
            },
            onProgress = { completed, _ -> progress += completed },
        )

        assertEquals(listOf(1, 2, 3), attempted)
        assertEquals(listOf(1, 2, 3), progress)
        assertEquals(2, result.importedCount)
        assertEquals(1, result.failedCount)
        assertTrue(result.canUseCache(totalCount = 3))
    }

    @Test
    fun totalProviderFailureDoesNotMarkAnEmptyCacheReady() = runTest {
        val result = MessageImportBatch.run(
            items = listOf(1, 2),
            importOne = { error("provider unavailable") },
            onProgress = { _, _ -> },
        )

        assertEquals(0, result.importedCount)
        assertEquals(2, result.failedCount)
        assertFalse(result.canUseCache(totalCount = 2))
    }
}
