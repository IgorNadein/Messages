package com.afkanerd.deku.messages.domain

/** Models exposed above the messaging/domain boundary. They contain no transport details. */
data class ConversationThread(
    val id: Int,
    val address: String,
    val displayName: String,
    val avatarUri: String?,
    val snippet: String,
    val timestampMillis: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
)

data class ConversationGroup(
    val id: String,
    val name: String,
    val threadIds: Set<Int>,
)

enum class ConversationFolder {
    INBOX,
    ARCHIVED,
    DRAFTS,
    MUTED,
    BLOCKED,
    UNREAD,
}

enum class ConversationThreadAction {
    PIN,
    UNPIN,
    MUTE,
    UNMUTE,
    ARCHIVE,
    UNARCHIVE,
    DELETE,
}

enum class MessageDirection {
    INCOMING,
    OUTGOING,
}

enum class DeliveryState {
    RECEIVED,
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
}

data class MessageAuthor(
    val address: String,
    val displayName: String,
    val avatarUri: String?,
)

enum class ConversationSecurityState {
    PLAIN,
    NEGOTIATING,
    REQUEST_RECEIVED,
    SECURE_UNVERIFIED,
    SECURE_VERIFIED,
    KEY_CHANGED,
    RECOVERY_REQUIRED,
}

sealed interface TimelineItem {
    val stableId: String
    val timestampMillis: Long

    data class Text(
        override val stableId: String,
        override val timestampMillis: Long,
        val text: String,
        val direction: MessageDirection,
        val deliveryState: DeliveryState,
        val isSecure: Boolean,
        val subscriptionId: Long? = null,
        val isFavorite: Boolean = false,
        val author: MessageAuthor? = null,
    ) : TimelineItem

    data class Media(
        override val stableId: String,
        override val timestampMillis: Long,
        val uri: String?,
        val fileName: String?,
        val mimeType: String?,
        val caption: String?,
        val direction: MessageDirection,
        val deliveryState: DeliveryState,
        val isSecure: Boolean = false,
        val subscriptionId: Long? = null,
        val isFavorite: Boolean = false,
        val author: MessageAuthor? = null,
    ) : TimelineItem

    data class SecurityEvent(
        override val stableId: String,
        override val timestampMillis: Long,
        val kind: SecurityEventKind,
        val direction: MessageDirection? = null,
    ) : TimelineItem
}

enum class SecurityEventKind {
    REQUEST_SENT,
    REQUEST_RECEIVED,
    SESSION_ESTABLISHED,
    DECRYPTION_FAILED,
}

enum class AttachmentKind {
    PHOTO,
    VOICE,
    FILE,
}

enum class AttachmentTransferState {
    PREPARING,
    WAITING,
    OFFERED,
    SENDING,
    RECEIVING,
    PAUSED,
    RETRYING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class AttachmentTransfer(
    val stableId: String,
    val timestampMillis: Long,
    val direction: MessageDirection,
    val kind: AttachmentKind,
    val fileName: String,
    val mimeType: String,
    val encodedBytes: Long,
    val completedSms: Int,
    val totalSms: Int,
    val state: AttachmentTransferState,
    val completedPath: String?,
    /** Local source while sending, or committed file after receiving. Used only for previews. */
    val previewPath: String? = completedPath,
    val durationMillis: Long,
    val hasError: Boolean,
    val errorMessage: String? = null,
    val isSecure: Boolean = true,
    val transport: MediaTransport = MediaTransport.DATA_SMS,
)

enum class AttachmentAction {
    ACCEPT,
    REJECT,
    CANCEL,
    CONTINUE,
    CONTINUE_WITH_STANDARD_SMS,
}

data class PreparedAttachment(
    val sourceUri: String,
    val kind: AttachmentKind,
    val mimeType: String,
    val fileName: String,
    val originalBytes: Long,
    val encodedBytes: Long,
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sampleRate: Int = 0,
    val durationMillis: Long = 0,
)

sealed interface AttachmentPrepareResult {
    data object Queued : AttachmentPrepareResult
    data class Failed(val reason: String) : AttachmentPrepareResult
}

data class ConversationHeader(
    val threadId: Int,
    val address: String,
    val displayName: String,
    val avatarUri: String?,
    val subscriptionId: Long,
    val subscriptions: List<SimSubscription>,
    val securityState: ConversationSecurityState,
    val secureSendingEnabled: Boolean = true,
    /** A secure send is desired, even if the route currently needs repair. */
    val secureSendingRequested: Boolean = secureSendingEnabled,
    val isMuted: Boolean = false,
    val participants: List<MessageRecipient> = emptyList(),
    val availableContactNumbers: List<MessageRecipient> = emptyList(),
    val relatedThreadIds: List<Int> = listOf(threadId),
    val securityChannels: List<SecureChannel> = emptyList(),
    val canReply: Boolean = true,
) {
    val isGroupConversation: Boolean
        get() = participants.size > 1
}

data class SecureChannel(
    val remoteAddress: String,
    val remoteLabel: String?,
    val subscriptionId: Long,
    val subscriptionName: String,
    val state: ConversationSecurityState,
    val encryptFutureMessages: Boolean,
) {
    val isEstablished: Boolean
        get() = state == ConversationSecurityState.SECURE_UNVERIFIED ||
            state == ConversationSecurityState.SECURE_VERIFIED
}

data class SimSubscription(
    val id: Long,
    val displayName: String,
    val slotIndex: Int,
)

data class MessageRecipient(
    val id: Long,
    val address: String,
    val displayName: String,
    val avatarUri: String?,
    val isDirectEntry: Boolean = false,
    val label: String? = null,
)

data class ImportProgress(
    val completedThreads: Int,
    val totalThreads: Int,
)

sealed interface ImportResult {
    data object RoleRequired : ImportResult
    data object AlreadyReady : ImportResult
    data class Imported(val threadCount: Int) : ImportResult
    data class Failed(val cause: Throwable) : ImportResult
}

sealed interface SendResult {
    data class Sent(val localMessageId: Long) : SendResult
    data class BlockedBySecurity(val reason: String) : SendResult
    data class Failed(val reason: String) : SendResult
}

sealed interface SecureSessionActionResult {
    data object RequestSent : SecureSessionActionResult
    data class Failed(val reason: String) : SecureSessionActionResult
}
