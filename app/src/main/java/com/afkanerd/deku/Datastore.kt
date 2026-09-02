package com.afkanerd.deku

import android.content.Context
import androidx.annotation.NonNull
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameTable
import androidx.room.Room
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afkanerd.deku.Datastore.Migrate16To17
import com.afkanerd.deku.Datastore.Migrate19To20
import com.afkanerd.deku.Datastore.Migrate20To21
import com.afkanerd.deku.Datastore.Migrate21To22
import com.afkanerd.deku.Datastore.Migrate22To23
import com.afkanerd.deku.Datastore.Migrate28To29
import com.afkanerd.deku.RemoteListeners.Models.RemoteListener.RemoteListenersQueuesDao
import com.afkanerd.deku.RemoteListeners.Models.RemoteListenerDAO
import com.afkanerd.deku.RemoteListeners.Models.RemoteListeners
import com.afkanerd.deku.RemoteListeners.Models.RemoteListenersQueues
import com.afkanerd.deku.Router.data.dao.GatewayServerDAO
import com.afkanerd.deku.Router.data.models.GatewayServer
import com.afkanerd.deku.attachments.storage.AttachmentTransferDao
import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentOfferFragmentDao
import com.afkanerd.deku.attachments.storage.AttachmentOfferFragmentEntity
import com.afkanerd.smswithoutborders_libsmsmms.data.Cryptography.getDatabasePassword
import com.afkanerd.smswithoutborders_libsmsmms.data.DatabaseImpl
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import kotlin.concurrent.Volatile


@Database(
    entities = [GatewayServer::class, RemoteListenersQueues::class, RemoteListeners::class,
        AttachmentTransferEntity::class, AttachmentOfferFragmentEntity::class],
    version = 33,
    autoMigrations = [AutoMigration(from = 9, to = 10), AutoMigration(
        from = 10,
        to = 11
    ), AutoMigration(from = 11, to = 12), AutoMigration(
        from = 12,
        to = 13
    ), AutoMigration(from = 13, to = 14), AutoMigration(
        from = 14,
        to = 15
    ), AutoMigration(from = 15, to = 16), AutoMigration(
        from = 16,
        to = 17,
        spec = Migrate16To17::class
    ), AutoMigration(from = 17, to = 18), AutoMigration(
        from = 18,
        to = 19
    ), AutoMigration(from = 19, to = 20, spec = Migrate19To20::class), AutoMigration(
        from = 20,
        to = 21,
        spec = Migrate20To21::class
    ), AutoMigration(from = 21, to = 22, spec = Migrate21To22::class), AutoMigration(
        from = 22,
        to = 23,
        spec = Migrate22To23::class
    ), AutoMigration(from = 23, to = 24), AutoMigration(
        from = 24,
        to = 25
    ), AutoMigration(from = 25, to = 26), AutoMigration(
        from = 26,
        to = 27
    ), AutoMigration(from = 27, to = 28), AutoMigration(
        from = 28,
        to = 29,
        spec = Migrate28To29::class
    ), AutoMigration(from = 29, to = 30), AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32), AutoMigration(from = 32, to = 33)]
)
abstract class Datastore : RoomDatabase() {
    @Volatile
    private var retainedPassword: com.afkanerd.smswithoutborders_libsmsmms.data.Cryptography.SecretBytes? = null

    abstract fun gatewayServerDAO(): GatewayServerDAO

    abstract fun remoteListenerDAO(): RemoteListenerDAO
    abstract fun remoteListenersQueuesDao(): RemoteListenersQueuesDao
    abstract fun attachmentTransferDao(): AttachmentTransferDao
    abstract fun attachmentOfferFragmentDao(): AttachmentOfferFragmentDao

    init {
        System.loadLibrary("sqlcipher")
    }

    private fun retainPassword(
        password: com.afkanerd.smswithoutborders_libsmsmms.data.Cryptography.SecretBytes,
    ) {
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

    @DeleteTable(tableName = "CustomKeyStore")
    internal class Migrate16To17 : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "Conversation",
            columnName = "isBlocked"
        ),
        DeleteColumn(
            tableName = "Conversation",
            columnName = "isMute"
        ),
        DeleteColumn(tableName = "Conversation", columnName = "isSecured")
    )
    @DeleteTable.Entries(DeleteTable(tableName = "ThreadedConversations"))
    internal class Migrate19To20 : AutoMigrationSpec

    @DeleteTable.Entries(DeleteTable(tableName = "ConversationsThreadsEncryption"))
    internal class Migrate20To21 : AutoMigrationSpec

    @RenameTable.Entries(
        RenameTable(
            fromTableName = "GatewayClientProjects",
            toTableName = "RemoteListenersQueues"
        )
    )
    internal class Migrate21To22 : AutoMigrationSpec

    @RenameTable.Entries(
        RenameTable(
            fromTableName = "GatewayClient",
            toTableName = "RemoteListeners"
        )
    )
    internal class Migrate22To23 : AutoMigrationSpec

    @DeleteTable.Entries(
        DeleteTable(tableName = "Archive"),
        DeleteTable(tableName = "Conversation"),
        DeleteTable(
            tableName = "ThreadsConfigurations"
        )
    )
    internal class Migrate28To29 : AutoMigrationSpec

    companion object {
        @Volatile
        private var datastore: Datastore? = null
        private const val dbKeystoreAlias = "afkanerd.smswithoutborders.deku.sms_mms_keystore_alias"
        @JvmField
        var databaseName: String = "afkanerd.smswithoutborders.dekusms.db"

        @JvmStatic
        @Synchronized
        fun getDatastore(context: Context): Datastore {
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
                    migrateLegacyZeroPassword(databaseFile, rawBytes)
                    val database = databaseBuilder(
                        context = context.applicationContext,
                        klass = Datastore::class.java,
                        databaseFile.absolutePath,
                    )
                        .openHelperFactory(SupportOpenHelperFactory(rawBytes))
                        .fallbackToDestructiveMigration(false)
                        .build()
                    try {
                        // SQLCipher's connection pool retains this passphrase to open
                        // additional connections. Keep it alive until Room is closed.
                        database.openHelper.writableDatabase
                        database.retainPassword(password)
                        datastore = database
                    } catch(error: Exception) {
                        database.close()
                        throw error
                    }
                }
            } catch(error: Exception) {
                password.close()
                throw error
            }
        }

        /** Migrates databases created by the old zeroed-password bug. */
        private fun migrateLegacyZeroPassword(databaseFile: File, password: ByteArray) {
            if(!databaseFile.exists()) return

            var database: SQLiteDatabase? = null
            val passwordError = try {
                database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    password,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                )
                return
            } catch(error: Exception) {
                error
            } finally {
                database?.close()
            }

            val legacyPassword = ByteArray(password.size)
            try {
                database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    legacyPassword,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                )
                database.changePassword(password)
            } catch(error: Exception) {
                passwordError.addSuppressed(error)
                throw passwordError
            } finally {
                database?.close()
                legacyPassword.fill(0)
            }
        }
    }
}
