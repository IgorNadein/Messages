package com.afkanerd.smswithoutborders_libsmsmms.`data`

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class DatabaseImpl_AutoMigration_2_3_Impl : Migration {
  public constructor() : super(2, 3)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("ALTER TABLE `Threads` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
  }
}
