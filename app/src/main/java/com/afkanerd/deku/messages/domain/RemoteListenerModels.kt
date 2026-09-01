package com.afkanerd.deku.messages.domain

enum class RemoteListenerProtocol {
    AMQP,
    AMQPS,
}

data class RemoteListenerSummary(
    val id: Long,
    val displayName: String,
    val host: String,
    val username: String,
    val port: Int,
    val virtualHost: String,
    val protocol: RemoteListenerProtocol,
    val activated: Boolean,
    val connected: Boolean,
)

data class RemoteListenerDraft(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val virtualHost: String = "/",
    val port: String = DEFAULT_AMQP_PORT.toString(),
    val protocol: RemoteListenerProtocol = RemoteListenerProtocol.AMQP,
) {
    fun isValid(): Boolean = host.isNotBlank() &&
        username.isNotBlank() &&
        password.isNotBlank() &&
        virtualHost.isNotBlank() &&
        port.toIntOrNull() in 1..65535

    companion object {
        const val DEFAULT_AMQP_PORT = 5672
    }
}

data class RemoteQueueSummary(
    val id: Long,
    val listenerId: Long,
    val exchange: String,
    val sim1Binding: String,
    val sim2Binding: String,
)

data class RemoteQueueDraft(
    val exchange: String = "",
    val sim1Binding: String = "",
    val sim2Binding: String = "",
) {
    fun isValid(): Boolean = exchange.isNotBlank() && sim1Binding.isNotBlank()
}

enum class RemoteListenerToggleResult {
    ACTIVATED,
    DEACTIVATED,
    MISSING_QUEUES,
    PERMISSION_REQUIRED,
    FAILED,
}
