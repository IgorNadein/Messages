package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.AttachmentProtection
import com.afkanerd.deku.messages.domain.MediaTransport

/** One place for the fail-closed rules that turn a sender preference into a wire route. */
object MediaTransportRouter {
    sealed interface Route {
        data object Mms : Route
        data object SecureMms : Route
        data class DataSms(val protection: AttachmentProtection) : Route
        data class StandardSms(val protection: AttachmentProtection) : Route
        data class CloudStorage(val protection: AttachmentProtection) : Route
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
                Route.SecureMms
            } else {
                Route.Mms
            }
            MediaTransport.DATA_SMS -> when {
                recipientCount != 1 -> Route.UnsupportedGroupDataSms
                secureOneToOne -> Route.DataSms(AttachmentProtection.SECURE)
                else -> Route.DataSms(AttachmentProtection.UNPROTECTED)
            }
            MediaTransport.STANDARD_SMS -> when {
                recipientCount != 1 -> Route.UnsupportedGroupDataSms
                secureOneToOne -> Route.StandardSms(AttachmentProtection.SECURE)
                else -> Route.StandardSms(AttachmentProtection.UNPROTECTED)
            }
            MediaTransport.CLOUD_STORAGE -> if(recipientCount != 1) {
                Route.UnsupportedGroupDataSms
            } else {
                Route.CloudStorage(
                    if(secureOneToOne) AttachmentProtection.SECURE
                    else AttachmentProtection.UNPROTECTED
                )
            }
        }
    }
}
