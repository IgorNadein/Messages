package com.afkanerd.deku.messages.service

/** Determines whether an SMS sender address can be used as a reply destination. */
object ConversationReplyPolicy {
    fun canReply(addresses: List<String>): Boolean =
        addresses.isNotEmpty() && addresses.all(::canReply)

    fun canReply(address: String): Boolean {
        val compact = address.trim().filterNot { character ->
            character.isWhitespace() || character in "-()."
        }
        val digits = compact.removePrefix("+")
        return digits.isNotEmpty() && digits.all(Char::isDigit)
    }
}
