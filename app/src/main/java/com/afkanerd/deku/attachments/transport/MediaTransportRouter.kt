package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.AttachmentProtection
import com.afkanerd.deku.messages.domain.MediaTransport

/** One place for the fail-closed rules that turn a sender preference into a wire route. */
object MediaTransportRouter {
    sealed interface Route {
        data object Mms : Route
        data class DataSms(val protection: AttachmentProtection) : Route
        data object CloudStorage : Route
        data object UnsupportedGroupDataSms : Route
    }

    fun resolve(
        selected: MediaTransport,
        recipientCount: Int,
        secureOneToOne: Boolean,
    ): Route {
        require(recipientCount > 0)
        return when(selected) {
            MediaTransport.MMS -> if(secureOneToOne) {
                // Until the encrypted-MMS container and receiver are both available, preserve
                // the existing protected packet path. Never downgrade a secure attachment.
                Route.DataSms(AttachmentProtection.SECURE)
            } else {
                Route.Mms
            }
            MediaTransport.DATA_SMS -> when {
                recipientCount != 1 -> Route.UnsupportedGroupDataSms
                secureOneToOne -> Route.DataSms(AttachmentProtection.SECURE)
                else -> Route.DataSms(AttachmentProtection.UNPROTECTED)
            }
            MediaTransport.CLOUD_STORAGE -> Route.CloudStorage
        }
    }
}
