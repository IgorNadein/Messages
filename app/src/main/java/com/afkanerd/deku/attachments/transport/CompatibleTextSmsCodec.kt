package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsFrameCodec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** A recognizable, strictly validated text envelope for attachment protocol frames. */
@OptIn(ExperimentalEncodingApi::class)
object CompatibleTextSmsCodec {
    const val PREFIX = "//DKA1:"
    private const val MAX_TEXT_LENGTH = 512

    fun encode(frame: SmsFrame): String = PREFIX + Base64.Default.encode(SmsFrameCodec.encode(frame))

    fun decodeOrNull(text: String): SmsFrame? {
        val compact = text.filterNot(Char::isWhitespace)
        if(!compact.startsWith(PREFIX) || compact.length !in (PREFIX.length + 1)..MAX_TEXT_LENGTH) {
            return null
        }
        val bytes = runCatching { Base64.Default.decode(compact.removePrefix(PREFIX)) }
            .getOrNull() ?: return null
        return (SmsFrameCodec.decode(bytes) as? SmsFrameCodec.DecodeResult.Success)?.frame
    }
}
