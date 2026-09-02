package com.afkanerd.deku.attachments.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentTransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: AttachmentTransferEntity)

    @Update
    suspend fun update(transfer: AttachmentTransferEntity)

    @Query("SELECT * FROM AttachmentTransfer WHERE transferId = :id LIMIT 1")
    suspend fun get(id: String): AttachmentTransferEntity?

    @Query("SELECT * FROM AttachmentTransfer WHERE transferId = :id LIMIT 1")
    fun observe(id: String): Flow<AttachmentTransferEntity?>

    @Query("SELECT * FROM AttachmentTransfer WHERE address = :address ORDER BY createdAt DESC")
    fun observeForAddress(address: String): Flow<List<AttachmentTransferEntity>>

    @Query("SELECT * FROM AttachmentTransfer WHERE address IN (:addresses) ORDER BY createdAt DESC")
    fun observeForAddresses(addresses: List<String>): Flow<List<AttachmentTransferEntity>>

    @Query("SELECT * FROM AttachmentTransfer WHERE status IN (:statuses) ORDER BY updatedAt ASC")
    suspend fun getByStatuses(statuses: List<String>): List<AttachmentTransferEntity>

    @Query("SELECT COUNT(*) FROM AttachmentTransfer WHERE address = :address AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun countActiveForAddress(address: String): Int

    @Query("SELECT COUNT(*) FROM AttachmentTransfer WHERE outgoing = 0 AND status = 'OFFERED'")
    suspend fun countPendingOffers(): Int

    @Query("DELETE FROM AttachmentTransfer WHERE status IN ('FAILED', 'CANCELLED') AND updatedAt < :olderThan")
    suspend fun deleteExpiredTerminal(olderThan: Long): Int

    @Query("SELECT * FROM AttachmentTransfer WHERE expiresAt < :now")
    suspend fun getExpired(now: Long): List<AttachmentTransferEntity>

    @Query("UPDATE AttachmentTransfer SET smsDelivered = smsDelivered + 1, updatedAt = :now WHERE transferId = :id")
    suspend fun incrementDelivered(id: String, now: Long)
}
