package com.afkanerd.deku.messages.ui

import com.afkanerd.deku.messages.domain.ConversationSecurityState
import org.junit.Assert.assertEquals
import org.junit.Test

class SecuritySheetPolicyTest {
    @Test
    fun incomingRequestOffersExplicitAcceptAction() {
        assertEquals(
            SecuritySheetPrimaryAction.ACCEPT_REQUEST,
            securitySheetPrimaryAction(ConversationSecurityState.REQUEST_RECEIVED),
        )
    }

    @Test
    fun outgoingRequestCannotBeAcceptedLocally() {
        assertEquals(
            SecuritySheetPrimaryAction.NONE,
            securitySheetPrimaryAction(ConversationSecurityState.NEGOTIATING),
        )
    }

    @Test
    fun recoveryAndIdentityChangeKeepDistinctActions() {
        assertEquals(
            SecuritySheetPrimaryAction.REPAIR,
            securitySheetPrimaryAction(ConversationSecurityState.RECOVERY_REQUIRED),
        )
        assertEquals(
            SecuritySheetPrimaryAction.ACCEPT_CHANGED_IDENTITY,
            securitySheetPrimaryAction(ConversationSecurityState.KEY_CHANGED),
        )
    }
}
