package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityVerificationStatus

internal object ConversationSecurityStateMapper {
    fun map(
        session: SecureSessionStatus,
        identity: IdentityVerificationStatus,
        mode: EncryptionController.SecureRequestMode,
    ): ConversationSecurityState {
        if(identity == IdentityVerificationStatus.KEY_CHANGED) {
            return ConversationSecurityState.KEY_CHANGED
        }
        return when(session) {
            SecureSessionStatus.PLAIN -> ConversationSecurityState.PLAIN
            SecureSessionStatus.SECURE_PENDING -> {
                if(mode == EncryptionController.SecureRequestMode.REQUEST_RECEIVED) {
                    ConversationSecurityState.REQUEST_RECEIVED
                } else {
                    ConversationSecurityState.NEGOTIATING
                }
            }
            SecureSessionStatus.SECURE_BROKEN -> ConversationSecurityState.RECOVERY_REQUIRED
            SecureSessionStatus.SECURE_ESTABLISHED -> {
                if(identity == IdentityVerificationStatus.VERIFIED) {
                    ConversationSecurityState.SECURE_VERIFIED
                } else {
                    ConversationSecurityState.SECURE_UNVERIFIED
                }
            }
        }
    }
}
