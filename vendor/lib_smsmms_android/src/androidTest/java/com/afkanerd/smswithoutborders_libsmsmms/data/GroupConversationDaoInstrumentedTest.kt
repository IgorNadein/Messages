package com.afkanerd.smswithoutborders_libsmsmms.data

import android.content.Context
import android.provider.Telephony
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupConversationDaoInstrumentedTest {
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
    fun incomingGroupMmsKeepsThreadMembersSeparateFromEachMessageAuthor() {
        val dao = requireNotNull(database.conversationsDao())
        val participants = listOf(FIRST_MEMBER, SECOND_MEMBER)

        val firstId = dao.insert(groupMms(messageId = 1, sender = FIRST_MEMBER, participants))
        val secondId = dao.insert(groupMms(messageId = 2, sender = SECOND_MEMBER, participants))

        assertEquals(participants.sorted(), dao.getThreadParticipantAddresses(THREAD_ID))
        assertEquals(participants.joinToString(","), dao.getThread(THREAD_ID)?.address)
        assertEquals(FIRST_MEMBER, dao.getConversation(firstId)?.sender_address)
        assertEquals(SECOND_MEMBER, dao.getConversation(secondId)?.sender_address)
    }

    private fun groupMms(
        messageId: Long,
        sender: String,
        participants: List<String>,
    ): Conversations = Conversations(
        sms = SmsMmsNatives.Sms(
            _id = messageId,
            thread_id = THREAD_ID,
            address = participants.joinToString(","),
            date = messageId,
            date_sent = messageId,
            read = 1,
            status = Telephony.Sms.STATUS_COMPLETE,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            body = "group-$messageId",
            sub_id = 1,
        ),
        mms = SmsMmsNatives.Mms(
            _id = messageId,
            thread_id = THREAD_ID,
            date = messageId,
            date_sent = messageId,
            msg_box = Telephony.Mms.MESSAGE_BOX_INBOX,
            sub_id = 1,
        ),
        sender_address = sender,
        mms_text = "group-$messageId",
    ).apply {
        participantAddresses = participants
    }

    private companion object {
        const val THREAD_ID = 8801
        const val FIRST_MEMBER = "+79990008801"
        const val SECOND_MEMBER = "+79990008802"
    }
}
