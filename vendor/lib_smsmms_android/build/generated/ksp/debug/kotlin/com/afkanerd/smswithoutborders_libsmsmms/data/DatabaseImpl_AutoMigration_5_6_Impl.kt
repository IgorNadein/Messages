package com.afkanerd.smswithoutborders_libsmsmms.`data`

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class DatabaseImpl_AutoMigration_5_6_Impl : Migration {
  public constructor() : super(5, 6)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("ALTER TABLE `Conversations` ADD COLUMN `sender_address` TEXT DEFAULT NULL")
    connection.execSQL("CREATE TABLE IF NOT EXISTS `ThreadParticipants` (`threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, PRIMARY KEY(`threadId`, `address`))")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_ThreadParticipants_address` ON `ThreadParticipants` (`address`)")
  }
}
