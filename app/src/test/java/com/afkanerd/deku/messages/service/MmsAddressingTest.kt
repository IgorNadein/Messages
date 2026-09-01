package com.afkanerd.deku.messages.service

import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressEntry
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressRole
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsAddressing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MmsAddressingTest {
    @Test
    fun incomingGroupKeepsSenderSeparateFromParticipants() {
        val result = MmsAddressing.resolve(
            entries = listOf(
                entry("+15550000001", MmsAddressRole.FROM),
                entry("+15550000002", MmsAddressRole.TO),
                entry("+15550000003", MmsAddressRole.TO),
            ),
            providerParticipants = listOf("+15550000001", "+15550000003"),
            ownNumbers = listOf("+15550000002"),
            outgoing = false,
        )

        assertEquals("+15550000001", result.senderAddress)
        assertEquals(listOf("+15550000001", "+15550000003"), result.participantAddresses)
    }

    @Test
    fun ccRecipientsAreIncludedWhenProviderThreadIsUnavailable() {
        val result = MmsAddressing.resolve(
            entries = listOf(
                entry("+15550000001", MmsAddressRole.FROM),
                entry("+15550000002", MmsAddressRole.TO),
                entry("+15550000003", MmsAddressRole.CC),
            ),
            providerParticipants = emptyList(),
            ownNumbers = listOf("+15550000002"),
            outgoing = false,
        )

        assertEquals(listOf("+15550000001", "+15550000003"), result.participantAddresses)
    }

    @Test
    fun allKnownDualSimNumbersAreExcluded() {
        val result = MmsAddressing.resolve(
            entries = listOf(
                entry("+15550000001", MmsAddressRole.FROM),
                entry("+15550000002", MmsAddressRole.TO),
                entry("+15550000004", MmsAddressRole.CC),
                entry("+15550000003", MmsAddressRole.TO),
            ),
            providerParticipants = emptyList(),
            ownNumbers = listOf("+15550000002", "+15550000004"),
            outgoing = false,
        )

        assertEquals(listOf("+15550000001", "+15550000003"), result.participantAddresses)
    }

    @Test
    fun providerParticipantsOverrideIncompletePduRecipients() {
        val result = MmsAddressing.resolve(
            entries = listOf(entry("+15550000001", MmsAddressRole.FROM)),
            providerParticipants = listOf("+15550000001", "+15550000003"),
            ownNumbers = listOf("+15550000002"),
            outgoing = false,
        )

        assertEquals(listOf("+15550000001", "+15550000003"), result.participantAddresses)
    }

    @Test
    fun outgoingMmsHasRecipientsButNoRemoteSender() {
        val result = MmsAddressing.resolve(
            entries = listOf(
                entry("insert-address-token", MmsAddressRole.FROM),
                entry("+15550000001", MmsAddressRole.TO),
                entry("+15550000003", MmsAddressRole.TO),
            ),
            providerParticipants = emptyList(),
            ownNumbers = listOf("+15550000002"),
            outgoing = true,
        )

        assertNull(result.senderAddress)
        assertEquals(listOf("+15550000001", "+15550000003"), result.participantAddresses)
    }

    private fun entry(address: String, role: MmsAddressRole) = MmsAddressEntry(address, role)
}
