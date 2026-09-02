package com.afkanerd.smswithoutborders_libsmsmms.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Threads
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.ThreadSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadsDao {

    @Query("SELECT * FROM Threads WHERE isArchive = 0 AND address IS NOT NULL ORDER BY isPinned DESC, date DESC, threadId DESC")
    fun getThreads0(): PagingSource<Int, Threads>

    @Query("SELECT * FROM Threads WHERE isArchive = 0 AND address IS NOT NULL AND isPinned = 1 ORDER BY date DESC, threadId DESC")
    fun getPinnedOnly(): PagingSource<Int, Threads>

    fun getThreads(): PagingSource<Int, Threads>{
        return this.getThreads0();
    }

    @Query(
        "SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, " +
            "t.isArchive, t.isBlocked, " +
            "(SELECT COUNT(*) FROM Conversations unread " +
            " WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId) " +
            " AND unread.read = 0) AS unreadCount, " +
            "latest.sms_data AS smsData, " +
            "latest.secure_transport_text AS secureTransportText, " +
            "(SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp " +
            " WHERE tp.threadId = t.threadId) AS participantAddresses " +
            "FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId " +
            "WHERE t.isArchive = 0 AND t.address IS NOT NULL " +
            "ORDER BY t.isPinned DESC, t.date DESC, t.threadId DESC"
    )
    fun getThreadSummaries(): PagingSource<Int, ThreadSummary>

    /**
     * Summary projection used by the Compose inbox folders. Folder values mirror
     * ConversationFolder ordinals without coupling this storage module to app UI types:
     * 0 inbox, 1 archived, 2 drafts, 3 muted, 4 blocked.
     */
    @Query(
        "SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, " +
            "t.isArchive, t.isBlocked, " +
            "(SELECT COUNT(*) FROM Conversations unread " +
            " WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId) " +
            " AND unread.read = 0) AS unreadCount, " +
            "latest.sms_data AS smsData, " +
            "latest.secure_transport_text AS secureTransportText, " +
            "(SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp " +
            " WHERE tp.threadId = t.threadId) AS participantAddresses " +
            "FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId " +
            "WHERE t.address IS NOT NULL AND ((:folder = 0 AND t.isArchive = 0) " +
            "OR (:folder = 1 AND t.isArchive = 1) " +
            "OR (:folder = 2 AND t.type = 3) " +
            "OR (:folder = 3 AND t.isMute = 1) " +
            "OR (:folder = 4 AND t.isBlocked = 1)) " +
            "ORDER BY CASE WHEN :folder = 0 THEN t.isPinned ELSE 0 END DESC, " +
            "t.date DESC, t.threadId DESC"
    )
    fun getThreadSummaries(folder: Int): PagingSource<Int, ThreadSummary>

    @Query(
        "SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, " +
            "t.isArchive, t.isBlocked, " +
            "(SELECT COUNT(*) FROM Conversations unread " +
            " WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId) " +
            " AND unread.read = 0) AS unreadCount, " +
            "latest.sms_data AS smsData, " +
            "latest.secure_transport_text AS secureTransportText, " +
            "(SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp " +
            " WHERE tp.threadId = t.threadId) AS participantAddresses " +
            "FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId " +
            "WHERE t.threadId IN (:threadIds) AND t.address IS NOT NULL " +
            "AND ((:folder = 0 AND t.isArchive = 0) " +
            "OR (:folder = 1 AND t.isArchive = 1) " +
            "OR (:folder = 2 AND t.type = 3) " +
            "OR (:folder = 3 AND t.isMute = 1) " +
            "OR (:folder = 4 AND t.isBlocked = 1)) " +
            "ORDER BY CASE WHEN :folder = 0 THEN t.isPinned ELSE 0 END DESC, " +
            "t.date DESC, t.threadId DESC"
    )
    fun getThreadSummaries(
        folder: Int,
        threadIds: List<Int>,
    ): PagingSource<Int, ThreadSummary>

    @Query(
        "SELECT COUNT(*) FROM Conversations c INNER JOIN Threads t " +
            "ON (t.threadId = c.thread_id OR t.threadId = c.mms_thread_id) " +
            "WHERE c.read = 0 AND t.isArchive = 0 AND t.address IS NOT NULL"
    )
    fun unreadMessageCount(): Flow<Int>

    @Query("SELECT * FROM Threads WHERE isArchive = 1 ORDER BY date DESC, threadId DESC")
    fun getArchived(): PagingSource<Int, Threads>

    @Query("SELECT * FROM Threads WHERE type = :type ORDER BY date DESC, threadId DESC")
    fun getType(type: Int): PagingSource<Int, Threads>

    @Query("SELECT * FROM Threads WHERE isMute = 1 ORDER BY date DESC, threadId DESC")
    fun getIsMute(): PagingSource<Int, Threads>

    @Query("SELECT * FROM Threads WHERE isBlocked = 1 ORDER BY date DESC, threadId DESC")
    fun getIsBlocked(): PagingSource<Int, Threads>

    @Query("SELECT * FROM Threads WHERE address = :address ORDER BY date DESC")
    fun get(address: String): Threads?

    @Query("SELECT * FROM Threads WHERE threadId = :threadId")
    fun get(threadId: Int): Threads?

    @Query("SELECT * FROM Threads WHERE address IS NOT NULL")
    fun getAllSnapshot(): List<Threads>

    @Query("UPDATE Threads SET isMute = :isMute WHERE threadId = :threadId")
    fun setMute(isMute: Boolean, threadId: Int)

    @Query("UPDATE Threads SET isBlocked = :isBlocked WHERE address IN (:addresses)")
    fun setIsBlocked(isBlocked: Boolean, addresses: List<String>)

    @Delete
    fun deleteThreads(threads: List<Threads>)

    @Update
    fun update(threads: List<Threads>): Int

    @Query("UPDATE Threads SET unread = 0")
    fun markAllThreadsAsRead(): Int

    @Query("UPDATE Conversations SET read = 1")
    fun markAllConversationRowsAsRead(): Int

    @Query("UPDATE Threads SET unread = 0 WHERE threadId IN (:threadIds)")
    fun markThreadsAsRead(threadIds: List<Int>): Int

    @Query(
        "UPDATE Conversations SET read = 1 WHERE " +
            "thread_id IN (:threadIds) OR mms_thread_id IN (:threadIds)"
    )
    fun markConversationRowsAsRead(threadIds: List<Int>): Int

    @Transaction
    fun markAsRead(threadIds: List<Int>): Int {
        if(threadIds.isEmpty()) return 0
        return markThreadsAsRead(threadIds) + markConversationRowsAsRead(threadIds)
    }

    @Transaction
    fun markAllAsRead(): Int =
        markAllThreadsAsRead() + markAllConversationRowsAsRead()

    @Query("DELETE FROM Conversations WHERE thread_id IN (:threads)")
    fun deleteConversations(threads: List<Int>)

    @Query("DELETE FROM Threads")
    fun deleteAll()

    @Transaction
    fun delete(threads: List<Threads>) {
        deleteThreads(threads)
        val threadIds = threads.map { it.threadId }
        deleteConversations(threadIds)
    }

    @Query("SELECT " +
            "tc.threadId, " +
            "tc.isArchive, " +
            "tc.address, " +
            "tc.conversationId, " +
            "c.date AS date, " +
            "tc.isMute, " +
            "tc.type, " +
            "tc.unread, " +
            "tc.isMms, " +
            "tc.isBlocked, " +
            "tc.isPinned," +
            "SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, " +
            "c.body AS snippet " +
            "FROM Threads tc LEFT JOIN Conversations c " +
            "ON c.thread_id = tc.threadId " +
            "WHERE tc.isArchive = 0 AND " +
            "(c.type IS NOT 3 AND c.body like '%' || :query || '%') " +
            "GROUP BY thread_id ORDER BY date DESC")
    fun search(query: String): PagingSource<Int, Threads>


    @Query("SELECT " +
            "tc.threadId, " +
            "tc.isArchive, " +
            "tc.address, " +
            "tc.conversationId, " +
            "c.date AS date, " +
            "tc.isMute, " +
            "tc.type, " +
            "tc.unread, " +
            "tc.isMms, " +
            "tc.isBlocked, " +
            "tc.isPinned," +
            "SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, " +
            "c.body AS snippet " +
            "FROM Threads tc LEFT JOIN Conversations c " +
            "ON c.thread_id = tc.threadId " +
            "WHERE c.thread_id = :threadId AND tc.isArchive = 0 AND " +
            "(c.type IS NOT 3 AND c.body like '%' || :query || '%') " +
            "GROUP BY thread_id ORDER BY date DESC")
    fun search(query: String, threadId: Int): PagingSource<Int, Threads>

    @Query("SELECT " +
            "tc.threadId, " +
            "tc.isArchive, " +
            "tc.address, " +
            "tc.conversationId, " +
            "c.date AS date, " +
            "tc.isMute, " +
            "tc.type, " +
            "tc.unread, " +
            "tc.isMms, " +
            "tc.isBlocked, " +
            "tc.isPinned," +
            "SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, " +
            "c.body AS snippet " +
            "FROM Threads tc LEFT JOIN Conversations c " +
            "ON c.thread_id = tc.threadId " +
            "WHERE tc.address = :address AND tc.isArchive = 0 AND " +
            "(c.type IS NOT 3 AND c.body like '%' || :query || '%') " +
            "GROUP BY thread_id ORDER BY date DESC")
    fun searchByAddress(query: String, address: String): PagingSource<Int, Threads>

    @Query("SELECT COUNT(*) FROM Conversations WHERE thread_id = :threadId AND read = 0")
    fun getUnreadCount(threadId: Int): Flow<Int>

}
