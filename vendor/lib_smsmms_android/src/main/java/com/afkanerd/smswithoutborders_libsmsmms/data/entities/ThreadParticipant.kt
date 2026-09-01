package com.afkanerd.smswithoutborders_libsmsmms.data.entities

import androidx.room.Entity
import androidx.room.Index

/** Stable normalized membership for a Telephony SMS/MMS thread. */
@Entity(
    tableName = "ThreadParticipants",
    primaryKeys = ["threadId", "address"],
    indices = [Index("address")],
)
data class ThreadParticipant(
    val threadId: Int,
    val address: String,
)
