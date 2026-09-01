package com.afkanerd.smswithoutborders_libsmsmms.data.data.models

/** Address roles stored in the Telephony MMS `addr` table. */
enum class MmsAddressRole {
    FROM,
    TO,
    CC,
    BCC,
    UNKNOWN,
}

data class MmsAddressEntry(
    val address: String,
    val role: MmsAddressRole,
)

data class MmsConversationAddressing(
    val senderAddress: String?,
    val participantAddresses: List<String>,
)

/**
 * Separates the author of one MMS from the stable recipient set that identifies its thread.
 * Provider participants win when available because Android has already applied carrier/OEM
 * threading rules. The PDU FROM/TO/CC fields are the fallback for providers that do not expose a
 * newly-created thread until its first message has been persisted.
 */
object MmsAddressing {
    fun resolve(
        entries: List<MmsAddressEntry>,
        providerParticipants: List<String>,
        ownNumbers: Collection<String>,
        outgoing: Boolean,
        sameAddress: (String, String) -> Boolean = ::defaultSameAddress,
    ): MmsConversationAddressing {
        val validEntries = entries.filterNot { isPlaceholder(it.address) }
        val sender = validEntries
            .firstOrNull { it.role == MmsAddressRole.FROM }
            ?.address
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        val providerSet = sanitize(
            addresses = providerParticipants,
            ownNumbers = ownNumbers,
            sameAddress = sameAddress,
        )
        val pduCandidates = if(outgoing) {
            validEntries.filter { it.role in OUTGOING_RECIPIENT_ROLES }.map(MmsAddressEntry::address)
        } else {
            validEntries.filter { it.role in INCOMING_PARTICIPANT_ROLES }.map(MmsAddressEntry::address)
        }
        val pduSet = sanitize(
            addresses = pduCandidates,
            ownNumbers = ownNumbers,
            sameAddress = sameAddress,
        )
        val participants = when {
            providerSet.isNotEmpty() -> providerSet
            pduSet.isNotEmpty() -> pduSet
            !outgoing && sender != null -> listOf(sender)
            else -> emptyList()
        }

        return MmsConversationAddressing(
            senderAddress = sender.takeUnless { outgoing },
            participantAddresses = participants,
        )
    }

    private fun sanitize(
        addresses: Collection<String>,
        ownNumbers: Collection<String>,
        sameAddress: (String, String) -> Boolean,
    ): List<String> {
        val result = ArrayList<String>()
        addresses.forEach { candidate ->
            val address = candidate.trim()
            if(address.isEmpty() || isPlaceholder(address)) return@forEach
            if(ownNumbers.any { own -> sameAddress(address, own) }) return@forEach
            if(result.none { existing -> sameAddress(existing, address) }) result += address
        }
        return result
    }

    private fun isPlaceholder(address: String): Boolean =
        address.contains("insert-address-token", ignoreCase = true)

    private fun defaultSameAddress(first: String, second: String): Boolean {
        val normalizedFirst = first.filter(Char::isLetterOrDigit).lowercase()
        val normalizedSecond = second.filter(Char::isLetterOrDigit).lowercase()
        if(normalizedFirst == normalizedSecond) return true
        val firstDigits = first.filter(Char::isDigit)
        val secondDigits = second.filter(Char::isDigit)
        return firstDigits.length >= MIN_COMPARABLE_DIGITS &&
            secondDigits.length >= MIN_COMPARABLE_DIGITS &&
            (firstDigits.endsWith(secondDigits) || secondDigits.endsWith(firstDigits))
    }

    private val INCOMING_PARTICIPANT_ROLES = setOf(
        MmsAddressRole.FROM,
        MmsAddressRole.TO,
        MmsAddressRole.CC,
    )
    private val OUTGOING_RECIPIENT_ROLES = setOf(
        MmsAddressRole.TO,
        MmsAddressRole.CC,
        MmsAddressRole.BCC,
    )
    private const val MIN_COMPARABLE_DIGITS = 7
}
