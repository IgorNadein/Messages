package com.afkanerd.smswithoutborders_libsmsmms.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Threads
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.ThreadParticipant

internal fun Threads.withLatestMessage(
    sms: SmsMmsNatives.Sms,
    keepArchived: Boolean,
    isMms: Boolean,
    conversationId: Long,
    participantAddress: String = requireNotNull(sms.address),
): Threads = copy(
    snippet = sms.body.orEmpty(),
    date = sms.date,
    unread = sms.read == 0,
    address = participantAddress,
    type = sms.type,
    conversationId = conversationId,
    isArchive = isArchive && keepArchived,
    isMms = isMms,
    // isMute, isBlocked and isPinned intentionally survive message/draft updates.
)

@Dao
interface ConversationsDao {

    @Query("SELECT address FROM ThreadParticipants WHERE threadId = :threadId ORDER BY address")
    fun getThreadParticipantAddresses(threadId: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertThreadParticipants(participants: List<ThreadParticipant>)

    @Query("DELETE FROM ThreadParticipants WHERE threadId = :threadId")
    fun deleteThreadParticipants(threadId: Int)

    @Query("UPDATE Threads SET address = :address WHERE threadId = :threadId")
    fun updateThreadAddress(threadId: Int, address: String)

    @Transaction
    fun replaceThreadParticipants(threadId: Int, addresses: Collection<String>) {
        val normalized = addresses.map(String::trim).filter(String::isNotEmpty).distinct()
        if(normalized.isEmpty()) return
        deleteThreadParticipants(threadId)
        insertThreadParticipants(normalized.map { ThreadParticipant(threadId, it) })
    }

    @Query("SELECT * FROM Conversations WHERE Conversations.id = :id")
    fun getConversation(id: Long): Conversations?

    @Query(
        "SELECT * FROM Conversations WHERE type = 1 AND " +
            "(body LIKE 'Ayg%' OR body LIKE 'AwAC%') ORDER BY date ASC"
    )
    fun getPotentialUndecryptedSecureMessages(): List<Conversations>

    @Query(
        "SELECT * FROM Conversations WHERE type = 1 AND " +
            "secure_transport_text IS NOT NULL AND body = :failureText"
    )
    fun getStoredSecureDecryptionFailures(failureText: String): List<Conversations>

    @Update
    fun updateConversation(conversations: Conversations)

    @Update
    fun updateThread(thread: Threads)

    fun insertUpdateThread(
        sms: SmsMmsNatives.Sms,
        keepArchived: Boolean,
        isMms: Boolean,
        mms: SmsMmsNatives.Mms?,
        conversationId: Long,
        participantAddresses: Collection<String> = emptyList(),
    ) {
        val threadId = mms?.thread_id ?: sms.thread_id
        val thread = getThread(threadId)
        val count = unreadCount(threadId)
        val participantAddress = participantAddresses
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(",")
            .ifBlank { requireNotNull(sms.address) }

        if(thread == null) {
            insertThread(
                Threads(
                    threadId = threadId,
                    snippet = sms.body ?: "",
                    date = sms.date,
                    unread = count > 0,
                    address = participantAddress,
                    isMute = false,
                    type = sms.type,
                    conversationId = conversationId,
                    isArchive = false,
                    isMms = isMms,
                )
            )
        } else {
            updateThread(
                thread.withLatestMessage(
                    sms,
                    keepArchived,
                    isMms,
                    conversationId,
                    participantAddress,
                )
            )
        }
        if(participantAddresses.isNotEmpty()) {
            replaceThreadParticipants(threadId, participantAddresses)
        }
    }

    @Transaction
    fun update(conversation: Conversations) {
        updateConversation(conversation)
        conversation.sms?.let {
            insertUpdateThread(
                it,
                true,
                conversation.mms != null || !conversation.mms_content_uri.isNullOrEmpty(),
                conversation.mms,
                conversation.id,
                conversation.participantAddresses,
            )
        }
    }

    @Update
    fun update(conversations: MutableList<Conversations>): Int

    @Query("SELECT * FROM Conversations WHERE thread_id = :threadId OR " +
            "Conversations.mms_thread_id = :threadId ORDER BY date DESC")
    fun getConversations(threadId: Int): PagingSource<Int, Conversations>

    @Query("SELECT * FROM Conversations WHERE thread_id IN (:threadIds) OR " +
            "Conversations.mms_thread_id IN (:threadIds) ORDER BY date DESC")
    fun getConversations(threadIds: List<Int>): PagingSource<Int, Conversations>

    @Query("SELECT COUNT('_id') FROM Conversations WHERE " +
            "(thread_id = :threadId OR Conversations.mms_thread_id = :threadId) AND read = 0")
    fun unreadCount(threadId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConversation(conversation: Conversations): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertConversations(conversation: List<Conversations>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertThread(thread: Threads)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertThreads(thread: List<Threads>)

    @Query("SELECT * FROM Threads WHERE threadId = :threadId")
    fun getThread(threadId: Int): Threads?

    @Query("SELECT * FROM Threads WHERE address IN (:addresses) ORDER BY date DESC")
    fun getThreadsForAddresses(addresses: List<String>): List<Threads>

    @Transaction
    fun insert(conversation: Conversations, removeArchive: Boolean = false): Long {
        val id = insertConversation(conversation)
        conversation.sms?.let {
            insertUpdateThread(
                it,
                removeArchive,
            conversation.mms != null || !conversation.mms_content_uri.isNullOrEmpty(),
                conversation.mms,
                id,
                conversation.participantAddresses,
            )
        }
        return id
    }

    @Query("DELETE FROM Conversations WHERE address like :address || '%'")
    fun deleteAllAddressConversations(address: String)

    @Query("DELETE FROM Conversations WHERE address like :address || '%'")
    fun deleteAllThreads(address: String)

    @Transaction
    fun insertAllThreads(conversationsList: List<Conversations>, isMms: Boolean) {
        val conversation = conversationsList.firstOrNull() ?: return

//        deleteAllAddressConversations(conversation.sms?.address!!)
//        deleteAllThreads(conversation.sms?.address!!)
        insertConversations(conversationsList)
        conversation.sms?.let {
            insertUpdateThread(
                it,
                false,
                isMms,
                conversation.mms,
                conversation.id,
                conversation.participantAddresses,
            )
        }
    }

    @Transaction
    fun insertAll(conversationsList: List<Conversations>, deleteDb: Boolean = false) {
        if(deleteDb) {
            deleteAllConversations()
            deleteAllThreads()
        }

//        insertConversations(conversationsList)
        conversationsList.forEach {
            insert(it)
        }
    }

    @Query("DELETE FROM Conversations")
    fun deleteAllConversations()

    @Query("DELETE FROM Threads")
    fun deleteAllThreads()

    @Delete
    fun deleteConversation(conversations: Conversations)

    @Delete
    fun deleteConversations(conversations: List<Conversations>)

    @Query("DELETE FROM Threads WHERE conversationId = :conversationId")
    fun deleteThreadConversation(conversationId: Long)

    @Query("DELETE FROM Threads WHERE conversationId IN (:ids)")
    fun deleteThreadConversations(ids: List<Long>)

    @Transaction
    fun delete(conversation: Conversations, removeArchive: Boolean) {
        deleteConversation(conversation)
        deleteThreadConversation(conversation.id )
        getLatestConversation(conversation.sms!!.thread_id)?.let { latest ->
            insertUpdateThread(
                latest.sms!!,
                removeArchive,
                !latest.mms_content_uri.isNullOrEmpty(),
                latest.mms,
                latest.id
            )
        }
    }

    @Transaction
    fun delete(conversations: List<Conversations>, removeArchive: Boolean) {
        deleteConversations(conversations)
        deleteThreadConversations(conversations.map { it.id })
        conversations.forEach {
            getLatestConversation(it.sms!!.thread_id)?.let { latest ->
                insertUpdateThread(
                    latest.sms!!,
                    removeArchive,
                    !latest.mms_content_uri.isNullOrEmpty(),
                    latest.mms,
                    latest.id
                )
            }
        }
    }

    @Query("SELECT * FROM Conversations WHERE (thread_id = :threadId OR " +
            "Conversations.mms_thread_id = :threadId) AND " +
            "type = :type ORDER BY  date DESC LIMIT 1")
    fun fetchConversationsForType(threadId: Int, type: Int): Conversations?

    @Query("SELECT * FROM Conversations WHERE (thread_id = :threadId OR " +
            "Conversations.mms_thread_id = :threadId) ORDER BY date DESC")
    fun getConversationsList(threadId: Int): List<Conversations>

    @Query("SELECT * FROM Conversations WHERE (thread_id = :threadId OR " +
            "Conversations.mms_thread_id = :threadId) ORDER BY date DESC LIMIT 1")
    fun getLatestConversation(threadId: Int): Conversations?
}
