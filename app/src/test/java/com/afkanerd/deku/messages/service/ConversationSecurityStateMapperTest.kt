package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSecurityStateMapperTest {
    @Test
    fun incomingRequestRemainsActionableInsteadOfGenericNegotiatingState() {
        assertEquals(
            ConversationSecurityState.REQUEST_RECEIVED,
            ConversationSecurityStateMapper.map(
                session = SecureSessionStatus.SECURE_PENDING,
                identity = IdentityVerificationStatus.UNVERIFIED,
                mode = EncryptionController.SecureRequestMode.REQUEST_RECEIVED,
            ),
        )
    }

    @Test
    fun outgoingRequestRemainsWaitingOnly() {
        assertEquals(
            ConversationSecurityState.NEGOTIATING,
            ConversationSecurityStateMapper.map(
                session = SecureSessionStatus.SECURE_PENDING,
                identity = IdentityVerificationStatus.UNVERIFIED,
                mode = EncryptionController.SecureRequestMode.REQUEST_REQUESTED,
            ),
        )
    }

    @Test
    fun changedIdentityStillWinsOverSessionMode() {
        assertEquals(
            ConversationSecurityState.KEY_CHANGED,
            ConversationSecurityStateMapper.map(
                session = SecureSessionStatus.SECURE_PENDING,
                identity = IdentityVerificationStatus.KEY_CHANGED,
                mode = EncryptionController.SecureRequestMode.REQUEST_RECEIVED,
            ),
        )
    }

    @Test
    fun establishedSessionPreservesVerificationState() {
        assertEquals(
            ConversationSecurityState.SECURE_VERIFIED,
            ConversationSecurityStateMapper.map(
                session = SecureSessionStatus.SECURE_ESTABLISHED,
                identity = IdentityVerificationStatus.VERIFIED,
                mode = EncryptionController.SecureRequestMode.REQUEST_ACCEPTED,
            ),
        )
        assertEquals(
            ConversationSecurityState.SECURE_UNVERIFIED,
            ConversationSecurityStateMapper.map(
                session = SecureSessionStatus.SECURE_ESTABLISHED,
                identity = IdentityVerificationStatus.UNVERIFIED,
                mode = EncryptionController.SecureRequestMode.REQUEST_ACCEPTED,
            ),
        )
    }
}
