package com.afkanerd.smswithoutborders_libsmsmms.data

import android.provider.Telephony
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.dao.withLatestMessage
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Threads
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadStateRegressionTest {
    @Test
    fun insertingLatestMessagePreservesPinnedBlockedMutedAndArchivedFlags() {
        val existing = Threads(
            threadId = THREAD_ID,
            address = ADDRESS,
            snippet = "old",
            date = 1,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            conversationId = 1,
            isMms = false,
            isMute = true,
            isArchive = true,
            isBlocked = true,
            isPinned = true,
        )
        val latest = SmsMmsNatives.Sms(
            _id = 2,
            thread_id = THREAD_ID,
            address = ADDRESS,
            date = 2,
            date_sent = 2,
            read = 1,
            status = Telephony.Sms.STATUS_COMPLETE,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            body = "new",
            sub_id = 1,
        )

        val updated = existing.withLatestMessage(
            sms = latest,
            keepArchived = true,
            isMms = false,
            conversationId = 2,
        )
        assertTrue(updated.isPinned)
        assertTrue(updated.isBlocked)
        assertTrue(updated.isMute)
        assertTrue(updated.isArchive)
    }

    private companion object {
        const val THREAD_ID = 91
        const val ADDRESS = "+79990000091"
    }
}
