package com.afkanerd.deku.messages.service

import androidx.paging.PagingConfig

/** Thread summaries are lightweight and must keep stable indices while paging to the end. */
internal object ConversationPagingPolicy {
    const val PAGE_SIZE = 40

    fun config() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE * 2,
        prefetchDistance = PAGE_SIZE,
        enablePlaceholders = false,
        maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
    )
}
