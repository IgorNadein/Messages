package com.afkanerd.deku.messages.service

import kotlinx.coroutines.CancellationException

internal data class MessageImportBatchResult(
    val importedCount: Int,
    val failedCount: Int,
    val firstFailure: Throwable?,
) {
    fun canUseCache(totalCount: Int): Boolean = totalCount == 0 || importedCount > 0
}

/**
 * Imports Telephony threads independently. A single malformed OEM-provider row must not make
 * every subsequent app resume repeat the entire import from the beginning.
 */
internal object MessageImportBatch {
    suspend fun <T> run(
        items: List<T>,
        importOne: suspend (T) -> Unit,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): MessageImportBatchResult {
        var importedCount = 0
        var failedCount = 0
        var firstFailure: Throwable? = null

        items.forEachIndexed { index, item ->
            try {
                importOne(item)
                importedCount++
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(error: Throwable) {
                failedCount++
                if(firstFailure == null) firstFailure = error
            }
            onProgress(index + 1, items.size)
        }

        return MessageImportBatchResult(importedCount, failedCount, firstFailure)
    }
}
