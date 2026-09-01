package com.afkanerd.deku.messages.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface MessageService {
    fun isDefaultSmsApp(): Boolean

    fun hasContactAccess(): Boolean

    fun isContactPromptCompleted(): Boolean

    fun completeContactPrompt()

    fun conversationThreads(): Flow<PagingData<ConversationThread>>

    fun conversationThreads(folder: ConversationFolder): Flow<PagingData<ConversationThread>> =
        if(folder == ConversationFolder.INBOX) conversationThreads()
        else kotlinx.coroutines.flow.flowOf(PagingData.empty())

    fun conversationThreads(
        folder: ConversationFolder,
        groupId: String?,
    ): Flow<PagingData<ConversationThread>> = if(groupId == null) {
        conversationThreads(folder)
    } else {
        kotlinx.coroutines.flow.flowOf(PagingData.empty())
    }

    fun conversationGroups(): Flow<List<ConversationGroup>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun createConversationGroup(name: String, threadIds: Set<Int>): String? = null

    suspend fun updateConversationGroup(
        id: String,
        name: String,
        threadIds: Set<Int>,
    ): Boolean = false

    suspend fun deleteConversationGroup(id: String): Boolean = false

    fun unreadMessageCount(): Flow<Int> = kotlinx.coroutines.flow.flowOf(0)

    suspend fun updateConversationThread(
        threadId: Int,
        action: ConversationThreadAction,
    ): Boolean = false

    suspend fun markAllConversationsRead(): Boolean = false

    suspend fun exportMessages(destinationUri: String): Boolean = false

    fun gatewayConfigurations(): Flow<List<GatewaySummary>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun loadGatewayDraft(id: Long): GatewayDraft? = null

    suspend fun saveGateway(id: Long?, draft: GatewayDraft): Boolean = false

    suspend fun deleteGateway(id: Long): Boolean = false

    suspend fun saveMedia(sourceUri: String, destinationUri: String): Boolean = false

    fun remoteListeners(): Flow<List<RemoteListenerSummary>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun loadRemoteListenerDraft(id: Long): RemoteListenerDraft? = null

    suspend fun saveRemoteListener(id: Long?, draft: RemoteListenerDraft): Boolean = false

    suspend fun deleteRemoteListener(id: Long): Boolean = false

    fun remoteQueues(listenerId: Long): Flow<List<RemoteQueueSummary>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun loadRemoteQueueDraft(id: Long): RemoteQueueDraft? = null

    suspend fun suggestRemoteBindings(exchange: String): List<String> = emptyList()

    suspend fun saveRemoteQueue(
        listenerId: Long,
        id: Long?,
        draft: RemoteQueueDraft,
    ): Boolean = false

    suspend fun deleteRemoteQueue(listenerId: Long, id: Long): Boolean = false

    suspend fun toggleRemoteListener(id: Long): RemoteListenerToggleResult =
        RemoteListenerToggleResult.FAILED

    fun routingHistory(): Flow<List<RoutingHistoryItem>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun searchThreads(query: String, address: String? = null): Flow<PagingData<ConversationThread>> =
        kotlinx.coroutines.flow.flowOf(PagingData.empty())

    suspend fun searchRecipients(query: String): List<MessageRecipient> = emptyList()

    fun activeSimSubscriptions(): List<SimSubscription> = emptyList()

    fun timeline(threadId: Int): Flow<PagingData<TimelineItem>>

    fun attachmentTransfers(address: String): Flow<List<AttachmentTransfer>>

    suspend fun performAttachmentAction(transferId: String, action: AttachmentAction)

    suspend fun prepareAttachment(
        address: String,
        subscriptionId: Long,
        attachment: PreparedAttachment,
    ): AttachmentPrepareResult

    suspend fun importIfNeeded(
        onProgress: suspend (ImportProgress) -> Unit = {},
    ): ImportResult

    /** True when the Telephony provider has already been copied into the local message store. */
    fun isMessageStoreReady(): Boolean = false

    suspend fun conversationHeader(address: String, threadId: Int?): ConversationHeader

    suspend fun conversationHeader(
        addresses: List<String>,
        threadId: Int?,
    ): ConversationHeader = conversationHeader(addresses.first(), threadId)

    suspend fun loadDraft(threadId: Int): String

    suspend fun saveDraft(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    )

    suspend fun selectSubscription(address: String, subscriptionId: Long)

    suspend fun isContactBlocked(address: String): Boolean = false

    suspend fun setContactBlocked(address: String, blocked: Boolean): Boolean = false

    suspend fun sendText(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult

    suspend fun sendMms(
        addresses: List<String>,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult = SendResult.Failed("MMS is not supported")

    suspend fun sendNotificationReply(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult = sendText(address, threadId, subscriptionId, text)

    suspend fun requestOrRepairSecureSession(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        forceRenewal: Boolean = false,
    ): SecureSessionActionResult

    suspend fun securityFingerprint(address: String): String?

    suspend fun localSecurityQrPayload(): String? = null

    suspend fun verifyContactIdentity(address: String, qrPayload: String): Boolean = false

    suspend fun acceptChangedIdentityAndRepair(
        address: String,
        threadId: Int,
        subscriptionId: Long,
    ): SecureSessionActionResult
}
