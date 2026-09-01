package com.afkanerd.deku.messages.domain

data class RespondViaMessageRequest(
    val address: String,
    val text: String,
) {
    companion object {
        fun create(dataUri: String?, text: String?): RespondViaMessageRequest? {
            val safeText = text?.takeIf(String::isNotBlank) ?: return null
            val route = ExternalMessageRouteMapper.fromSendTo(
                dataUri = dataUri,
                smsBody = safeText,
                sharedText = null,
            ) ?: return null
            return RespondViaMessageRequest(
                address = route.address,
                text = safeText,
            )
        }
    }
}
