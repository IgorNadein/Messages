package com.afkanerd.deku.attachments.protocol

enum class SmsPacketType(val wireValue: Int, val encrypted: Boolean) {
    TRANSFER_OFFER(1, false),
    TRANSFER_ACCEPT(2, true),
    TRANSFER_REJECT(3, true),
    TRANSFER_CHUNK(4, true),
    TRANSFER_COMPLETE(5, true),
    ACK(6, true),
    NACK(7, true),
    CANCEL(8, true),
    ERROR(9, true);

    companion object {
        fun fromWireValue(value: Int): SmsPacketType? = entries.firstOrNull { it.wireValue == value }
    }
}
