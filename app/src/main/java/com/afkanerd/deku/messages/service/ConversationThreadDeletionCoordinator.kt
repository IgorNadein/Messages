package com.afkanerd.deku.messages.service

/** Keeps the legacy deletion order: local cache first, optional Android SMS storage second. */
internal object ConversationThreadDeletionCoordinator {
    fun delete(
        threadId: Int,
        deleteFromSystemDatabase: Boolean,
        deleteLocal: () -> Unit,
        deleteSystem: (String) -> Unit,
    ): Boolean = runCatching {
        deleteLocal()
        if(deleteFromSystemDatabase) deleteSystem(threadId.toString())
    }.isSuccess
}
