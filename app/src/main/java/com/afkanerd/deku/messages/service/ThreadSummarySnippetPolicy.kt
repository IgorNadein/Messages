package com.afkanerd.deku.messages.service

import com.afkanerd.deku.security.SecureMessageCodec

/** Keeps transport/control bytes out of inbox previews without hiding decrypted Data-SMS text. */
internal object ThreadSummarySnippetPolicy {
    fun display(
        snippet: String,
        smsData: ByteArray?,
        secureTransportText: String?,
        decryptionFailureText: String,
        recoveryText: String,
        securityUpdateText: String,
        decryptionFailedText: String,
    ): String = when {
        smsData != null && SecureMessageCodec.decodeKeyExchangeOrNull(smsData) != null ->
            securityUpdateText
        secureTransportText != null && snippet == decryptionFailureText ->
            decryptionFailedText
        else -> SearchSnippetPolicy.safeSnippet(snippet, recoveryText)
    }
}
