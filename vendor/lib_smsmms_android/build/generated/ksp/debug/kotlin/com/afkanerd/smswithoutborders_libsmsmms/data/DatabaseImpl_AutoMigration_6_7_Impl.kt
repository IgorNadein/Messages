package com.afkanerd.smswithoutborders_libsmsmms.`data`

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class DatabaseImpl_AutoMigration_6_7_Impl : Migration {
  public constructor() : super(6, 7)

  public override fun migrate(connection: SQLiteConnection) {
    connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_Conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sms_data` BLOB, `secure_transport_text` TEXT, `sender_address` TEXT, `mms_text` TEXT, `mms_content_uri` TEXT, `mms_mimetype` TEXT, `mms_filename` TEXT, `mms_filepath` TEXT, `_id` INTEGER, `thread_id` INTEGER, `address` TEXT, `person` TEXT, `date` INTEGER, `date_sent` INTEGER, `protocol` TEXT, `read` INTEGER, `status` INTEGER, `type` INTEGER, `reply_path_present` TEXT, `subject` TEXT, `body` TEXT, `service_center` TEXT, `locked` INTEGER, `sub_id` INTEGER, `error_code` INTEGER, `creator` TEXT, `seen` INTEGER, `mms__id` INTEGER, `mms_thread_id` INTEGER, `mms_date` INTEGER, `mms_date_sent` INTEGER, `mms_msg_box` INTEGER, `mms_read` INTEGER, `mms_m_id` TEXT, `mms_sub` TEXT, `mms_sub_cs` INTEGER, `mms_ct_t` TEXT, `mms_ct_l` TEXT, `mms_exp` TEXT, `mms_m_cls` TEXT, `mms_m_type` INTEGER, `mms_v` INTEGER, `mms_m_size` INTEGER, `mms_pri` INTEGER, `mms_rr` INTEGER, `mms_rpt_a` TEXT, `mms_resp_st` TEXT, `mms_st` TEXT, `mms_tr_id` TEXT, `mms_retr_st` TEXT, `mms_retr_txt` TEXT, `mms_retr_txt_cs` TEXT, `mms_read_status` TEXT, `mms_ct_cls` TEXT, `mms_resp_txt` TEXT, `mms_d_tm` TEXT, `mms_d_rpt` INTEGER, `mms_locked` INTEGER, `mms_sub_id` INTEGER, `mms_seen` INTEGER, `mms_creator` TEXT, `mms_text_only` INTEGER)")
    connection.execSQL("INSERT INTO `_new_Conversations` (`id`,`sms_data`,`secure_transport_text`,`sender_address`,`mms_text`,`mms_content_uri`,`mms_mimetype`,`mms_filename`,`mms_filepath`,`_id`,`thread_id`,`address`,`person`,`date`,`date_sent`,`protocol`,`read`,`status`,`type`,`reply_path_present`,`subject`,`body`,`service_center`,`locked`,`sub_id`,`error_code`,`creator`,`seen`,`mms__id`,`mms_thread_id`,`mms_date`,`mms_date_sent`,`mms_msg_box`,`mms_read`,`mms_m_id`,`mms_sub`,`mms_sub_cs`,`mms_ct_t`,`mms_ct_l`,`mms_exp`,`mms_m_cls`,`mms_m_type`,`mms_v`,`mms_m_size`,`mms_pri`,`mms_rr`,`mms_rpt_a`,`mms_resp_st`,`mms_st`,`mms_tr_id`,`mms_retr_st`,`mms_retr_txt`,`mms_retr_txt_cs`,`mms_read_status`,`mms_ct_cls`,`mms_resp_txt`,`mms_d_tm`,`mms_d_rpt`,`mms_locked`,`mms_sub_id`,`mms_seen`,`mms_creator`,`mms_text_only`) SELECT `id`,`sms_data`,`secure_transport_text`,`sender_address`,`mms_text`,`mms_content_uri`,`mms_mimetype`,`mms_filename`,`mms_filepath`,`_id`,`thread_id`,`address`,`person`,`date`,`date_sent`,`protocol`,`read`,`status`,`type`,`reply_path_present`,`subject`,`body`,`service_center`,`locked`,`sub_id`,`error_code`,`creator`,`seen`,`mms__id`,`mms_thread_id`,`mms_date`,`mms_date_sent`,`mms_msg_box`,`mms_read`,`mms_m_id`,`mms_sub`,`mms_sub_cs`,`mms_ct_t`,`mms_ct_l`,`mms_exp`,`mms_m_cls`,`mms_m_type`,`mms_v`,`mms_m_size`,`mms_pri`,`mms_rr`,`mms_rpt_a`,`mms_resp_st`,`mms_st`,`mms_tr_id`,`mms_retr_st`,`mms_retr_txt`,`mms_retr_txt_cs`,`mms_read_status`,`mms_ct_cls`,`mms_resp_txt`,`mms_d_tm`,`mms_d_rpt`,`mms_locked`,`mms_sub_id`,`mms_seen`,`mms_creator`,`mms_text_only` FROM `Conversations`")
    connection.execSQL("DROP TABLE `Conversations`")
    connection.execSQL("ALTER TABLE `_new_Conversations` RENAME TO `Conversations`")
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Conversations__id` ON `Conversations` (`_id`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Conversations_thread_id_read` ON `Conversations` (`thread_id`, `read`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Conversations_mms_thread_id_read` ON `Conversations` (`mms_thread_id`, `read`)")
    connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_Threads` (`threadId` INTEGER NOT NULL, `address` TEXT NOT NULL, `snippet` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` INTEGER NOT NULL, `conversationId` INTEGER NOT NULL, `isMms` INTEGER NOT NULL, `isMute` INTEGER NOT NULL, `isArchive` INTEGER NOT NULL, `isBlocked` INTEGER NOT NULL, `unread` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`threadId`))")
    connection.execSQL("INSERT INTO `_new_Threads` (`threadId`,`address`,`snippet`,`date`,`type`,`conversationId`,`isMms`,`isMute`,`isArchive`,`isBlocked`,`unread`,`isPinned`) SELECT `threadId`,`address`,`snippet`,`date`,`type`,`conversationId`,`isMms`,`isMute`,`isArchive`,`isBlocked`,`unread`,`isPinned` FROM `Threads`")
    connection.execSQL("DROP TABLE `Threads`")
    connection.execSQL("ALTER TABLE `_new_Threads` RENAME TO `Threads`")
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Threads_address` ON `Threads` (`address`)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Threads_isArchive_isPinned_date_threadId` ON `Threads` (`isArchive`, `isPinned`, `date`, `threadId`)")
  }
}
