package com.afkanerd.deku.messages.domain

data class RoutingHistoryItem(
    val workId: String,
    val messageId: Long,
    val gatewayId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val timestampMillis: Long,
    val state: RoutingHistoryState,
)

enum class RoutingHistoryState {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED,
    UNKNOWN,
    ;

    companion object {
        fun fromWorkerName(value: String): RoutingHistoryState =
            entries.firstOrNull { it.name == value.uppercase() } ?: UNKNOWN
    }
}
