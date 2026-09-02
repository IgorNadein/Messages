package com.afkanerd.deku.messages.service

/** One visible conversation per contact (or normalized recipient set), regardless of Telephony aliases. */
internal object ConversationThreadIdentityPolicy {
    fun key(normalizedParticipants: List<String>, contactId: Long?): String =
        if(normalizedParticipants.size == 1 && contactId != null) {
            "contact:$contactId"
        } else {
            "addresses:${normalizedParticipants.distinct().sorted().joinToString(",")}"
        }

    fun relatedThreadIds(
        participantsByThreadId: Map<Int, List<String>>,
        contactAddresses: Collection<String>,
        normalize: (String) -> String,
    ): List<Int> {
        val normalizedContactAddresses = contactAddresses.map(normalize).toSet()
        return participantsByThreadId.mapNotNull { (threadId, participants) ->
            threadId.takeIf {
                participants.singleOrNull()?.let(normalize) in normalizedContactAddresses
            }
        }
    }
}
