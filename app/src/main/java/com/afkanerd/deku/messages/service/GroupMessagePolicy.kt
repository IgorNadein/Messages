package com.afkanerd.deku.messages.service

internal enum class ReplyTransport {
    SMS,
    GROUP_MMS,
}

internal object GroupMessagePolicy {
    fun recipients(storedAddress: String): List<String> = storedAddress
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun notificationReplyTransport(storedAddress: String): ReplyTransport =
        if(recipients(storedAddress).size > 1) ReplyTransport.GROUP_MMS else ReplyTransport.SMS
}
