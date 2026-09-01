package com.afkanerd.deku.messages.service

import android.Manifest
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import androidx.lifecycle.asFlow
import androidx.work.WorkManager
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.RemoteListeners.Models.RemoteListenersHandler
import com.afkanerd.deku.RemoteListeners.RemoteListenerConnectionService
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationFolder
import com.afkanerd.deku.messages.domain.ConversationGroup
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.ConversationThreadAction
import com.afkanerd.deku.messages.domain.ImportProgress
import com.afkanerd.deku.messages.domain.ImportResult
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.MessageRecipient
import com.afkanerd.deku.messages.domain.MessageAuthor
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewaySummary
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerSummary
import com.afkanerd.deku.messages.domain.RemoteListenerToggleResult
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import com.afkanerd.deku.messages.domain.RemoteQueueSummary
import com.afkanerd.deku.messages.domain.RoutingHistoryItem
import com.afkanerd.deku.messages.domain.SecureSessionActionResult
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.domain.SimSubscription
import com.afkanerd.deku.messages.domain.TimelineItem
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.security.SecureSessionStatusResolver
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityKeyManager
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityVerificationStatus
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SavedEncryptedModes
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.getEncryptionModeStatesSync
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.removeEncryptionModeStates
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.removeEncryptionRatchetStates
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.exportRawWithColumnGuesses
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDefaultSimSubscription
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getNativesLoaded
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getSimCardInformation
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getThreadId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getThreadParticipantAddresses
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.isDefault
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.deleteSmsThreads
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.loadRawSmsMmsDb
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.loadRawThreads
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.makeE16PhoneNumber
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.retrieveContactName
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.retrieveContactPhoto
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendSms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendMms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.setNativesLoaded
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetConversationsSubscriptionId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetConversationsSubscriptionId
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsBlockedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AndroidMessageService(context: Context) : MessageService {
    private val appContext = context.applicationContext
    private val importMutex = Mutex()
    private val conversationGroupPreferences by lazy {
        ConversationGroupPreferences(appContext)
    }
    private val contactNames = ConcurrentHashMap<String, String>()
    private val contactPhotos = ConcurrentHashMap<String, String>()

    override fun isDefaultSmsApp(): Boolean = appContext.isDefault()

    override fun hasContactAccess(): Boolean = hasContactsPermission()

    override fun isContactPromptCompleted(): Boolean = appContext
        .getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(CONTACT_PROMPT_COMPLETED, false)

    override fun completeContactPrompt() {
        appContext.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean(CONTACT_PROMPT_COMPLETED, true) }
        contactNames.clear()
        contactPhotos.clear()
    }

    override fun conversationThreads(): Flow<PagingData<ConversationThread>> =
        conversationThreads(ConversationFolder.INBOX)

    override fun conversationThreads(
        folder: ConversationFolder,
    ): Flow<PagingData<ConversationThread>> = conversationThreadsForIds(folder, null)

    private fun conversationThreadsForIds(
        folder: ConversationFolder,
        includedThreadIds: List<Int>?,
    ): Flow<PagingData<ConversationThread>> {
        val database = appContext.getDatabase()
        val threadsDao = requireNotNull(database.threadsDao())
        return Pager(
            ConversationPagingPolicy.config()
        ) {
            includedThreadIds?.let { ids ->
                threadsDao.getThreadSummaries(folder.ordinal, ids)
            } ?: threadsDao.getThreadSummaries(folder.ordinal)
        }.flow.map { page ->
            page.map { thread ->
                MessageStorageDispatcher.read {
                    val snippet = when {
                        thread.smsData != null -> SECURITY_UPDATE_SNIPPET
                        thread.secureTransportText != null -> DECRYPTION_FAILED_SNIPPET
                        else -> SearchSnippetPolicy.safeSnippet(
                            thread.snippet,
                            appContext.getString(R.string.oneui_secure_recovery),
                        )
                    }
                    val participants = resolveThreadParticipants(
                        threadId = thread.threadId,
                        fallbackAddress = thread.address,
                    )
                    val threadAddress = participants.joinToString(GROUP_ADDRESS_SEPARATOR)
                    ConversationThread(
                        id = thread.threadId,
                        address = threadAddress,
                        displayName = contactName(threadAddress),
                        avatarUri = contactPhoto(threadAddress),
                        snippet = snippet,
                        timestampMillis = thread.date,
                        unreadCount = thread.unreadCount,
                        isPinned = thread.isPinned,
                        isMuted = thread.isMute,
                        isArchived = thread.isArchive,
                        isBlocked = thread.isBlocked,
                    )
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun unreadMessageCount(): Flow<Int> =
        requireNotNull(appContext.getDatabase().threadsDao())
            .unreadMessageCount()
            .flowOn(Dispatchers.IO)

    override fun conversationGroups(): Flow<List<ConversationGroup>> =
        conversationGroupPreferences.groups

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun conversationThreads(
        folder: ConversationFolder,
        groupId: String?,
    ): Flow<PagingData<ConversationThread>> {
        if(groupId == null) return conversationThreads(folder)
        return conversationGroupPreferences.groups.flatMapLatest { groups ->
            val threadIds = groups.firstOrNull { it.id == groupId }?.threadIds
                ?: return@flatMapLatest flowOf(PagingData.empty())
            if(threadIds.isEmpty()) flowOf(PagingData.empty())
            else conversationThreadsForIds(folder, threadIds.sorted())
        }
    }

    override suspend fun createConversationGroup(
        name: String,
        threadIds: Set<Int>,
    ): String? = withContext(Dispatchers.IO) {
        conversationGroupPreferences.create(name, threadIds)
    }

    override suspend fun updateConversationGroup(
        id: String,
        name: String,
        threadIds: Set<Int>,
    ): Boolean = withContext(Dispatchers.IO) {
        conversationGroupPreferences.update(id, name, threadIds)
    }

    override suspend fun deleteConversationGroup(id: String): Boolean =
        withContext(Dispatchers.IO) {
            conversationGroupPreferences.delete(id)
        }

    override suspend fun updateConversationThread(
        threadId: Int,
        action: ConversationThreadAction,
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = requireNotNull(appContext.getDatabase().threadsDao())
        val current = dao.get(threadId) ?: return@withContext false
        if(action == ConversationThreadAction.DELETE) {
            return@withContext ConversationThreadDeletionCoordinator.delete(
                threadId = threadId,
                deleteFromSystemDatabase = appContext.settingsGetDeleteSystem,
                deleteLocal = { dao.delete(listOf(current)) },
                deleteSystem = { systemThreadId ->
                    appContext.deleteSmsThreads(arrayOf(systemThreadId))
                },
            )
        }
        val updated = when(action) {
            ConversationThreadAction.PIN -> current.copy(isPinned = true)
            ConversationThreadAction.UNPIN -> current.copy(isPinned = false)
            ConversationThreadAction.MUTE -> current.copy(isMute = true)
            ConversationThreadAction.UNMUTE -> current.copy(isMute = false)
            ConversationThreadAction.ARCHIVE -> current.copy(isArchive = true)
            ConversationThreadAction.UNARCHIVE -> current.copy(isArchive = false)
            ConversationThreadAction.DELETE -> error("Handled before thread update")
        }
        dao.update(listOf(updated)) > 0
    }

    override suspend fun markAllConversationsRead(): Boolean = withContext(Dispatchers.IO) {
        val dao = requireNotNull(appContext.getDatabase().threadsDao())
        dao.markAllAsRead()
        true
    }

    override suspend fun exportMessages(destinationUri: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = appContext.exportRawWithColumnGuesses().encodeToByteArray()
                appContext.contentResolver.openOutputStream(destinationUri.toUri(), "wt")?.use {
                    it.write(payload)
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    override fun gatewayConfigurations(): Flow<List<GatewaySummary>> =
        Datastore.getDatastore(appContext)
            .gatewayServerDAO()
            .all
            .asFlow()
            .map { entities -> entities.map(GatewayConfigurationMapper::toSummary) }
            .flowOn(Dispatchers.IO)

    override suspend fun loadGatewayDraft(id: Long): GatewayDraft? =
        withContext(Dispatchers.IO) {
            Datastore.getDatastore(appContext)
                .gatewayServerDAO()[id.toString()]
                ?.let(GatewayConfigurationMapper::toDraft)
        }

    override suspend fun saveGateway(id: Long?, draft: GatewayDraft): Boolean =
        withContext(Dispatchers.IO) {
            if(!draft.isValid()) return@withContext false
            runCatching {
                Datastore.getDatastore(appContext).gatewayServerDAO().insert(
                    GatewayConfigurationMapper.toEntity(
                        id = id ?: 0,
                        draft = draft,
                        nowMillis = System.currentTimeMillis(),
                    )
                )
            }.isSuccess
        }

    override suspend fun deleteGateway(id: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dao = Datastore.getDatastore(appContext).gatewayServerDAO()
            val entity = dao[id.toString()] ?: return@withContext false
            dao.delete(entity)
        }.isSuccess
    }

    override suspend fun saveMedia(sourceUri: String, destinationUri: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = appContext.contentResolver
                resolver.openInputStream(sourceUri.toUri())?.use { input ->
                    resolver.openOutputStream(destinationUri.toUri(), "w")?.use { output ->
                        input.copyTo(output)
                    } ?: error("Unable to open media destination")
                } ?: error("Unable to open media source")
                true
            }.getOrDefault(false)
        }

    override fun remoteListeners(): Flow<List<RemoteListenerSummary>> {
        val dao = Datastore.getDatastore(appContext).remoteListenerDAO()
        return combine(
            dao.fetch().asFlow(),
            RemoteListenerConnectionService.connectedListenerIds,
        ) { listeners, connectedIds ->
            listeners.map { listener ->
                RemoteListenerConfigurationMapper.toSummary(
                    entity = listener,
                    connected = listener.id in connectedIds,
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun loadRemoteListenerDraft(id: Long): RemoteListenerDraft? =
        withContext(Dispatchers.IO) {
            runCatching {
                Datastore.getDatastore(appContext).remoteListenerDAO().fetch(id)
            }.getOrNull()?.let(RemoteListenerConfigurationMapper::toDraft)
        }

    override suspend fun saveRemoteListener(
        id: Long?,
        draft: RemoteListenerDraft,
    ): Boolean = withContext(Dispatchers.IO) {
        if(!draft.isValid()) return@withContext false
        runCatching {
            val dao = Datastore.getDatastore(appContext).remoteListenerDAO()
            val existing = id?.let { dao.fetch(it) }
            if(id != null && existing == null) return@withContext false
            val entity = RemoteListenerConfigurationMapper.toEntity(
                existing = existing,
                draft = draft,
                nowMillis = System.currentTimeMillis(),
            )
            if(existing == null) dao.insert(entity) else dao.update(entity)
            if(entity.activated) RemoteListenersHandler.onOffAgain(appContext, entity)
            true
        }.getOrDefault(false)
    }

    override suspend fun deleteRemoteListener(id: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val datastore = Datastore.getDatastore(appContext)
                val listener = datastore.remoteListenerDAO().fetch(id)
                if(listener.activated) {
                    listener.activated = false
                    RemoteListenersHandler.stopListening(appContext, listener)
                }
                datastore.remoteListenersQueuesDao().deleteRemoteListenerQueue(id)
                datastore.remoteListenerDAO().delete(listener) > 0
            }.getOrDefault(false)
        }

    override fun remoteQueues(listenerId: Long): Flow<List<RemoteQueueSummary>> =
        Datastore.getDatastore(appContext)
            .remoteListenersQueuesDao()
            .fetchRemoteListenerQueue(listenerId)
            .asFlow()
            .map { queues -> queues.map(RemoteListenerConfigurationMapper::toQueueSummary) }
            .flowOn(Dispatchers.IO)

    override suspend fun loadRemoteQueueDraft(id: Long): RemoteQueueDraft? =
        withContext(Dispatchers.IO) {
            Datastore.getDatastore(appContext)
                .remoteListenersQueuesDao()
                .fetch(id)
                ?.let(RemoteListenerConfigurationMapper::toQueueDraft)
        }

    override suspend fun suggestRemoteBindings(exchange: String): List<String> =
        withContext(Dispatchers.IO) {
            if(exchange.isBlank()) emptyList()
            else runCatching {
                RemoteListenersHandler.getPublisherDetails(appContext, exchange.trim())
            }.getOrDefault(emptyList())
        }

    override suspend fun saveRemoteQueue(
        listenerId: Long,
        id: Long?,
        draft: RemoteQueueDraft,
    ): Boolean = withContext(Dispatchers.IO) {
        if(!draft.isValid()) return@withContext false
        runCatching {
            val datastore = Datastore.getDatastore(appContext)
            val listener = datastore.remoteListenerDAO().fetch(listenerId)
            datastore.remoteListenersQueuesDao().insert(
                RemoteListenerConfigurationMapper.toQueueEntity(
                    id = id ?: 0,
                    listenerId = listenerId,
                    draft = draft,
                )
            )
            if(listener.activated) RemoteListenersHandler.onOffAgain(appContext, listener)
            true
        }.getOrDefault(false)
    }

    override suspend fun deleteRemoteQueue(listenerId: Long, id: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val datastore = Datastore.getDatastore(appContext)
                val queue = datastore.remoteListenersQueuesDao().fetch(id)
                    ?: return@withContext false
                if(queue.gatewayClientId != listenerId) return@withContext false
                datastore.remoteListenersQueuesDao().delete(queue)
                val listener = datastore.remoteListenerDAO().fetch(listenerId)
                if(listener.activated) {
                    val hasQueues = datastore.remoteListenersQueuesDao()
                        .fetchRemoteListenersQueues(listenerId)
                        .isNotEmpty()
                    if(hasQueues) {
                        RemoteListenersHandler.onOffAgain(appContext, listener)
                    } else {
                        listener.activated = false
                        RemoteListenersHandler.stopListening(appContext, listener)
                    }
                }
                true
            }.getOrDefault(false)
        }

    override suspend fun toggleRemoteListener(id: Long): RemoteListenerToggleResult =
        withContext(Dispatchers.IO) {
            if(!hasRemoteListenerPermissions()) {
                return@withContext RemoteListenerToggleResult.PERMISSION_REQUIRED
            }
            runCatching {
                val datastore = Datastore.getDatastore(appContext)
                val listener = datastore.remoteListenerDAO().fetch(id)
                if(listener.activated) {
                    listener.activated = false
                    RemoteListenersHandler.stopListening(appContext, listener)
                    RemoteListenerToggleResult.DEACTIVATED
                } else if(
                    datastore.remoteListenersQueuesDao()
                        .fetchRemoteListenersQueues(id)
                        .isEmpty()
                ) {
                    RemoteListenerToggleResult.MISSING_QUEUES
                } else {
                    listener.activated = true
                    RemoteListenersHandler.startListening(appContext, listener)
                    RemoteListenerToggleResult.ACTIVATED
                }
            }.getOrDefault(RemoteListenerToggleResult.FAILED)
        }

    override fun routingHistory(): Flow<List<RoutingHistoryItem>> =
        WorkManager.getInstance(appContext)
            .getWorkInfosByTagFlow(RouterHandler.TAG_NAME_GATEWAY_SERVER)
            .map { workInfos ->
                val conversationsDao = appContext.getDatabase().conversationsDao()
                    ?: return@map emptyList()
                workInfos.mapNotNull { workInfo ->
                    val tags = RoutingHistoryMapper.parseTags(workInfo.tags)
                        ?: return@mapNotNull null
                    val conversation = runCatching {
                        conversationsDao.getConversation(tags.messageId)
                    }.getOrNull()
                    val address = conversation?.sms?.address
                    RoutingHistoryMapper.toItem(
                        workId = workInfo.id.toString(),
                        tags = workInfo.tags,
                        workerState = workInfo.state.name,
                        conversation = conversation,
                        displayName = address?.let(::contactName),
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    override fun searchThreads(
        query: String,
        address: String?,
    ): Flow<PagingData<ConversationThread>> {
        if(query.length < 2) return kotlinx.coroutines.flow.flowOf(PagingData.empty())
        val threadsDao = requireNotNull(appContext.getDatabase().threadsDao())
        val normalizedAddress = address?.let(appContext::makeE16PhoneNumber)
        return Pager(
            PagingConfig(
                pageSize = THREAD_PAGE_SIZE,
                enablePlaceholders = false,
                maxSize = THREAD_PAGE_SIZE * 4,
            )
        ) {
            if(normalizedAddress == null) threadsDao.search(query)
            else threadsDao.searchByAddress(query, normalizedAddress)
        }.flow.map { page ->
            page.map { thread ->
                MessageStorageDispatcher.read {
                    ConversationThread(
                        id = thread.threadId,
                        address = thread.address,
                        displayName = contactName(thread.address),
                        avatarUri = contactPhoto(thread.address),
                        snippet = SearchSnippetPolicy.safeSnippet(
                            thread.snippet,
                            appContext.getString(R.string.oneui_secure_recovery),
                        ),
                        timestampMillis = thread.date,
                        unreadCount = if(thread.unread) 1 else 0,
                        isPinned = thread.isPinned,
                        isMuted = thread.isMute,
                        isArchived = thread.isArchive,
                        isBlocked = thread.isBlocked,
                    )
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchRecipients(query: String): List<MessageRecipient> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            val direct = trimmed.takeIf(::looksLikeAddress)?.let { address ->
                MessageRecipient(
                    id = Long.MIN_VALUE,
                    address = address,
                    displayName = address,
                    avatarUri = null,
                    isDirectEntry = true,
                )
            }
            if(ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) return@withContext listOfNotNull(direct)

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            )
            val normalizedQuery = trimmed.lowercase()
            val digitQuery = trimmed.filter(Char::isDigit)
            val seen = HashSet<String>()
            val contacts = ArrayList<MessageRecipient>()
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val numberIndex = cursor.getColumnIndexOrThrow(projection[1])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[2])
                val photoIndex = cursor.getColumnIndexOrThrow(projection[3])
                while(cursor.moveToNext() && contacts.size < MAX_RECIPIENT_RESULTS) {
                    val number = cursor.getString(numberIndex).orEmpty()
                    val name = cursor.getString(nameIndex).orEmpty().ifBlank { number }
                    val matchesNumber = digitQuery.isNotEmpty() &&
                        number.filter(Char::isDigit).contains(digitQuery)
                    if(normalizedQuery.isNotEmpty() &&
                        !name.lowercase().contains(normalizedQuery) && !matchesNumber
                    ) continue
                    val normalized = runCatching { appContext.makeE16PhoneNumber(number) }
                        .getOrDefault(number)
                    if(!seen.add(normalized)) continue
                    contacts += MessageRecipient(
                        id = cursor.getLong(idIndex),
                        address = normalized,
                        displayName = name,
                        avatarUri = cursor.getString(photoIndex),
                    )
                }
            }
            buildList {
                if(direct != null && seen.add(direct.address)) add(direct)
                addAll(contacts)
            }
        }

    override fun timeline(threadId: Int): Flow<PagingData<TimelineItem>> {
        val dao = requireNotNull(appContext.getDatabase().conversationsDao())
        return flow {
            val participants = MessageStorageDispatcher.read {
                resolveThreadParticipants(
                    threadId = threadId,
                    fallbackAddress = dao.getThread(threadId)?.address.orEmpty(),
                )
            }
            val secureConversation = participants.singleOrNull()?.let { address ->
                SecureSessionStatusResolver.resolve(appContext, address) != SecureSessionStatus.PLAIN
            } == true
            emitAll(Pager(
                MessagePagingPolicy.config()
            ) { dao.getConversations(threadId) }.flow.map { page ->
                page
                    .filter(ConversationEntityMapper::shouldExpose)
                    .map { entity ->
                        val author = entity.sender_address?.takeIf(String::isNotBlank)?.let {
                            MessageAuthor(
                                address = it,
                                displayName = contactName(it),
                                avatarUri = contactPhoto(it),
                            )
                        }
                        ConversationEntityMapper.map(entity, secureConversation, author)
                    }
            })
        }.flowOn(Dispatchers.IO)
    }

    override fun attachmentTransfers(address: String): Flow<List<AttachmentTransfer>> {
        val recipient = splitStoredAddresses(address).singleOrNull()
            ?: return flowOf(emptyList())
        val normalizedAddress = appContext.makeE16PhoneNumber(recipient)
        return Datastore.getDatastore(appContext)
            .attachmentTransferDao()
            .observeForAddress(normalizedAddress)
            .map { transfers -> transfers.map(AttachmentTransferMapper::map) }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun performAttachmentAction(transferId: String, action: AttachmentAction) {
        withContext(Dispatchers.IO) {
            val manager = AttachmentManager.get(appContext)
            when(action) {
                AttachmentAction.ACCEPT -> manager.accept(transferId)
                AttachmentAction.REJECT -> manager.reject(transferId)
                AttachmentAction.CANCEL -> manager.cancel(transferId)
            }
        }
    }

    override suspend fun prepareAttachment(
        address: String,
        subscriptionId: Long,
        attachment: PreparedAttachment,
    ): AttachmentPrepareResult = withContext(Dispatchers.IO) {
        try {
            val recipients = splitStoredAddresses(address)
                .map(appContext::makeE16PhoneNumber)
                .distinct()
            val secureOneToOne = recipients.singleOrNull()?.let { recipient ->
                SecureSessionStatusResolver.resolve(appContext, recipient) ==
                    SecureSessionStatus.SECURE_ESTABLISHED
            } == true
            if(!secureOneToOne) {
                val conversation = appContext.sendMms(
                    text = "",
                    addresses = recipients,
                    threadId = appContext.getThreadId(recipients),
                    subscriptionId = subscriptionId,
                    contentUri = attachment.sourceUri.toUri(),
                    filename = attachment.fileName,
                    mimeType = attachment.mimeType,
                )
                return@withContext if(conversation != null) {
                    AttachmentPrepareResult.Queued
                } else {
                    AttachmentPrepareResult.Failed("MMS attachment was not queued")
                }
            }
            AttachmentManager.get(appContext).prepareFile(
                address = recipients.single(),
                subscriptionId = subscriptionId.toInt(),
                source = attachment.sourceUri.toUri(),
                mediaType = when(attachment.kind) {
                    AttachmentKind.PHOTO -> AttachmentManifest.MediaType.PHOTO
                    AttachmentKind.VOICE -> AttachmentManifest.MediaType.VOICE
                    AttachmentKind.FILE -> AttachmentManifest.MediaType.FILE
                },
                mimeType = attachment.mimeType,
                filename = attachment.fileName,
                originalSize = attachment.originalBytes,
                metadata = AttachmentManager.MediaMetadata(
                    codec = attachment.codec,
                    width = attachment.width,
                    height = attachment.height,
                    sampleRate = attachment.sampleRate,
                    durationMs = attachment.durationMillis,
                ),
            )
            AttachmentPrepareResult.Queued
        } catch(error: Throwable) {
            AttachmentPrepareResult.Failed(error.message ?: "Unable to prepare attachment")
        }
    }

    override suspend fun importIfNeeded(
        onProgress: suspend (ImportProgress) -> Unit,
    ): ImportResult = importMutex.withLock {
        if(!isDefaultSmsApp()) return ImportResult.RoleRequired
        if(appContext.getNativesLoaded()) return ImportResult.AlreadyReady

        withContext(Dispatchers.IO) {
            try {
                val rawThreads = appContext.loadRawThreads()
                val batch = MessageImportBatch.run(
                    items = rawThreads,
                    importOne = { thread ->
                        val conversations = appContext.loadRawSmsMmsDb(thread.first, thread.second)
                        appContext.getDatabase().conversationsDao()
                            ?.insertAllThreads(conversations, thread.second)
                    },
                    onProgress = { completed, total ->
                        onProgress(ImportProgress(completed, total))
                    },
                )
                if(!batch.canUseCache(rawThreads.size)) {
                    throw batch.firstFailure ?: IllegalStateException("Message import failed")
                }
                appContext.setNativesLoaded(true)
                ImportResult.Imported(batch.importedCount)
            } catch(error: Throwable) {
                ImportResult.Failed(error)
            }
        }
    }

    override fun isMessageStoreReady(): Boolean = appContext.getNativesLoaded()

    override suspend fun conversationHeader(
        address: String,
        threadId: Int?,
    ): ConversationHeader = conversationHeader(splitStoredAddresses(address), threadId)

    override suspend fun conversationHeader(
        addresses: List<String>,
        threadId: Int?,
    ): ConversationHeader = withContext(Dispatchers.IO) {
        val normalizedAddresses = addresses
            .map(appContext::makeE16PhoneNumber)
            .filter(String::isNotBlank)
            .distinct()
        require(normalizedAddresses.isNotEmpty()) { "At least one recipient is required" }
        val resolvedThreadId = threadId ?: appContext.getThreadId(normalizedAddresses)
        val participants = resolveThreadParticipants(
            threadId = resolvedThreadId,
            fallbackAddresses = normalizedAddresses,
        )
        val storedAddress = participants.joinToString(GROUP_ADDRESS_SEPARATOR)
        val storedThread = appContext.getDatabase().threadsDao()?.get(resolvedThreadId)
        val subscriptions = activeSubscriptions()
        val rememberedSubscription = appContext
            .settingsGetConversationsSubscriptionId(storedAddress)
            .first()
        val defaultSubscription = appContext.getDefaultSimSubscription() ?: -1L
        val selectedSubscription = rememberedSubscription
            ?.takeIf { saved -> subscriptions.isEmpty() || subscriptions.any { it.id == saved } }
            ?: defaultSubscription
        ConversationHeader(
            threadId = resolvedThreadId,
            address = storedAddress,
            displayName = participants.joinToString(", ") { contactName(it) },
            avatarUri = participants.singleOrNull()?.let(::contactPhoto),
            subscriptionId = selectedSubscription,
            subscriptions = subscriptions,
            securityState = participants.singleOrNull()?.let {
                resolveSecurityState(it)
            } ?: ConversationSecurityState.PLAIN,
            isMuted = storedThread?.isMute == true,
            participants = participants.map { participantAddress ->
                MessageRecipient(
                    id = participantAddress.hashCode().toLong(),
                    address = participantAddress,
                    displayName = contactName(participantAddress),
                    avatarUri = contactPhoto(participantAddress),
                )
            },
        )
    }

    override suspend fun loadDraft(threadId: Int): String = withContext(Dispatchers.IO) {
        appContext.getDatabase().conversationsDao()
            ?.fetchConversationsForType(threadId, Telephony.Sms.MESSAGE_TYPE_DRAFT)
            ?.sms
            ?.body
            .orEmpty()
    }

    override suspend fun saveDraft(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ) = withContext(Dispatchers.IO) {
        val dao = requireNotNull(appContext.getDatabase().conversationsDao())
        val existing = dao.fetchConversationsForType(
            threadId,
            Telephony.Sms.MESSAGE_TYPE_DRAFT,
        )
        if(text.isBlank()) {
            existing?.let { dao.delete(it, false) }
            return@withContext
        }

        val now = System.currentTimeMillis()
        val draft = Conversations(
            id = existing?.id ?: 0,
            sms = SmsMmsNatives.Sms(
                _id = existing?.sms?._id ?: now,
                thread_id = threadId,
                address = address,
                date = now,
                date_sent = now,
                read = 1,
                status = Telephony.Sms.STATUS_PENDING,
                type = Telephony.Sms.MESSAGE_TYPE_DRAFT,
                body = text,
                sub_id = subscriptionId,
            ),
        )
        dao.insert(draft)
    }

    override suspend fun selectSubscription(address: String, subscriptionId: Long) {
        appContext.settingsSetConversationsSubscriptionId(
            normalizeStoredAddress(address),
            subscriptionId,
        )
    }

    override suspend fun isContactBlocked(address: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedAddress = appContext.makeE16PhoneNumber(address)
        runCatching { BlockedNumberContract.isBlocked(appContext, normalizedAddress) }
            .getOrElse {
                appContext.getDatabase().threadsDao()?.get(normalizedAddress)?.isBlocked ?: false
            }
    }

    override suspend fun setContactBlocked(address: String, blocked: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val normalizedAddress = appContext.makeE16PhoneNumber(address)
            runCatching {
                if(blocked) {
                    val values = ContentValues().apply {
                        put(
                            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                            normalizedAddress,
                        )
                    }
                    appContext.contentResolver.insert(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        values,
                    )
                } else {
                    BlockedNumberContract.unblock(appContext, normalizedAddress)
                }
                appContext.getDatabase().threadsDao()
                    ?.setIsBlocked(blocked, listOf(normalizedAddress))
                true
            }.getOrElse { false }
        }

    override suspend fun sendText(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult = withContext(Dispatchers.IO) {
        if(text.isBlank()) return@withContext SendResult.Failed("Message is empty")
        try {
            val conversation = appContext.sendSms(
                text = text,
                address = address,
                threadId = threadId,
                subscriptionId = subscriptionId,
                data = null,
                bundle = Bundle(),
            ) ?: return@withContext SendResult.Failed("Message was not queued")
            SendResult.Sent(conversation.id)
        } catch(error: OutboundSmsBlockedException) {
            SendResult.BlockedBySecurity(error.message ?: "Secure session is not ready")
        } catch(error: SecurityException) {
            SendResult.BlockedBySecurity(error.message ?: "Secure send was blocked")
        } catch(error: Throwable) {
            SendResult.Failed(error.message ?: "Unable to send message")
        }
    }

    override suspend fun sendMms(
        addresses: List<String>,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult = withContext(Dispatchers.IO) {
        if(text.isBlank()) return@withContext SendResult.Failed("Message is empty")
        try {
            val conversation = appContext.sendMms(
                text = text,
                addresses = addresses,
                threadId = threadId,
                subscriptionId = subscriptionId,
            ) ?: return@withContext SendResult.Failed("MMS was not queued")
            SendResult.Sent(conversation.id)
        } catch(error: OutboundSmsBlockedException) {
            SendResult.BlockedBySecurity(error.message ?: "MMS cannot use the secure SMS session")
        } catch(error: SecurityException) {
            SendResult.BlockedBySecurity(error.message ?: "MMS send was blocked")
        } catch(error: Throwable) {
            SendResult.Failed(error.message ?: "Unable to send MMS")
        }
    }

    override suspend fun sendNotificationReply(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
    ): SendResult {
        val recipients = GroupMessagePolicy.recipients(address)
        val groupReply = GroupMessagePolicy.notificationReplyTransport(address) ==
            ReplyTransport.GROUP_MMS
        val result = if(groupReply) {
            sendMms(recipients, threadId, subscriptionId, text)
        } else {
            sendText(recipients.single(), threadId, subscriptionId, text)
        }
        if(result is SendResult.Sent) {
            withContext(Dispatchers.IO) {
                appContext.getDatabase().conversationsDao()
                    ?.getConversation(result.localMessageId)
                    ?.let { conversation ->
                        appContext.sendNotificationBroadcast(
                            conversation = conversation,
                            self = true,
                            type = if(groupReply) NotificationTxType.MMS else NotificationTxType.TEXT,
                        )
                    }
            }
        }
        return result
    }

    override suspend fun requestOrRepairSecureSession(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        forceRenewal: Boolean,
    ): SecureSessionActionResult = withContext(Dispatchers.IO) {
        try {
            val normalizedAddress = appContext.makeE16PhoneNumber(address)
            val status = SecureSessionStatusResolver.resolve(appContext, normalizedAddress)
            val mode = currentSecureMode(normalizedAddress)
            val payload = if(forceRenewal || status == SecureSessionStatus.SECURE_BROKEN) {
                EncryptionController.renewSession(appContext, normalizedAddress)
            } else {
                EncryptionController.sendRequest(appContext, normalizedAddress, mode)
            }
            val queued = appContext.sendSms(
                text = "",
                address = normalizedAddress,
                threadId = threadId,
                subscriptionId = subscriptionId,
                data = payload,
                bundle = Bundle(),
            )
            if(queued == null) {
                SecureSessionActionResult.Failed("Secure request was not queued")
            } else {
                SecureSessionActionResult.RequestSent
            }
        } catch(error: Throwable) {
            SecureSessionActionResult.Failed(error.message ?: "Unable to request a secure key")
        }
    }

    override suspend fun securityFingerprint(address: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                IdentityKeyManager.safetyNumber(
                    appContext,
                    appContext.makeE16PhoneNumber(address),
                )
            }.getOrNull()
        }

    override suspend fun localSecurityQrPayload(): String? = withContext(Dispatchers.IO) {
        runCatching {
            IdentityKeyManager.qrPayload(IdentityKeyManager.localPublicKey(appContext))
        }.getOrNull()
    }

    override suspend fun verifyContactIdentity(
        address: String,
        qrPayload: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val scannedKey = IdentityKeyManager.parseQrPayload(qrPayload) ?: return@withContext false
        runCatching {
            IdentityKeyManager.verifyContact(
                appContext,
                appContext.makeE16PhoneNumber(address),
                scannedKey,
            )
        }.getOrDefault(false)
    }

    override suspend fun acceptChangedIdentityAndRepair(
        address: String,
        threadId: Int,
        subscriptionId: Long,
    ): SecureSessionActionResult = withContext(Dispatchers.IO) {
        val normalizedAddress = appContext.makeE16PhoneNumber(address)
        try {
            if(!IdentityKeyManager.acceptChangedIdentity(appContext, normalizedAddress)) {
                return@withContext SecureSessionActionResult.Failed(
                    "No changed identity is waiting for acceptance"
                )
            }
            appContext.removeEncryptionRatchetStates(normalizedAddress)
            appContext.removeEncryptionModeStates(normalizedAddress)
            requestOrRepairSecureSession(
                address = normalizedAddress,
                threadId = threadId,
                subscriptionId = subscriptionId,
                forceRenewal = true,
            )
        } catch(error: Throwable) {
            SecureSessionActionResult.Failed(error.message ?: "Unable to accept the new key")
        }
    }

    private suspend fun resolveSecurityState(address: String): ConversationSecurityState {
        val session = SecureSessionStatusResolver.resolve(appContext, address)
        val identity = try {
            IdentityKeyManager.getContactIdentity(appContext, address).status
        } catch (_: Throwable) {
            return ConversationSecurityState.RECOVERY_REQUIRED
        }
        return ConversationSecurityStateMapper.map(
            session = session,
            identity = identity,
            mode = currentSecureMode(address),
        )
    }

    private suspend fun currentSecureMode(address: String): EncryptionController.SecureRequestMode {
        return try {
            appContext.getEncryptionModeStatesSync(address)
                ?.let(SavedEncryptedModes::deserialize)
                ?.mode
                ?: EncryptionController.SecureRequestMode.REQUEST_NONE
        } catch (_: Throwable) {
            EncryptionController.SecureRequestMode.REQUEST_BROKEN
        }
    }

    private fun contactName(address: String): String {
        val addresses = splitStoredAddresses(address)
        if(addresses.size > 1) return addresses.joinToString(", ") { contactName(it) }
        val singleAddress = addresses.firstOrNull() ?: address
        return contactNames[singleAddress] ?: run {
        val value = if(hasContactsPermission()) {
            runCatching { appContext.retrieveContactName(singleAddress) }.getOrNull()
        } else null
        (value ?: singleAddress).also { contactNames[singleAddress] = it }
        }
    }

    private fun contactPhoto(address: String): String? {
        val addresses = splitStoredAddresses(address)
        if(addresses.size != 1) return null
        val singleAddress = addresses.single()
        contactPhotos[singleAddress]?.let { return it.ifEmpty { null } }
        val value = if(hasContactsPermission()) {
            runCatching { appContext.retrieveContactPhoto(singleAddress) }.getOrNull()
        } else null
        contactPhotos[singleAddress] = value.orEmpty()
        return value
    }

    private fun resolveThreadParticipants(
        threadId: Int,
        fallbackAddress: String,
    ): List<String> = resolveThreadParticipants(threadId, splitStoredAddresses(fallbackAddress))

    private fun resolveThreadParticipants(
        threadId: Int,
        fallbackAddresses: Collection<String>,
    ): List<String> {
        val dao = requireNotNull(appContext.getDatabase().conversationsDao())
        val storedParticipants = dao.getThreadParticipantAddresses(threadId)
        val providerParticipants = if(
            ThreadParticipantCachePolicy.shouldQueryProvider(storedParticipants)
        ) {
            runCatching { appContext.getThreadParticipantAddresses(threadId) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val participants = ThreadParticipantCachePolicy.resolve(
            cached = storedParticipants,
            provider = providerParticipants,
            fallback = fallbackAddresses,
        ).map(::normalizeParticipantAddress)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        if(participants.isNotEmpty() && participants != storedParticipants) {
            dao.replaceThreadParticipants(threadId, participants)
            val resolvedAddress = participants.joinToString(GROUP_ADDRESS_SEPARATOR)
            val owningThreadId = appContext.getDatabase().threadsDao()
                ?.get(resolvedAddress)
                ?.threadId
            ThreadParticipantCachePolicy.tryUpdateAddress(
                threadId = threadId,
                owningThreadId = owningThreadId,
            ) {
                dao.updateThreadAddress(threadId, resolvedAddress)
            }
        }
        return participants
    }

    private fun normalizeParticipantAddress(address: String): String =
        runCatching { appContext.makeE16PhoneNumber(address.trim()) }
            .getOrDefault(address.trim())

    private fun splitStoredAddresses(address: String): List<String> = address
        .let(GroupMessagePolicy::recipients)

    private fun normalizeStoredAddress(address: String): String = splitStoredAddresses(address)
        .map(appContext::makeE16PhoneNumber)
        .distinct()
        .joinToString(GROUP_ADDRESS_SEPARATOR)

    private fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        android.Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasRemoteListenerPermissions(): Boolean = listOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
    ).all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun looksLikeAddress(value: String): Boolean =
        value.count(Char::isDigit) >= 3 && value.all { character ->
            character.isDigit() || character in "+-() "
        }

    override fun activeSimSubscriptions(): List<SimSubscription> = activeSubscriptions()

    private fun activeSubscriptions(): List<SimSubscription> {
        return runCatching {
            appContext.getSimCardInformation().orEmpty().map { info ->
                SimSubscription(
                    id = info.subscriptionId.toLong(),
                    displayName = info.displayName?.toString().orEmpty()
                        .ifBlank { "SIM ${info.simSlotIndex + 1}" },
                    slotIndex = info.simSlotIndex,
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val THREAD_PAGE_SIZE = 40
        const val MAX_RECIPIENT_RESULTS = 200
        const val SECURITY_UPDATE_SNIPPET = "Security information updated"
        const val DECRYPTION_FAILED_SNIPPET = "Encrypted message could not be read"
        const val ONBOARDING_PREFERENCES = "messages_oneui_onboarding"
        const val CONTACT_PROMPT_COMPLETED = "contact_prompt_completed"
        const val GROUP_ADDRESS_SEPARATOR = ","
    }
}

/** Paging transformations are collected by Compose, so blocking Room/contact work must hop to IO. */
internal object MessageStorageDispatcher {
    suspend fun <T> read(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
