package com.afkanerd.deku.attachments.storage

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "AttachmentOfferFragment",
    primaryKeys = ["transferId", "fragmentIndex"],
    indices = [Index("receivedAt")],
)
data class AttachmentOfferFragmentEntity(
    val transferId: String,
    val fragmentIndex: Int,
    val totalFragments: Int,
    val address: String,
    val subscriptionId: Int,
    val payload: ByteArray,
    val receivedAt: Long,
)
