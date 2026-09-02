package com.afkanerd.deku.messages.service

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
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
import com.afkanerd.deku.attachments.transport.MediaTransportPreference
import com.afkanerd.deku.attachments.transport.MediaTransportRouter
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
import com.afkanerd.deku.messages.domain.SecureChannel
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
import com.afkanerd.deku.security.SecureChannelId
import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.security.SecureSessionStatusResolver
import com.afkanerd.deku.security.SecureSendPreference
import com.afkanerd.deku.security.SecureMessageTransportPreference
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
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendSms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendMms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.cancelNotification
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.setNativesLoaded
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetConversationsSubscriptionId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetConversationsSubscriptionId
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.ThreadSummary
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsBlockedException
import com.afkanerd.smswithoutborders_libsmsmms.security.FORCE_PLAIN_TEXT_EXTRA
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
    private val contactPresentations = ConcurrentHashMap<String, ContactPresentation>()

    override fun isDefaultSmsApp(): Boolean = appContext.isDefault()

    override fun hasContactAccess(): Boolean = hasContactsPermission()

    override fun isContactPromptCompleted(): Boolean = appContext
        .getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(CONTACT_PROMPT_COMPLETED, false)

    override fun completeContactPrompt() {
        appContext.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
            .edit { putBoolean(CONTACT_PROMPT_COMPLETED, true) }
        contactPresentations.clear()
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
        return Pager<Int, ThreadSummary>(
            ConversationPagingPolicy.config()
        ) {
            includedThreadIds?.let { ids ->
                threadsDao.getThreadSummaries(folder.ordinal, ids)
            } ?: threadsDao.getThreadSummaries(folder.ordinal)
        }.flow.map { page ->
            val visibleIdentities = ConcurrentHashMap.newKeySet<String>()
            page.map { thread ->
                MessageStorageDispatcher.read {
                    val decryptionFailureText = appContext.getString(
                        R.string.security_decryption_failed
                    )
                    val snippet = ThreadSummarySnippetPolicy.display(
                        snippet = thread.snippet,
                        smsData = thread.smsData,
                        secureTransportText = thread.secureTransportText,
                        decryptionFailureText = decryptionFailureText,
                        recoveryText = appContext.getString(R.string.oneui_secure_recovery),
                        securityUpdateText = SECURITY_UPDATE_SNIPPET,
                        decryptionFailedText = DECRYPTION_FAILED_SNIPPET,
                    )
                    val participants = summaryParticipants(
                        storedParticipants = thread.participantAddresses,
                        fallbackAddress = thread.address,
                    )
                    val threadAddress = participants.joinToString(GROUP_ADDRESS_SEPARATOR)
                    val contact = participants.singleOrNull()?.let(::contactPresentation)
                    InboxThreadProjection(
                        identity = ConversationThreadIdentityPolicy.key(
                            normalizedParticipants = participants,
                            contactId = contact?.contactId,
                        ),
                        thread = ConversationThread(
                            id = thread.threadId,
                            address = threadAddress,
                            displayName = contact?.name ?: contactName(threadAddress),
                            avatarUri = contact?.photoUri,
                            snippet = snippet,
                            timestampMillis = thread.date,
                            unreadCount = thread.unreadCount,
                            isPinned = thread.isPinned,
                            isMuted = thread.isMute,
                            isArchived = thread.isArchive,
                            isBlocked = thread.isBlocked,
                        ),
                    )
                }
            }.filter { visibleIdentities.add(it.identity) }
                .map { projection -> projection.thread }
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
        val relatedThreads = relatedOneToOneThreads(current.threadId, current.address)
        if(action == ConversationThreadAction.DELETE) {
            return@withContext relatedThreads.all { related ->
                ConversationThreadDeletionCoordinator.delete(
                    threadId = related.threadId,
                    deleteFromSystemDatabase = appContext.settingsGetDeleteSystem,
                    deleteLocal = { dao.delete(listOf(related)) },
                    deleteSystem = { systemThreadId ->
                        appContext.deleteSmsThreads(arrayOf(systemThreadId))
                    },
                )
            }
        }
        val updated = relatedThreads.map { related ->
            when(action) {
                ConversationThreadAction.PIN -> related.copy(isPinned = true)
                ConversationThreadAction.UNPIN -> related.copy(isPinned = false)
                ConversationThreadAction.MUTE -> related.copy(isMute = true)
                ConversationThreadAction.UNMUTE -> related.copy(isMute = false)
                ConversationThreadAction.ARCHIVE -> related.copy(isArchive = true)
                ConversationThreadAction.UNARCHIVE -> related.copy(isArchive = false)
                ConversationThreadAction.DELETE -> error("Handled before thread update")
            }
        }
        dao.update(updated) == updated.size
    }

    override suspend fun markAllConversationsRead(): Boolean = withContext(Dispatchers.IO) {
        val dao = requireNotNull(appContext.getDatabase().threadsDao())
        dao.markAllAsRead()
        val readValues = ContentValues().apply { put(Telephony.TextBasedSmsColumns.READ, 1) }
        runCatching {
            appContext.contentResolver.update(Telephony.Sms.CONTENT_URI, readValues, null, null)
        }
        runCatching {
            appContext.contentResolver.update(Telephony.Mms.CONTENT_URI, readValues, null, null)
        }
        true
    }

    override suspend fun markConversationRead(threadIds: List<Int>): Boolean =
        withContext(Dispatchers.IO) {
            val ids = threadIds.filter { it >= 0 }.distinct()
            if(ids.isEmpty()) return@withContext false
            val dao = requireNotNull(appContext.getDatabase().threadsDao())
            MessageStorageDispatcher.write { dao.markAsRead(ids) }

            val placeholders = ids.joinToString(",") { "?" }
            val selection = "${Telephony.TextBasedSmsColumns.THREAD_ID} IN ($placeholders)"
            val selectionArgs = ids.map(Int::toString).toTypedArray()
            val readValues = ContentValues().apply {
                put(Telephony.TextBasedSmsColumns.READ, 1)
            }
            runCatching {
                appContext.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    readValues,
                    selection,
                    selectionArgs,
                )
            }
            runCatching {
                appContext.contentResolver.update(
                    Telephony.Mms.CONTENT_URI,
                    readValues,
                    selection,
                    selectionArgs,
                )
            }
            ids.forEach(appContext::cancelNotification)
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

    override fun timeline(threadId: Int): Flow<PagingData<TimelineItem>> =
        timeline(listOf(threadId))

    override fun timeline(threadIds: List<Int>): Flow<PagingData<TimelineItem>> {
        val resolvedThreadIds = threadIds.distinct()
        if(resolvedThreadIds.isEmpty()) return flowOf(PagingData.empty())
        val dao = requireNotNull(appContext.getDatabase().conversationsDao())
        return flow {
            emitAll(Pager(
                MessagePagingPolicy.config()
            ) { dao.getConversations(resolvedThreadIds) }.flow.map { page ->
                page
                    .filter(ConversationEntityMapper::shouldExpose)
                    .map { entity ->
                        val entityAddress = entity.sms?.address
                            ?.let(::normalizeParticipantAddress)
                        val secureConversation = entityAddress?.let { address ->
                            SecureSessionStatusResolver.resolve(
                                appContext,
                                address,
                                entity.sms?.sub_id ?: -1,
                            ) != SecureSessionStatus.PLAIN
                        } == true
                        val author = entity.sender_address?.takeIf(String::isNotBlank)?.let {
                            MessageAuthor(
                                address = it,
                                displayName = contactName(it),
                                avatarUri = contactPhoto(it),
                            )
                        }
                        ConversationEntityMapper.map(
                            entity,
                            secureConversation,
                            author,
                            appContext.getString(R.string.security_decryption_failed),
                        ).withFavorite(messageFavorite("message-${entity.id}"))
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

    override fun attachmentTransfers(addresses: List<String>): Flow<List<AttachmentTransfer>> {
        val normalizedAddresses = addresses
            .map(appContext::makeE16PhoneNumber)
            .filter(String::isNotBlank)
            .distinct()
        if(normalizedAddresses.isEmpty()) return flowOf(emptyList())
        return Datastore.getDatastore(appContext)
            .attachmentTransferDao()
            .observeForAddresses(normalizedAddresses)
            .map { transfers -> transfers.map(AttachmentTransferMapper::map) }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun deleteMessage(stableId: String): Boolean = withContext(Dispatchers.IO) {
        val localId = stableId.removePrefix(MESSAGE_STABLE_ID_PREFIX).toLongOrNull()
            ?: return@withContext false
        val dao = appContext.getDatabase().conversationsDao()
            ?: return@withContext false
        val conversation = MessageStorageDispatcher.read {
            dao.getConversation(localId)
        } ?: return@withContext false
        val providerId = conversation.sms?._id ?: return@withContext false
        val providerCollection = if(
            conversation.mms != null || !conversation.mms_content_uri.isNullOrBlank()
        ) {
            Telephony.Mms.CONTENT_URI
        } else {
            Telephony.Sms.CONTENT_URI
        }
        val providerDeleteSucceeded = runCatching {
            appContext.contentResolver.delete(
                ContentUris.withAppendedId(providerCollection, providerId),
                null,
                null,
            )
        }.isSuccess
        if(!providerDeleteSucceeded) return@withContext false
        MessageStorageDispatcher.write {
            dao.delete(conversation, true)
        }
        appContext.getSharedPreferences(MESSAGE_ACTION_PREFERENCES, Context.MODE_PRIVATE)
            .edit { remove(stableId) }
        true
    }

    override suspend fun setMessageFavorite(
        stableId: String,
        favorite: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if(!stableId.startsWith(MESSAGE_STABLE_ID_PREFIX)) return@withContext false
        appContext.getSharedPreferences(MESSAGE_ACTION_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                if(favorite) putBoolean(stableId, true) else remove(stableId)
            }
        true
    }

    private fun messageFavorite(stableId: String): Boolean = appContext
        .getSharedPreferences(MESSAGE_ACTION_PREFERENCES, Context.MODE_PRIVATE)
        .getBoolean(stableId, false)

    private fun TimelineItem.withFavorite(favorite: Boolean): TimelineItem = when(this) {
        is TimelineItem.Text -> copy(isFavorite = favorite)
        is TimelineItem.Media -> copy(isFavorite = favorite)
        is TimelineItem.SecurityEvent -> this
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
                SecureSessionStatusResolver.resolve(
                    appContext,
                    recipient,
                    subscriptionId,
                ) ==
                    SecureSessionStatus.SECURE_ESTABLISHED
            } == true
            val route = MediaTransportRouter.resolve(
                selected = MediaTransportPreference.selected(appContext),
                recipientCount = recipients.size,
                secureOneToOne = secureOneToOne,
            )
            when(route) {
                MediaTransportRouter.Route.Mms -> {
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
                MediaTransportRouter.Route.CloudStorage -> return@withContext
                    AttachmentPrepareResult.Failed("Internet storage is not configured")
                MediaTransportRouter.Route.UnsupportedGroupDataSms -> return@withContext
                    AttachmentPrepareResult.Failed("SMS packet media currently supports one recipient")
                is MediaTransportRouter.Route.DataSms -> Unit
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
                protection = (route as MediaTransportRouter.Route.DataSms).protection,
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
        val availableContactNumbers = participants.singleOrNull()?.let { participantAddress ->
            contactPhoneNumbers(participantAddress).ifEmpty {
                listOf(
                    MessageRecipient(
                        id = participantAddress.hashCode().toLong(),
                        address = participantAddress,
                        displayName = contactName(participantAddress),
                        avatarUri = contactPhoto(participantAddress),
                    )
                )
            }
        }.orEmpty()
        val relatedThreadIds = relatedOneToOneThreads(resolvedThreadId, storedAddress)
            .map { it.threadId }
        val selectedAddress = participants.singleOrNull()
        val selectedSecurityState = selectedAddress?.let {
            resolveSecurityState(it, selectedSubscription)
        } ?: ConversationSecurityState.PLAIN
        val secureSendingRequested = selectedAddress?.let {
            SecureSendPreference.isEnabled(appContext, it, selectedSubscription)
        } ?: false
        val channelSubscriptions = subscriptions.ifEmpty {
            listOf(SimSubscription(selectedSubscription, "SIM", -1))
        }
        val channelNumbers = availableContactNumbers.ifEmpty {
            participants.map { participantAddress ->
                MessageRecipient(
                    id = participantAddress.hashCode().toLong(),
                    address = participantAddress,
                    displayName = contactName(participantAddress),
                    avatarUri = contactPhoto(participantAddress),
                )
            }
        }
        val securityChannels = if(participants.size == 1) {
            channelNumbers.flatMap { recipient ->
                channelSubscriptions.map { subscription ->
                    val channelState = resolveSecurityState(
                        recipient.address,
                        subscription.id,
                    )
                    SecureChannel(
                        remoteAddress = recipient.address,
                        remoteLabel = recipient.label,
                        subscriptionId = subscription.id,
                        subscriptionName = subscription.displayName,
                        state = channelState,
                        encryptFutureMessages = channelState.isEstablished() &&
                            SecureSendPreference.isEnabled(
                                appContext,
                                recipient.address,
                                subscription.id,
                            ),
                    )
                }
            }
        } else emptyList()
        ConversationHeader(
            threadId = resolvedThreadId,
            address = storedAddress,
            displayName = participants.joinToString(", ") { contactName(it) },
            avatarUri = participants.singleOrNull()?.let(::contactPhoto),
            subscriptionId = selectedSubscription,
            subscriptions = subscriptions,
            securityState = selectedSecurityState,
            secureSendingEnabled = selectedSecurityState.isEstablished() &&
                secureSendingRequested,
            secureSendingRequested = secureSendingRequested,
            isMuted = storedThread?.isMute == true,
            participants = participants.map { participantAddress ->
                MessageRecipient(
                    id = participantAddress.hashCode().toLong(),
                    address = participantAddress,
                    displayName = contactName(participantAddress),
                    avatarUri = contactPhoto(participantAddress),
                )
            },
            availableContactNumbers = availableContactNumbers,
            relatedThreadIds = relatedThreadIds,
            securityChannels = securityChannels,
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

    override suspend fun setSecureSendingEnabled(
        address: String,
        subscriptionId: Long,
        enabled: Boolean,
    ) {
        val normalizedAddress = appContext.makeE16PhoneNumber(address)
        val state = SecureSessionStatusResolver.resolve(
            appContext,
            normalizedAddress,
            subscriptionId,
        )
        if(enabled && state != SecureSessionStatus.SECURE_ESTABLISHED) return
        SecureSendPreference.setEnabled(appContext, normalizedAddress, subscriptionId, enabled)
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
    ): SendResult = sendText(address, threadId, subscriptionId, text, forcePlainText = false)

    override suspend fun sendText(
        address: String,
        threadId: Int,
        subscriptionId: Long,
        text: String,
        forcePlainText: Boolean,
    ): SendResult = withContext(Dispatchers.IO) {
        if(text.isBlank()) return@withContext SendResult.Failed("Message is empty")
        try {
            val conversation = appContext.sendSms(
                text = text,
                address = address,
                threadId = threadId,
                subscriptionId = subscriptionId,
                data = null,
                bundle = Bundle().apply {
                    putBoolean(FORCE_PLAIN_TEXT_EXTRA, forcePlainText)
                },
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

    override suspend fun resendMessage(
        addresses: List<String>,
        threadId: Int,
        subscriptionId: Long,
        item: TimelineItem,
        forcePlainText: Boolean,
    ): SendResult = withContext(Dispatchers.IO) {
        val recipients = addresses.map(appContext::makeE16PhoneNumber)
            .filter(String::isNotBlank)
            .distinct()
        if(recipients.isEmpty()) return@withContext SendResult.Failed("Recipient is empty")
        try {
            val conversation = when(item) {
                is TimelineItem.Text -> appContext.sendSms(
                    text = item.text,
                    address = recipients.first(),
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    data = null,
                    bundle = Bundle().apply {
                        putBoolean(FORCE_PLAIN_TEXT_EXTRA, forcePlainText)
                    },
                )
                is TimelineItem.Media -> appContext.sendMms(
                    text = item.caption.orEmpty(),
                    addresses = recipients,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    contentUri = item.uri?.toUri(),
                    filename = item.fileName,
                    mimeType = item.mimeType,
                )
                is TimelineItem.SecurityEvent -> null
            }
            conversation?.let { SendResult.Sent(it.id) }
                ?: SendResult.Failed("Message was not queued")
        } catch(error: OutboundSmsBlockedException) {
            SendResult.BlockedBySecurity(error.message ?: "Secure session is not ready")
        } catch(error: SecurityException) {
            SendResult.BlockedBySecurity(error.message ?: "Message send was blocked")
        } catch(error: Throwable) {
            SendResult.Failed(error.message ?: "Unable to resend message")
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
            SecureMessageTransportPreference.resetPeer(appContext, normalizedAddress)
            val channelAddress = SecureChannelId.storageAddress(
                normalizedAddress,
                subscriptionId,
            )
            val status = SecureSessionStatusResolver.resolve(
                appContext,
                normalizedAddress,
                subscriptionId,
            )
            val mode = currentSecureMode(channelAddress)
            val payload = if(forceRenewal || status == SecureSessionStatus.SECURE_BROKEN) {
                EncryptionController.renewSession(appContext, channelAddress)
            } else {
                EncryptionController.sendRequest(appContext, channelAddress, mode)
            }
            SecureSendPreference.setEnabled(
                appContext,
                normalizedAddress,
                subscriptionId,
                true,
            )
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
        securityFingerprint(address, -1)

    override suspend fun securityFingerprint(
        address: String,
        subscriptionId: Long,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                IdentityKeyManager.safetyNumber(
                    appContext,
                    SecureChannelId.storageAddress(
                        appContext.makeE16PhoneNumber(address),
                        subscriptionId,
                    ),
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
    ): Boolean = verifyContactIdentity(address, -1, qrPayload)

    override suspend fun verifyContactIdentity(
        address: String,
        subscriptionId: Long,
        qrPayload: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val scannedKey = IdentityKeyManager.parseQrPayload(qrPayload) ?: return@withContext false
        runCatching {
            IdentityKeyManager.verifyContact(
                appContext,
                SecureChannelId.storageAddress(
                    appContext.makeE16PhoneNumber(address),
                    subscriptionId,
                ),
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
        val channelAddress = SecureChannelId.storageAddress(normalizedAddress, subscriptionId)
        try {
            if(!IdentityKeyManager.acceptChangedIdentity(appContext, channelAddress)) {
                return@withContext SecureSessionActionResult.Failed(
                    "No changed identity is waiting for acceptance"
                )
            }
            appContext.removeEncryptionRatchetStates(channelAddress)
            appContext.removeEncryptionModeStates(channelAddress)
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

    private suspend fun resolveSecurityState(
        address: String,
        subscriptionId: Long,
    ): ConversationSecurityState {
        val channelAddress = SecureChannelId.storageAddress(address, subscriptionId)
        val session = SecureSessionStatusResolver.resolve(appContext, address, subscriptionId)
        val identity = try {
            IdentityKeyManager.getContactIdentity(appContext, channelAddress).status
        } catch (_: Throwable) {
            return ConversationSecurityState.RECOVERY_REQUIRED
        }
        return ConversationSecurityStateMapper.map(
            session = session,
            identity = identity,
            mode = currentSecureMode(channelAddress),
        )
    }

    private fun ConversationSecurityState.isEstablished(): Boolean =
        this == ConversationSecurityState.SECURE_UNVERIFIED ||
            this == ConversationSecurityState.SECURE_VERIFIED

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
        return contactPresentation(singleAddress).name
    }

    private fun contactPhoto(address: String): String? {
        val addresses = splitStoredAddresses(address)
        if(addresses.size != 1) return null
        return contactPresentation(addresses.single()).photoUri
    }

    private fun contactPresentation(address: String): ContactPresentation =
        contactPresentations[address] ?: run {
            val resolved = if(!hasContactsPermission()) null else runCatching {
                val lookup = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(address),
                )
                appContext.contentResolver.query(
                    lookup,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                        ContactsContract.PhoneLookup.CONTACT_ID,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if(!cursor.moveToFirst()) null else ContactPresentation(
                        name = cursor.getString(0)?.takeIf(String::isNotBlank) ?: address,
                        photoUri = cursor.getString(1),
                        contactId = cursor.getLong(2),
                    )
                }
            }.getOrNull()
            (resolved ?: ContactPresentation(address, null, null)).also {
                contactPresentations.putIfAbsent(address, it)
            }
        }

    private fun contactPhoneNumbers(address: String): List<MessageRecipient> {
        if(!hasContactsPermission()) return emptyList()
        val normalizedAddress = normalizeParticipantAddress(address)
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedAddress),
        )
        val contactId = appContext.contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return emptyList()

        val projection = arrayOf(
            Phone._ID,
            Phone.NUMBER,
            Phone.DISPLAY_NAME,
            Phone.PHOTO_URI,
            Phone.TYPE,
            Phone.LABEL,
        )
        val seen = HashSet<String>()
        val numbers = ArrayList<MessageRecipient>()
        appContext.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC, ${Phone._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Phone._ID)
            val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val photoIndex = cursor.getColumnIndexOrThrow(Phone.PHOTO_URI)
            val typeIndex = cursor.getColumnIndexOrThrow(Phone.TYPE)
            val labelIndex = cursor.getColumnIndexOrThrow(Phone.LABEL)
            while(cursor.moveToNext()) {
                val number = cursor.getString(numberIndex).orEmpty()
                val normalized = normalizeParticipantAddress(number)
                if(normalized.isBlank() || !seen.add(normalized)) continue
                val customLabel = cursor.getString(labelIndex)
                val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    appContext.resources,
                    cursor.getInt(typeIndex),
                    customLabel,
                ).toString()
                numbers += MessageRecipient(
                    id = cursor.getLong(idIndex),
                    address = normalized,
                    displayName = cursor.getString(nameIndex).orEmpty().ifBlank { normalized },
                    avatarUri = cursor.getString(photoIndex),
                    label = typeLabel,
                )
            }
        }
        return numbers.sortedByDescending { it.address == normalizedAddress }
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
        return participants
    }

    /** Pure inbox projection: a Paging transformation must never mutate its source tables. */
    private fun summaryParticipants(
        storedParticipants: String?,
        fallbackAddress: String,
    ): List<String> = (storedParticipants
        ?.takeIf(String::isNotBlank)
        ?.let(::splitStoredAddresses)
        ?: splitStoredAddresses(fallbackAddress))
        .map(::normalizeParticipantAddress)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()

    private fun normalizeParticipantAddress(address: String): String =
        runCatching { appContext.makeE16PhoneNumber(address.trim()) }
            .getOrDefault(address.trim())

    private fun relatedOneToOneThreads(threadId: Int, storedAddress: String) =
        splitStoredAddresses(storedAddress).singleOrNull()?.let { participantAddress ->
            val contactAddresses = contactPhoneNumbers(participantAddress)
                .map(MessageRecipient::address)
                .plus(participantAddress)
            val candidates = appContext.getDatabase().threadsDao()
                ?.getAllSnapshot().orEmpty()
            val relatedIds = ConversationThreadIdentityPolicy.relatedThreadIds(
                participantsByThreadId = candidates.associate { candidate ->
                    candidate.threadId to splitStoredAddresses(candidate.address)
                },
                contactAddresses = contactAddresses,
                normalize = ::normalizeParticipantAddress,
            ).toSet()
            candidates.filter { it.threadId in relatedIds }
                .ifEmpty {
                    listOfNotNull(appContext.getDatabase().threadsDao()?.get(threadId))
                }
        } ?: listOfNotNull(appContext.getDatabase().threadsDao()?.get(threadId))

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
        data class ContactPresentation(
            val name: String,
            val photoUri: String?,
            val contactId: Long?,
        )

        data class InboxThreadProjection(
            val identity: String,
            val thread: ConversationThread,
        )

        const val MESSAGE_STABLE_ID_PREFIX = "message-"
        const val MESSAGE_ACTION_PREFERENCES = "messages_oneui_message_actions"
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
    suspend fun <T> write(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
