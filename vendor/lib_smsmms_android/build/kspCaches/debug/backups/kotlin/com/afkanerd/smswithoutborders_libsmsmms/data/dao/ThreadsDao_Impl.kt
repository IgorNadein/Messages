package com.afkanerd.smswithoutborders_libsmsmms.`data`.dao

import androidx.paging.PagingSource
import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.coroutines.createFlow
import androidx.room.paging.LimitOffsetPagingSource
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performBlocking
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.afkanerd.smswithoutborders_libsmsmms.`data`.`data`.models.ThreadSummary
import com.afkanerd.smswithoutborders_libsmsmms.`data`.entities.Threads
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ThreadsDao_Impl(
  __db: RoomDatabase,
) : ThreadsDao {
  private val __db: RoomDatabase

  private val __deleteAdapterOfThreads: EntityDeleteOrUpdateAdapter<Threads>

  private val __updateAdapterOfThreads: EntityDeleteOrUpdateAdapter<Threads>
  init {
    this.__db = __db
    this.__deleteAdapterOfThreads = object : EntityDeleteOrUpdateAdapter<Threads>() {
      protected override fun createQuery(): String = "DELETE FROM `Threads` WHERE `threadId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Threads) {
        statement.bindLong(1, entity.threadId.toLong())
      }
    }
    this.__updateAdapterOfThreads = object : EntityDeleteOrUpdateAdapter<Threads>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `Threads` SET `threadId` = ?,`address` = ?,`snippet` = ?,`date` = ?,`type` = ?,`conversationId` = ?,`isMms` = ?,`isMute` = ?,`isArchive` = ?,`isBlocked` = ?,`unread` = ?,`isPinned` = ? WHERE `threadId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Threads) {
        statement.bindLong(1, entity.threadId.toLong())
        statement.bindText(2, entity.address)
        statement.bindText(3, entity.snippet)
        statement.bindLong(4, entity.date)
        statement.bindLong(5, entity.type.toLong())
        statement.bindLong(6, entity.conversationId)
        val _tmp: Int = if (entity.isMms) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.isMute) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        val _tmp_2: Int = if (entity.isArchive) 1 else 0
        statement.bindLong(9, _tmp_2.toLong())
        val _tmp_3: Int = if (entity.isBlocked) 1 else 0
        statement.bindLong(10, _tmp_3.toLong())
        val _tmp_4: Int = if (entity.unread) 1 else 0
        statement.bindLong(11, _tmp_4.toLong())
        val _tmp_5: Int = if (entity.isPinned) 1 else 0
        statement.bindLong(12, _tmp_5.toLong())
        statement.bindLong(13, entity.threadId.toLong())
      }
    }
  }

  public override fun deleteThreads(threads: List<Threads>): Unit = performBlocking(__db, false, true) { _connection ->
    __deleteAdapterOfThreads.handleMultiple(_connection, threads)
  }

  public override fun update(threads: List<Threads>): Int = performBlocking(__db, false, true) { _connection ->
    var _result: Int = 0
    _result += __updateAdapterOfThreads.handleMultiple(_connection, threads)
    _result
  }

  public override fun markAsRead(threadIds: List<Int>): Int = performBlocking(__db, false, true) { _ ->
    super@ThreadsDao_Impl.markAsRead(threadIds)
  }

  public override fun markAllAsRead(): Int = performBlocking(__db, false, true) { _ ->
    super@ThreadsDao_Impl.markAllAsRead()
  }

  public override fun delete(threads: List<Threads>): Unit = performBlocking(__db, false, true) { _ ->
    super@ThreadsDao_Impl.delete(threads)
  }

  public override fun getThreads0(): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE isArchive = 0 AND address IS NOT NULL ORDER BY isPinned DESC, date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getPinnedOnly(): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE isArchive = 0 AND address IS NOT NULL AND isPinned = 1 ORDER BY date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getThreadSummaries(): PagingSource<Int, ThreadSummary> {
    val _sql: String = "SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, t.isArchive, t.isBlocked, (SELECT COUNT(*) FROM Conversations unread  WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId)  AND unread.read = 0) AS unreadCount, latest.sms_data AS smsData, latest.secure_transport_text AS secureTransportText, (SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp  WHERE tp.threadId = t.threadId) AS participantAddresses FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId WHERE t.isArchive = 0 AND t.address IS NOT NULL ORDER BY t.isPinned DESC, t.date DESC, t.threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<ThreadSummary>(_rawQuery, __db, "Conversations", "ThreadParticipants", "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<ThreadSummary> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfAddress: Int = 1
          val _columnIndexOfSnippet: Int = 2
          val _columnIndexOfDate: Int = 3
          val _columnIndexOfIsPinned: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfIsArchive: Int = 6
          val _columnIndexOfIsBlocked: Int = 7
          val _columnIndexOfUnreadCount: Int = 8
          val _columnIndexOfSmsData: Int = 9
          val _columnIndexOfSecureTransportText: Int = 10
          val _columnIndexOfParticipantAddresses: Int = 11
          val _result: MutableList<ThreadSummary> = mutableListOf()
          while (_stmt.step()) {
            val _item: ThreadSummary
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsPinned: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnreadCount: Int
            _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
            val _tmpSmsData: ByteArray?
            if (_stmt.isNull(_columnIndexOfSmsData)) {
              _tmpSmsData = null
            } else {
              _tmpSmsData = _stmt.getBlob(_columnIndexOfSmsData)
            }
            val _tmpSecureTransportText: String?
            if (_stmt.isNull(_columnIndexOfSecureTransportText)) {
              _tmpSecureTransportText = null
            } else {
              _tmpSecureTransportText = _stmt.getText(_columnIndexOfSecureTransportText)
            }
            val _tmpParticipantAddresses: String?
            if (_stmt.isNull(_columnIndexOfParticipantAddresses)) {
              _tmpParticipantAddresses = null
            } else {
              _tmpParticipantAddresses = _stmt.getText(_columnIndexOfParticipantAddresses)
            }
            _item = ThreadSummary(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpIsPinned,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnreadCount,_tmpSmsData,_tmpSecureTransportText,_tmpParticipantAddresses)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getThreadSummaries(folder: Int): PagingSource<Int, ThreadSummary> {
    val _sql: String = "SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, t.isArchive, t.isBlocked, (SELECT COUNT(*) FROM Conversations unread  WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId)  AND unread.read = 0) AS unreadCount, latest.sms_data AS smsData, latest.secure_transport_text AS secureTransportText, (SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp  WHERE tp.threadId = t.threadId) AS participantAddresses FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId WHERE t.address IS NOT NULL AND ((? = 0 AND t.isArchive = 0) OR (? = 1 AND t.isArchive = 1) OR (? = 2 AND t.type = 3) OR (? = 3 AND t.isMute = 1) OR (? = 4 AND t.isBlocked = 1)) ORDER BY CASE WHEN ? = 0 THEN t.isPinned ELSE 0 END DESC, t.date DESC, t.threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 2
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 3
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 4
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 5
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 6
      _stmt.bindLong(_argIndex, folder.toLong())
    }
    return object : LimitOffsetPagingSource<ThreadSummary>(_rawQuery, __db, "Conversations", "ThreadParticipants", "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<ThreadSummary> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfAddress: Int = 1
          val _columnIndexOfSnippet: Int = 2
          val _columnIndexOfDate: Int = 3
          val _columnIndexOfIsPinned: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfIsArchive: Int = 6
          val _columnIndexOfIsBlocked: Int = 7
          val _columnIndexOfUnreadCount: Int = 8
          val _columnIndexOfSmsData: Int = 9
          val _columnIndexOfSecureTransportText: Int = 10
          val _columnIndexOfParticipantAddresses: Int = 11
          val _result: MutableList<ThreadSummary> = mutableListOf()
          while (_stmt.step()) {
            val _item: ThreadSummary
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsPinned: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnreadCount: Int
            _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
            val _tmpSmsData: ByteArray?
            if (_stmt.isNull(_columnIndexOfSmsData)) {
              _tmpSmsData = null
            } else {
              _tmpSmsData = _stmt.getBlob(_columnIndexOfSmsData)
            }
            val _tmpSecureTransportText: String?
            if (_stmt.isNull(_columnIndexOfSecureTransportText)) {
              _tmpSecureTransportText = null
            } else {
              _tmpSecureTransportText = _stmt.getText(_columnIndexOfSecureTransportText)
            }
            val _tmpParticipantAddresses: String?
            if (_stmt.isNull(_columnIndexOfParticipantAddresses)) {
              _tmpParticipantAddresses = null
            } else {
              _tmpParticipantAddresses = _stmt.getText(_columnIndexOfParticipantAddresses)
            }
            _item = ThreadSummary(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpIsPinned,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnreadCount,_tmpSmsData,_tmpSecureTransportText,_tmpParticipantAddresses)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getThreadSummaries(folder: Int, threadIds: List<Int>): PagingSource<Int, ThreadSummary> {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("SELECT t.threadId, t.address, t.snippet, t.date, t.isPinned, t.isMute, t.isArchive, t.isBlocked, (SELECT COUNT(*) FROM Conversations unread  WHERE (unread.thread_id = t.threadId OR unread.mms_thread_id = t.threadId)  AND unread.read = 0) AS unreadCount, latest.sms_data AS smsData, latest.secure_transport_text AS secureTransportText, (SELECT GROUP_CONCAT(tp.address, ',') FROM ThreadParticipants tp  WHERE tp.threadId = t.threadId) AS participantAddresses FROM Threads t LEFT JOIN Conversations latest ON latest.id = t.conversationId WHERE t.threadId IN (")
    val _inputSize: Int = threadIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(") AND t.address IS NOT NULL AND ((")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 0 AND t.isArchive = 0) OR (")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 1 AND t.isArchive = 1) OR (")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 2 AND t.type = 3) OR (")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 3 AND t.isMute = 1) OR (")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 4 AND t.isBlocked = 1)) ORDER BY CASE WHEN ")
    _stringBuilder.append("?")
    _stringBuilder.append(" = 0 THEN t.isPinned ELSE 0 END DESC, t.date DESC, t.threadId DESC")
    val _sql: String = _stringBuilder.toString()
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      for (_item: Int in threadIds) {
        _stmt.bindLong(_argIndex, _item.toLong())
        _argIndex++
      }
      _argIndex = 1 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 2 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 3 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 4 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 5 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
      _argIndex = 6 + _inputSize
      _stmt.bindLong(_argIndex, folder.toLong())
    }
    return object : LimitOffsetPagingSource<ThreadSummary>(_rawQuery, __db, "Conversations", "ThreadParticipants", "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<ThreadSummary> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfAddress: Int = 1
          val _columnIndexOfSnippet: Int = 2
          val _columnIndexOfDate: Int = 3
          val _columnIndexOfIsPinned: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfIsArchive: Int = 6
          val _columnIndexOfIsBlocked: Int = 7
          val _columnIndexOfUnreadCount: Int = 8
          val _columnIndexOfSmsData: Int = 9
          val _columnIndexOfSecureTransportText: Int = 10
          val _columnIndexOfParticipantAddresses: Int = 11
          val _result: MutableList<ThreadSummary> = mutableListOf()
          while (_stmt.step()) {
            val _item_1: ThreadSummary
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsPinned: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnreadCount: Int
            _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
            val _tmpSmsData: ByteArray?
            if (_stmt.isNull(_columnIndexOfSmsData)) {
              _tmpSmsData = null
            } else {
              _tmpSmsData = _stmt.getBlob(_columnIndexOfSmsData)
            }
            val _tmpSecureTransportText: String?
            if (_stmt.isNull(_columnIndexOfSecureTransportText)) {
              _tmpSecureTransportText = null
            } else {
              _tmpSecureTransportText = _stmt.getText(_columnIndexOfSecureTransportText)
            }
            val _tmpParticipantAddresses: String?
            if (_stmt.isNull(_columnIndexOfParticipantAddresses)) {
              _tmpParticipantAddresses = null
            } else {
              _tmpParticipantAddresses = _stmt.getText(_columnIndexOfParticipantAddresses)
            }
            _item_1 = ThreadSummary(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpIsPinned,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnreadCount,_tmpSmsData,_tmpSecureTransportText,_tmpParticipantAddresses)
            _result.add(_item_1)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun unreadMessageCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM Conversations c INNER JOIN Threads t ON (t.threadId = c.thread_id OR t.threadId = c.mms_thread_id) WHERE c.read = 0 AND t.isArchive = 0 AND t.address IS NOT NULL"
    return createFlow(__db, false, arrayOf("Conversations", "Threads")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getArchived(): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE isArchive = 1 ORDER BY date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getType(type: Int): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE type = ? ORDER BY date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, type.toLong())
    }
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getIsMute(): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE isMute = 1 ORDER BY date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getIsBlocked(): PagingSource<Int, Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE isBlocked = 1 ORDER BY date DESC, threadId DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql)
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
          val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
          val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
          val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
          val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
          val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
          val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
          val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
          val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
          val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
          val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
          val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpIsMms: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp != 0
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpIsArchive: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp_2 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_3 != 0
            val _tmpUnread: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun `get`(address: String): Threads? {
    val _sql: String = "SELECT * FROM Threads WHERE address = ? ORDER BY date DESC"
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, address)
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
        val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
        val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _result: Threads?
        if (_stmt.step()) {
          val _tmpThreadId: Int
          _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
          val _tmpAddress: String
          _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          val _tmpSnippet: String
          _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpType: Int
          _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
          val _tmpConversationId: Long
          _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpIsMms: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
          _tmpIsMms = _tmp != 0
          val _tmpIsMute: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
          _tmpIsMute = _tmp_1 != 0
          val _tmpIsArchive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
          _tmpIsArchive = _tmp_2 != 0
          val _tmpIsBlocked: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp_3 != 0
          val _tmpUnread: Boolean
          val _tmp_4: Int
          _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
          _tmpUnread = _tmp_4 != 0
          val _tmpIsPinned: Boolean
          val _tmp_5: Int
          _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          _tmpIsPinned = _tmp_5 != 0
          _result = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun `get`(threadId: Int): Threads? {
    val _sql: String = "SELECT * FROM Threads WHERE threadId = ?"
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, threadId.toLong())
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
        val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
        val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _result: Threads?
        if (_stmt.step()) {
          val _tmpThreadId: Int
          _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
          val _tmpAddress: String
          _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          val _tmpSnippet: String
          _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpType: Int
          _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
          val _tmpConversationId: Long
          _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpIsMms: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
          _tmpIsMms = _tmp != 0
          val _tmpIsMute: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
          _tmpIsMute = _tmp_1 != 0
          val _tmpIsArchive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
          _tmpIsArchive = _tmp_2 != 0
          val _tmpIsBlocked: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp_3 != 0
          val _tmpUnread: Boolean
          val _tmp_4: Int
          _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
          _tmpUnread = _tmp_4 != 0
          val _tmpIsPinned: Boolean
          val _tmp_5: Int
          _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          _tmpIsPinned = _tmp_5 != 0
          _result = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllSnapshot(): List<Threads> {
    val _sql: String = "SELECT * FROM Threads WHERE address IS NOT NULL"
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfThreadId: Int = getColumnIndexOrThrow(_stmt, "threadId")
        val _columnIndexOfAddress: Int = getColumnIndexOrThrow(_stmt, "address")
        val _columnIndexOfSnippet: Int = getColumnIndexOrThrow(_stmt, "snippet")
        val _columnIndexOfDate: Int = getColumnIndexOrThrow(_stmt, "date")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfConversationId: Int = getColumnIndexOrThrow(_stmt, "conversationId")
        val _columnIndexOfIsMms: Int = getColumnIndexOrThrow(_stmt, "isMms")
        val _columnIndexOfIsMute: Int = getColumnIndexOrThrow(_stmt, "isMute")
        val _columnIndexOfIsArchive: Int = getColumnIndexOrThrow(_stmt, "isArchive")
        val _columnIndexOfIsBlocked: Int = getColumnIndexOrThrow(_stmt, "isBlocked")
        val _columnIndexOfUnread: Int = getColumnIndexOrThrow(_stmt, "unread")
        val _columnIndexOfIsPinned: Int = getColumnIndexOrThrow(_stmt, "isPinned")
        val _result: MutableList<Threads> = mutableListOf()
        while (_stmt.step()) {
          val _item: Threads
          val _tmpThreadId: Int
          _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
          val _tmpAddress: String
          _tmpAddress = _stmt.getText(_columnIndexOfAddress)
          val _tmpSnippet: String
          _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
          val _tmpDate: Long
          _tmpDate = _stmt.getLong(_columnIndexOfDate)
          val _tmpType: Int
          _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
          val _tmpConversationId: Long
          _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
          val _tmpIsMms: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsMms).toInt()
          _tmpIsMms = _tmp != 0
          val _tmpIsMute: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
          _tmpIsMute = _tmp_1 != 0
          val _tmpIsArchive: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsArchive).toInt()
          _tmpIsArchive = _tmp_2 != 0
          val _tmpIsBlocked: Boolean
          val _tmp_3: Int
          _tmp_3 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
          _tmpIsBlocked = _tmp_3 != 0
          val _tmpUnread: Boolean
          val _tmp_4: Int
          _tmp_4 = _stmt.getLong(_columnIndexOfUnread).toInt()
          _tmpUnread = _tmp_4 != 0
          val _tmpIsPinned: Boolean
          val _tmp_5: Int
          _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
          _tmpIsPinned = _tmp_5 != 0
          _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun search(query: String): PagingSource<Int, Threads> {
    val _sql: String = "SELECT tc.threadId, tc.isArchive, tc.address, tc.conversationId, c.date AS date, tc.isMute, tc.type, tc.unread, tc.isMms, tc.isBlocked, tc.isPinned,SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, c.body AS snippet FROM Threads tc LEFT JOIN Conversations c ON c.thread_id = tc.threadId WHERE tc.isArchive = 0 AND (c.type IS NOT 3 AND c.body like '%' || ? || '%') GROUP BY thread_id ORDER BY date DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindText(_argIndex, query)
    }
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads", "Conversations") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfIsArchive: Int = 1
          val _columnIndexOfAddress: Int = 2
          val _columnIndexOfConversationId: Int = 3
          val _columnIndexOfDate: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfType: Int = 6
          val _columnIndexOfUnread: Int = 7
          val _columnIndexOfIsMms: Int = 8
          val _columnIndexOfIsBlocked: Int = 9
          val _columnIndexOfIsPinned: Int = 10
          val _columnIndexOfSnippet: Int = 12
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpIsArchive: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp != 0
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpUnread: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_2 != 0
            val _tmpIsMms: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp_3 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun search(query: String, threadId: Int): PagingSource<Int, Threads> {
    val _sql: String = "SELECT tc.threadId, tc.isArchive, tc.address, tc.conversationId, c.date AS date, tc.isMute, tc.type, tc.unread, tc.isMms, tc.isBlocked, tc.isPinned,SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, c.body AS snippet FROM Threads tc LEFT JOIN Conversations c ON c.thread_id = tc.threadId WHERE c.thread_id = ? AND tc.isArchive = 0 AND (c.type IS NOT 3 AND c.body like '%' || ? || '%') GROUP BY thread_id ORDER BY date DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindLong(_argIndex, threadId.toLong())
      _argIndex = 2
      _stmt.bindText(_argIndex, query)
    }
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads", "Conversations") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfIsArchive: Int = 1
          val _columnIndexOfAddress: Int = 2
          val _columnIndexOfConversationId: Int = 3
          val _columnIndexOfDate: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfType: Int = 6
          val _columnIndexOfUnread: Int = 7
          val _columnIndexOfIsMms: Int = 8
          val _columnIndexOfIsBlocked: Int = 9
          val _columnIndexOfIsPinned: Int = 10
          val _columnIndexOfSnippet: Int = 12
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpIsArchive: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp != 0
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpUnread: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_2 != 0
            val _tmpIsMms: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp_3 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun searchByAddress(query: String, address: String): PagingSource<Int, Threads> {
    val _sql: String = "SELECT tc.threadId, tc.isArchive, tc.address, tc.conversationId, c.date AS date, tc.isMute, tc.type, tc.unread, tc.isMms, tc.isBlocked, tc.isPinned,SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) as unreadCount, c.body AS snippet FROM Threads tc LEFT JOIN Conversations c ON c.thread_id = tc.threadId WHERE tc.address = ? AND tc.isArchive = 0 AND (c.type IS NOT 3 AND c.body like '%' || ? || '%') GROUP BY thread_id ORDER BY date DESC"
    val _rawQuery: RoomRawQuery = RoomRawQuery(_sql) { _stmt ->
      var _argIndex: Int = 1
      _stmt.bindText(_argIndex, address)
      _argIndex = 2
      _stmt.bindText(_argIndex, query)
    }
    return object : LimitOffsetPagingSource<Threads>(_rawQuery, __db, "Threads", "Conversations") {
      protected override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<Threads> = performSuspending(__db, true, false) { _connection ->
        val _stmt: SQLiteStatement = _connection.prepare(limitOffsetQuery.sql)
        limitOffsetQuery.getBindingFunction().invoke(_stmt)
        try {
          val _columnIndexOfThreadId: Int = 0
          val _columnIndexOfIsArchive: Int = 1
          val _columnIndexOfAddress: Int = 2
          val _columnIndexOfConversationId: Int = 3
          val _columnIndexOfDate: Int = 4
          val _columnIndexOfIsMute: Int = 5
          val _columnIndexOfType: Int = 6
          val _columnIndexOfUnread: Int = 7
          val _columnIndexOfIsMms: Int = 8
          val _columnIndexOfIsBlocked: Int = 9
          val _columnIndexOfIsPinned: Int = 10
          val _columnIndexOfSnippet: Int = 12
          val _result: MutableList<Threads> = mutableListOf()
          while (_stmt.step()) {
            val _item: Threads
            val _tmpThreadId: Int
            _tmpThreadId = _stmt.getLong(_columnIndexOfThreadId).toInt()
            val _tmpIsArchive: Boolean
            val _tmp: Int
            _tmp = _stmt.getLong(_columnIndexOfIsArchive).toInt()
            _tmpIsArchive = _tmp != 0
            val _tmpAddress: String
            _tmpAddress = _stmt.getText(_columnIndexOfAddress)
            val _tmpConversationId: Long
            _tmpConversationId = _stmt.getLong(_columnIndexOfConversationId)
            val _tmpDate: Long
            _tmpDate = _stmt.getLong(_columnIndexOfDate)
            val _tmpIsMute: Boolean
            val _tmp_1: Int
            _tmp_1 = _stmt.getLong(_columnIndexOfIsMute).toInt()
            _tmpIsMute = _tmp_1 != 0
            val _tmpType: Int
            _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
            val _tmpUnread: Boolean
            val _tmp_2: Int
            _tmp_2 = _stmt.getLong(_columnIndexOfUnread).toInt()
            _tmpUnread = _tmp_2 != 0
            val _tmpIsMms: Boolean
            val _tmp_3: Int
            _tmp_3 = _stmt.getLong(_columnIndexOfIsMms).toInt()
            _tmpIsMms = _tmp_3 != 0
            val _tmpIsBlocked: Boolean
            val _tmp_4: Int
            _tmp_4 = _stmt.getLong(_columnIndexOfIsBlocked).toInt()
            _tmpIsBlocked = _tmp_4 != 0
            val _tmpIsPinned: Boolean
            val _tmp_5: Int
            _tmp_5 = _stmt.getLong(_columnIndexOfIsPinned).toInt()
            _tmpIsPinned = _tmp_5 != 0
            val _tmpSnippet: String
            _tmpSnippet = _stmt.getText(_columnIndexOfSnippet)
            _item = Threads(_tmpThreadId,_tmpAddress,_tmpSnippet,_tmpDate,_tmpType,_tmpConversationId,_tmpIsMms,_tmpIsMute,_tmpIsArchive,_tmpIsBlocked,_tmpUnread,_tmpIsPinned)
            _result.add(_item)
          }
          _result
        } finally {
          _stmt.close()
        }
      }
    }
  }

  public override fun getUnreadCount(threadId: Int): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM Conversations WHERE thread_id = ? AND read = 0"
    return createFlow(__db, false, arrayOf("Conversations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, threadId.toLong())
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun setMute(isMute: Boolean, threadId: Int) {
    val _sql: String = "UPDATE Threads SET isMute = ? WHERE threadId = ?"
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isMute) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, threadId.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun setIsBlocked(isBlocked: Boolean, addresses: List<String>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("UPDATE Threads SET isBlocked = ")
    _stringBuilder.append("?")
    _stringBuilder.append(" WHERE address IN (")
    val _inputSize: Int = addresses.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (isBlocked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        for (_item: String in addresses) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun markAllThreadsAsRead(): Int {
    val _sql: String = "UPDATE Threads SET unread = 0"
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun markAllConversationRowsAsRead(): Int {
    val _sql: String = "UPDATE Conversations SET read = 1"
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun markThreadsAsRead(threadIds: List<Int>): Int {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("UPDATE Threads SET unread = 0 WHERE threadId IN (")
    val _inputSize: Int = threadIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: Int in threadIds) {
          _stmt.bindLong(_argIndex, _item.toLong())
          _argIndex++
        }
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun markConversationRowsAsRead(threadIds: List<Int>): Int {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("UPDATE Conversations SET read = 1 WHERE thread_id IN (")
    val _inputSize: Int = threadIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(") OR mms_thread_id IN (")
    val _inputSize_1: Int = threadIds.size
    appendPlaceholders(_stringBuilder, _inputSize_1)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: Int in threadIds) {
          _stmt.bindLong(_argIndex, _item.toLong())
          _argIndex++
        }
        _argIndex = 1 + _inputSize
        for (_item_1: Int in threadIds) {
          _stmt.bindLong(_argIndex, _item_1.toLong())
          _argIndex++
        }
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun deleteConversations(threads: List<Int>) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("DELETE FROM Conversations WHERE thread_id IN (")
    val _inputSize: Int = threads.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        for (_item: Int in threads) {
          _stmt.bindLong(_argIndex, _item.toLong())
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun deleteAll() {
    val _sql: String = "DELETE FROM Threads"
    return performBlocking(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
