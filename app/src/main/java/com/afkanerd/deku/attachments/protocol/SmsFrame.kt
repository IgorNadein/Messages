package com.afkanerd.deku.attachments.protocol

data class SmsFrame(
    val protocolVersion: Int = TransferLimits.PROTOCOL_VERSION,
    val packetType: SmsPacketType,
    val flags: Int = 0,
    val transferId: TransferId,
    val chunkIndex: Int,
    val totalChunks: Int,
    val payload: ByteArray,
) {
    init {
        require(protocolVersion in 0..0xff)
        require(flags in 0..0xff)
        require(chunkIndex in 0..0xffff)
        require(totalChunks in 0..0xffff)
        require(payload.size <= TransferLimits.FRAME_PAYLOAD_BYTES)
    }

    override fun equals(other: Any?): Boolean = other is SmsFrame &&
        protocolVersion == other.protocolVersion && packetType == other.packetType &&
        flags == other.flags && transferId == other.transferId &&
        chunkIndex == other.chunkIndex && totalChunks == other.totalChunks &&
        payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + packetType.hashCode()
        result = 31 * result + flags
        result = 31 * result + transferId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        return 31 * result + payload.contentHashCode()
    }
}
