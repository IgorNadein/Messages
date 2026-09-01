package com.afkanerd.smswithoutborders_libsmsmms.data

import android.content.Context
import android.widget.Toast
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afkanerd.lib_smsmms_android.R
import com.afkanerd.smswithoutborders_libsmsmms.data.Cryptography.getDatabasePassword
import com.afkanerd.smswithoutborders_libsmsmms.data.dao.ConversationsDao
import com.afkanerd.smswithoutborders_libsmsmms.data.dao.ThreadsDao
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Threads
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.ThreadParticipant
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getNativesLoaded
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.setNativesLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import kotlin.concurrent.Volatile
import kotlin.jvm.java


@Database(
    entities = [
        Conversations::class,
        Threads::class,
        ThreadParticipant::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from=2, to=3),
        AutoMigration(from=3, to=4, spec= MigrateFrom3To4::class),
        AutoMigration(from=4, to=5),
        AutoMigration(from=5, to=6),
    ]
)
abstract class DatabaseImpl : RoomDatabase() {
    @Volatile
    private var retainedPassword: Cryptography.SecretBytes? = null

    abstract fun conversationsDao(): ConversationsDao?
    abstract fun threadsDao(): ThreadsDao?

    init {
        System.loadLibrary("sqlcipher")
    }

    private fun retainPassword(password: Cryptography.SecretBytes) {
        check(retainedPassword == null) { "Database password is already attached" }
        retainedPassword = password
    }

    override fun close() {
        try {
            super.close()
        } finally {
            retainedPassword?.close()
            retainedPassword = null
        }
    }

    companion object {
        @Volatile
        private var datastore: DatabaseImpl? = null
        private var databaseName: String = "afkanerd.smswithoutborders.libsmsmms.db"
        private var dbKeystoreAlias: String = "afkanerd.smswithoutborders.sms_mms_keystore_alias"

        @Synchronized
        fun setDatabaseName(databaseName: String) {
            this.databaseName = databaseName
        }

        @Synchronized
        fun getDatabaseImpl(context: Context): DatabaseImpl {
            if (datastore == null) {
                create(context)
            }
            return datastore!!
        }

        private fun create(context: Context) {
            System.loadLibrary("sqlcipher")
            val password = getDatabasePassword(context, dbKeystoreAlias)
            try {
                val databaseFile = context.getDatabasePath(databaseName)

                password.useRaw { rawBytes ->
                    migrateLegacyZeroPasswordDatabase(databaseFile, rawBytes)
                    val candidate = Room.databaseBuilder(
                        context = context.applicationContext,
                        klass = DatabaseImpl::class.java,
                        databaseFile.absolutePath,
                    )
                        .openHelperFactory(SupportOpenHelperFactory(rawBytes))
                        .fallbackToDestructiveMigration(false)
                        .build()
                    try {
                        // SQLCipher's connection pool retains this passphrase to open
                        // additional connections. Keep it alive until Room is closed.
                        candidate.openHelper.writableDatabase
                        candidate.retainPassword(password)
                        datastore = candidate
                    } catch(e: Exception) {
                        candidate.close()
                        throw e
                    }
                }
            } catch(e: Exception) {
                password.close()
                throw e
            }
        }

        private fun migrateLegacyZeroPasswordDatabase(
            databaseFile: java.io.File,
            password: ByteArray,
        ) {
            if(!databaseFile.exists()) return

            try {
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    password,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                ).close()
                return
            } catch(currentPasswordError: Exception) {
                val legacyZeroPassword = ByteArray(password.size)
                try {
                    SQLiteDatabase.openDatabase(
                        databaseFile.absolutePath,
                        legacyZeroPassword,
                        null,
                        SQLiteDatabase.OPEN_READWRITE,
                        null,
                    ).use { legacyDatabase ->
                        legacyDatabase.changePassword(password)
                    }
                } catch(legacyPasswordError: Exception) {
                    currentPasswordError.addSuppressed(legacyPasswordError)
                    throw currentPasswordError
                } finally {
                    legacyZeroPassword.fill(0)
                }
            }
        }
    }
}


@DeleteColumn(
    tableName = "Threads",
    columnName = "unreadCount"
)
class MigrateFrom3To4 : AutoMigrationSpec
