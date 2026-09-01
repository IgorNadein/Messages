package com.afkanerd.deku.attachments.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttachmentOfferFragmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(fragment: AttachmentOfferFragmentEntity): Long

    @Query("SELECT * FROM AttachmentOfferFragment WHERE transferId = :transferId ORDER BY fragmentIndex ASC")
    suspend fun get(transferId: String): List<AttachmentOfferFragmentEntity>

    @Query("SELECT COUNT(DISTINCT transferId) FROM AttachmentOfferFragment")
    suspend fun countContexts(): Int

    @Query("DELETE FROM AttachmentOfferFragment WHERE transferId = :transferId")
    suspend fun delete(transferId: String)

    @Query("DELETE FROM AttachmentOfferFragment WHERE receivedAt < :olderThan")
    suspend fun deleteExpired(olderThan: Long): Int
}
