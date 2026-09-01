package com.afkanerd.deku.messages.service

import androidx.paging.PagingConfig

/** Bounded timeline window: database size must never become Compose working-set size. */
internal object MessagePagingPolicy {
    const val PAGE_SIZE = 50
    const val INITIAL_LOAD_SIZE = PAGE_SIZE * 2
    const val PREFETCH_DISTANCE = PAGE_SIZE * 2
    const val MAX_CACHED_ITEMS = PAGE_SIZE * 10

    fun config() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = INITIAL_LOAD_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false,
        maxSize = MAX_CACHED_ITEMS,
    )
}
