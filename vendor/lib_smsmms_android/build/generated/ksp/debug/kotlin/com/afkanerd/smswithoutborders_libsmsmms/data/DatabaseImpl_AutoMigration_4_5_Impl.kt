package com.afkanerd.smswithoutborders_libsmsmms.`data`

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class DatabaseImpl_AutoMigration_4_5_Impl : Migration {
  public constructor() : super(4, 5)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("ALTER TABLE `Conversations` ADD COLUMN `secure_transport_text` TEXT DEFAULT NULL")
  }
}
