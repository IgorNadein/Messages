package com.afkanerd.smswithoutborders_libsmsmms.data

import android.content.Context
import android.provider.Telephony
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Threads
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ThreadActionsDaoInstrumentedTest {
    private lateinit var database: DatabaseImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DatabaseImpl::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun markAllReadUpdatesThreadAndConversationRowsUsedByComposeBadge() {
        val conversations = requireNotNull(database.conversationsDao())
        val threads = requireNotNull(database.threadsDao())
        val conversationId = conversations.insertConversation(
            Conversations(
                sms = SmsMmsNatives.Sms(
                    _id = 7001,
                    thread_id = 701,
                    address = "+79990000701",
                    date = 1,
                    date_sent = 1,
                    read = 0,
                    status = Telephony.Sms.STATUS_COMPLETE,
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    body = "unread",
                    sub_id = 1,
                )
            )
        )
        conversations.insertThread(
            Threads(
                threadId = 701,
                address = "+79990000701",
                snippet = "unread",
                date = 1,
                type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                conversationId = conversationId,
                isMms = false,
                unread = true,
            )
        )

        threads.markAllAsRead()

        assertFalse(requireNotNull(threads.get(701)).unread)
        assertEquals(1, requireNotNull(conversations.getConversation(conversationId)).sms!!.read)
    }

    @Test
    fun unreadMessageCountIsExactAndExcludesArchivedThreads() = runBlocking {
        val conversations = requireNotNull(database.conversationsDao())
        val threads = requireNotNull(database.threadsDao())

        fun insert(threadId: Int, read: Int, archived: Boolean) {
            val conversationId = conversations.insertConversation(
                Conversations(
                    sms = SmsMmsNatives.Sms(
                        _id = threadId.toLong(),
                        thread_id = threadId,
                        address = "+79990000$threadId",
                        date = threadId.toLong(),
                        date_sent = threadId.toLong(),
                        read = read,
                        status = Telephony.Sms.STATUS_COMPLETE,
                        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        body = "message-$threadId",
                        sub_id = 1,
                    )
                )
            )
            conversations.insertThread(
                Threads(
                    threadId = threadId,
                    address = "+79990000$threadId",
                    snippet = "message-$threadId",
                    date = threadId.toLong(),
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    conversationId = conversationId,
                    isMms = false,
                    isArchive = archived,
                    unread = read == 0,
                )
            )
        }

        insert(threadId = 711, read = 0, archived = false)
        insert(threadId = 712, read = 0, archived = false)
        insert(threadId = 713, read = 1, archived = false)
        insert(threadId = 714, read = 0, archived = true)

        assertEquals(2, threads.unreadMessageCount().first())
    }

    @Test
    fun markReadUpdatesOnlyTheRelatedConversationThreads() = runBlocking {
        val conversations = requireNotNull(database.conversationsDao())
        val threads = requireNotNull(database.threadsDao())

        fun insert(threadId: Int) {
            val conversationId = conversations.insertConversation(
                Conversations(
                    sms = SmsMmsNatives.Sms(
                        _id = threadId.toLong(),
                        thread_id = threadId,
                        address = "+79990000$threadId",
                        date = threadId.toLong(),
                        date_sent = threadId.toLong(),
                        read = 0,
                        status = Telephony.Sms.STATUS_COMPLETE,
                        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        body = "message-$threadId",
                        sub_id = 1,
                    )
                )
            )
            conversations.insertThread(
                Threads(
                    threadId = threadId,
                    address = "+79990000$threadId",
                    snippet = "message-$threadId",
                    date = threadId.toLong(),
                    type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                    conversationId = conversationId,
                    isMms = false,
                    unread = true,
                )
            )
        }

        insert(721)
        insert(722)
        insert(723)

        threads.markAsRead(listOf(721, 722))

        assertFalse(requireNotNull(threads.get(721)).unread)
        assertFalse(requireNotNull(threads.get(722)).unread)
        assertEquals(true, requireNotNull(threads.get(723)).unread)
        assertEquals(1, threads.unreadMessageCount().first())
    }
}
