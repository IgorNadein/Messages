package com.afkanerd.smswithoutborders_libsmsmms.`data`

import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class DatabaseImpl_AutoMigration_3_4_Impl : Migration {
  private val callback: AutoMigrationSpec = MigrateFrom3To4()

  public constructor() : super(3, 4)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_Threads` (`threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, `snippet` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` INTEGER NOT NULL, `conversationId` INTEGER NOT NULL, `isMms` INTEGER NOT NULL, `isMute` INTEGER NOT NULL, `isArchive` INTEGER NOT NULL, `isBlocked` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`threadId`))")
    connection.execSQL("INSERT INTO `_new_Threads` (`threadId`,`address`,`snippet`,`date`,`type`,`conversationId`,`isMms`,`isMute`,`isArchive`,`isBlocked`,`unread`,`isPinned`) SELECT `threadId`,`address`,`snippet`,`date`,`type`,`conversationId`,`isMms`,`isMute`,`isArchive`,`isBlocked`,`unread`,`isPinned` FROM `Threads`")
    connection.execSQL("DROP TABLE `Threads`")
    connection.execSQL("ALTER TABLE `_new_Threads` RENAME TO `Threads`")
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Threads_address` ON `Threads` (`address`)")
    callback.onPostMigrate(connection)
  }
}
