package com.afkanerd.deku.messages.domain

data class NotificationReplyRequest(
    val address: String,
    val threadId: Int,
    val subscriptionId: Long,
    val text: String,
) {
    companion object {
        fun create(
            address: String?,
            threadId: Int,
            subscriptionId: Long,
            text: String?,
        ): NotificationReplyRequest? {
            val safeAddress = address?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val safeText = text?.takeIf(String::isNotEmpty) ?: return null
            if(threadId < 0 || subscriptionId < 0) return null
            return NotificationReplyRequest(
                address = safeAddress,
                threadId = threadId,
                subscriptionId = subscriptionId,
                text = safeText,
            )
        }
    }
}
