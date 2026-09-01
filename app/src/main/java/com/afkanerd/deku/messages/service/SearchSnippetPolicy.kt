package com.afkanerd.deku.messages.service

import com.afkanerd.deku.security.SecureMessageCodec

/** A search result never exposes an undeciphered secure transport envelope. */
internal object SearchSnippetPolicy {
    fun safeSnippet(
        raw: String,
        protectedReplacement: String = "Protected message needs attention",
    ): String = when {
        isSensitive(raw) -> protectedReplacement
        else -> raw
    }

    fun isSensitive(raw: String): Boolean =
        SecureMessageCodec.decodeTextOrNull(raw) != null ||
            SecureMessageCodec.decodeKeyExchangeTextOrNull(raw) != null ||
            ConversationEntityMapper.looksLikeLegacyBinaryPayload(raw)
}
