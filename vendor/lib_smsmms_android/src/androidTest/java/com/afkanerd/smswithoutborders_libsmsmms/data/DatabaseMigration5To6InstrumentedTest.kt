package com.afkanerd.smswithoutborders_libsmsmms.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.ThreadParticipant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration5To6InstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DatabaseImpl::class.java,
    )

    @Test
    fun migrationPreservesExistingThreadsAndAddsNormalizedGroupStorage() {
        migrationHelper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL(
                "INSERT INTO Threads " +
                    "(threadId,address,snippet,date,type,conversationId,isMms,isMute," +
                    "isArchive,isBlocked,unread,isPinned) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(
                    THREAD_ID,
                    OLD_ADDRESS,
                    "old message",
                    10L,
                    1,
                    1L,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                ),
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, DatabaseImpl::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            migrated.openHelper.writableDatabase
            val dao = requireNotNull(migrated.conversationsDao())
            assertEquals(OLD_ADDRESS, dao.getThread(THREAD_ID)?.address)

            dao.insertThreadParticipants(
                listOf(
                    ThreadParticipant(THREAD_ID, FIRST_MEMBER),
                    ThreadParticipant(THREAD_ID, SECOND_MEMBER),
                )
            )
            assertEquals(
                listOf(FIRST_MEMBER, SECOND_MEMBER),
                dao.getThreadParticipantAddresses(THREAD_ID),
            )
        } finally {
            migrated.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-5-to-6.db"
        const val THREAD_ID = 9901
        const val OLD_ADDRESS = "+79990009901"
        const val FIRST_MEMBER = "+79990009901"
        const val SECOND_MEMBER = "+79990009902"
    }
}
