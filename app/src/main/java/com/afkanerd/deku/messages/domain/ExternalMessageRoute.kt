package com.afkanerd.deku.messages.domain

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface ExternalMessageRoute {
    data class RecipientPicker(val text: String?) : ExternalMessageRoute

    data class Conversation(
        val address: String,
        val text: String?,
    ) : ExternalMessageRoute
}

/**
 * Converts Android entry-point data into app routes without depending on an Activity.
 * Keeping this parsing at the domain boundary makes cold-start and onNewIntent behavior
 * identical and lets malformed external input fail closed.
 */
object ExternalMessageRouteMapper {
    fun fromSharedText(text: String?): ExternalMessageRoute.RecipientPicker =
        ExternalMessageRoute.RecipientPicker(text?.takeIf(String::isNotBlank))

    fun fromSendTo(
        dataUri: String?,
        smsBody: String?,
        sharedText: String?,
    ): ExternalMessageRoute.Conversation? {
        val uri = runCatching { URI(dataUri ?: return null) }.getOrNull() ?: return null
        if(uri.scheme?.lowercase() !in SUPPORTED_MESSAGE_SCHEMES) return null

        val rawSchemeSpecificPart = uri.rawSchemeSpecificPart
            ?.removePrefix("//")
            ?: return null
        val rawAddress = rawSchemeSpecificPart
            .substringBefore('?')
            .substringBefore('#')
        val address = decodeComponent(rawAddress)?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        val uriBody = rawSchemeSpecificPart
            .substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
            .split('&')
            .asSequence()
            .mapNotNull { parameter ->
                val rawKey = parameter.substringBefore('=', missingDelimiterValue = parameter)
                val rawValue = parameter.substringAfter('=', missingDelimiterValue = "")
                decodeComponent(rawKey)?.let { key -> key to decodeComponent(rawValue) }
            }
            .firstOrNull { (key, _) -> key.equals("body", ignoreCase = true) }
            ?.second
            ?.takeIf(String::isNotBlank)

        return ExternalMessageRoute.Conversation(
            address = address,
            text = smsBody?.takeIf(String::isNotBlank)
                ?: sharedText?.takeIf(String::isNotBlank)
                ?: uriBody,
        )
    }

    fun fromNotification(address: String?): ExternalMessageRoute.Conversation? =
        address
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { ExternalMessageRoute.Conversation(it, null) }

    private fun decodeComponent(value: String): String? = runCatching {
        // A phone number's '+' is data, not application/x-www-form-urlencoded whitespace.
        URLDecoder.decode(
            value.replace("+", "%2B"),
            StandardCharsets.UTF_8.name(),
        )
    }.getOrNull()

    private val SUPPORTED_MESSAGE_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
}
