package com.afkanerd.deku.messages

import android.content.Context
import android.provider.Telephony
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afkanerd.smswithoutborders_libsmsmms.data.DatabaseImpl
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeTimelinePagingInstrumentedTest {
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
    fun fiftyThousandRowsLoadOnlyTheRequestedTimelineWindow() = runBlocking {
        val dao = requireNotNull(database.conversationsDao())
        dao.insertConversations(
            List(TOTAL_MESSAGES) { index ->
                val id = index + 1L
                Conversations(
                    sms = SmsMmsNatives.Sms(
                        _id = id,
                        thread_id = THREAD_ID,
                        address = "+15550000000",
                        date = id,
                        date_sent = id,
                        read = 1,
                        status = Telephony.Sms.STATUS_COMPLETE,
                        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        body = "message-$id",
                        sub_id = 1,
                    )
                )
            }
        )

        val result = dao.getConversations(THREAD_ID).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = REQUESTED_WINDOW,
                placeholdersEnabled = false,
            )
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertEquals(REQUESTED_WINDOW, page.data.size)
        assertEquals(TOTAL_MESSAGES.toLong(), page.data.first().sms?._id)
        assertTrue(page.nextKey != null)
    }

    private companion object {
        const val THREAD_ID = 5150
        const val TOTAL_MESSAGES = 50_000
        const val REQUESTED_WINDOW = 100
    }
}
