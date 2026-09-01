package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.net.toUri
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressEntry
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressRole
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressing
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsConversationAddressing

private val CONVERSATIONS_URI = "content://mms-sms/conversations?simple=true".toUri()
private val CANONICAL_ADDRESSES_URI = "content://mms-sms/canonical-addresses".toUri()

/** Resolves the address set Android assigned to an existing SMS/MMS thread. */
fun Context.getThreadParticipantAddresses(threadId: Int): List<String> {
    val recipientIds = runCatching {
        contentResolver.query(
            CONVERSATIONS_URI,
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
            "${Telephony.Threads._ID}=?",
            arrayOf(threadId.toString()),
            null,
        )?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(1).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
        .split(' ')
        .filter(String::isNotBlank)
        .distinct()
    if(recipientIds.isEmpty()) return emptyList()

    val placeholders = recipientIds.joinToString(",") { "?" }
    val addressesById = LinkedHashMap<String, String>()
    runCatching {
        contentResolver.query(
            CANONICAL_ADDRESSES_URI,
            arrayOf("_id", "address"),
            "_id IN ($placeholders)",
            recipientIds.toTypedArray(),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("_id")
            val addressIndex = cursor.getColumnIndexOrThrow("address")
            while(cursor.moveToNext()) {
                val address = cursor.getString(addressIndex)?.trim().orEmpty()
                if(address.isNotEmpty()) addressesById[cursor.getString(idIndex)] = address
            }
        }
    }
    return recipientIds.mapNotNull(addressesById::get)
}

fun Context.resolveMmsAddressing(
    messageId: Long,
    threadId: Int,
    subscriptionId: Int?,
    outgoing: Boolean,
): MmsConversationAddressing {
    val entries = getMmsAddressEntries(messageId)
    return MmsAddressing.resolve(
        entries = entries,
        providerParticipants = getThreadParticipantAddresses(threadId),
        ownNumbers = getOwnPhoneNumbers(subscriptionId),
        outgoing = outgoing,
        sameAddress = PhoneNumberUtils::compare,
    )
}

private fun Context.getMmsAddressEntries(messageId: Long): List<MmsAddressEntry> {
    val uri = Uri.parse("content://mms/$messageId/addr")
    val result = ArrayList<MmsAddressEntry>()
    runCatching {
        contentResolver.query(
            uri,
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
            val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
            while(cursor.moveToNext()) {
                val address = cursor.getString(addressIndex)?.trim().orEmpty()
                if(address.isNotEmpty()) {
                    result += MmsAddressEntry(address, cursor.getInt(typeIndex).toMmsAddressRole())
                }
            }
        }
    }
    return result
}

@SuppressLint("MissingPermission", "HardwareIds")
private fun Context.getOwnPhoneNumbers(preferredSubscriptionId: Int?): Set<String> {
    val result = LinkedHashSet<String>()
    val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        as SubscriptionManager
    val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val subscriptionIds = buildList {
        preferredSubscriptionId
            ?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it >= 0 }
            ?.let(::add)
        runCatching { subscriptionManager.activeSubscriptionInfoList.orEmpty() }
            .getOrDefault(emptyList())
            .forEach { info ->
                if(info.subscriptionId !in this) add(info.subscriptionId)
                info.number?.trim()?.takeIf(String::isNotEmpty)?.let(result::add)
            }
    }
    subscriptionIds.forEach { subId ->
        runCatching {
            telephonyManager.createForSubscriptionId(subId).line1Number
        }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)?.let(result::add)
    }
    return result
}

private fun Int.toMmsAddressRole(): MmsAddressRole = when(this) {
    137 -> MmsAddressRole.FROM
    151 -> MmsAddressRole.TO
    130 -> MmsAddressRole.CC
    129 -> MmsAddressRole.BCC
    else -> MmsAddressRole.UNKNOWN
}
