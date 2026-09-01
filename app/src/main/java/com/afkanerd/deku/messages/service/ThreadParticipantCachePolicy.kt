package com.afkanerd.deku.messages.service

/** A Telephony thread id represents an immutable recipient set, so one provider read is enough. */
internal object ThreadParticipantCachePolicy {
    fun shouldQueryProvider(cached: Collection<String>): Boolean = cached.isEmpty()

    fun resolve(
        cached: Collection<String>,
        provider: Collection<String>,
        fallback: Collection<String>,
    ): List<String> = when {
        cached.isNotEmpty() -> cached.toList()
        provider.isNotEmpty() -> provider.toList()
        else -> fallback.toList()
    }

    /**
     * Old imports can contain two Telephony thread ids which normalize to the same address.
     * Threads.address is unique, so refreshing that cache entry must never crash the inbox.
     */
    fun tryUpdateAddress(
        threadId: Int,
        owningThreadId: Int?,
        update: () -> Unit,
    ): Boolean {
        if(owningThreadId != null && owningThreadId != threadId) return false
        return runCatching(update).isSuccess
    }
}
