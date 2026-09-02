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
}
