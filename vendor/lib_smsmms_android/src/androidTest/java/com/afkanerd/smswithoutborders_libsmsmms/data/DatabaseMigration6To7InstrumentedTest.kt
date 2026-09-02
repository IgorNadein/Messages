package com.afkanerd.smswithoutborders_libsmsmms.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration6To7InstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DatabaseImpl::class.java,
    )

    @Test
    fun migrationPreservesThreadsAndAddsPagingIndexes() {
        migrationHelper.createDatabase(DATABASE_NAME, 6).apply {
            execSQL(
                "INSERT INTO Threads " +
                    "(threadId,address,snippet,date,type,conversationId,isMms,isMute," +
                    "isArchive,isBlocked,unread,isPinned) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(THREAD_ID, ADDRESS, "preserved", 10L, 1, 1L, 0, 0, 0, 0, 0, 0),
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, DatabaseImpl::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val sqlite = migrated.openHelper.writableDatabase
            assertEquals(ADDRESS, migrated.conversationsDao()?.getThread(THREAD_ID)?.address)
            val indexes = buildSet {
                sqlite.query("PRAGMA index_list('Conversations')").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while(cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            assertTrue("index_Conversations_thread_id_read" in indexes)
            assertTrue("index_Conversations_mms_thread_id_read" in indexes)
        } finally {
            migrated.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-6-to-7.db"
        const val THREAD_ID = 9902
        const val ADDRESS = "+79990009902"
    }
}
