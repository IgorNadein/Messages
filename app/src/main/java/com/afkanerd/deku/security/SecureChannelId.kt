package com.afkanerd.deku.security

/**
 * Local storage identity of one secure SMS route. The real remote address is
 * still used as the SMS destination; this value never appears on the wire.
 */
object SecureChannelId {
    fun storageAddress(remoteAddress: String, subscriptionId: Long): String =
        "$remoteAddress#deku-subscription:$subscriptionId"
}
