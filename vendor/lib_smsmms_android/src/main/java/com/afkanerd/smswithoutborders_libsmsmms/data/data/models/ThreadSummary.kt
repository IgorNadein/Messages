package com.afkanerd.smswithoutborders_libsmsmms.data.data.models

/** Read-only projection for the inbox. It does not alter the persistent Room schema. */
data class ThreadSummary(
    val threadId: Int,
    val address: String,
    val snippet: String,
    val date: Long,
    val isPinned: Boolean,
    val isMute: Boolean,
    val isArchive: Boolean,
    val isBlocked: Boolean,
    val unreadCount: Int,
    val smsData: ByteArray?,
    val secureTransportText: String?,
    val participantAddresses: String? = null,
)
