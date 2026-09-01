package com.afkanerd.deku.security

import android.content.Context
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityKeyManager
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityVerificationStatus
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.getEncryptionModeStatesSync
import org.json.JSONObject

enum class SecureSessionStatus {
    PLAIN,
    SECURE_PENDING,
    SECURE_ESTABLISHED,
    SECURE_BROKEN,
}

object SecureSessionStatusResolver {
    suspend fun resolve(context: Context, address: String): SecureSessionStatus {
        val identity = try {
            IdentityKeyManager.getContactIdentity(context, address)
        } catch (_: Exception) {
            return SecureSessionStatus.SECURE_BROKEN
        }
        if(identity.status == IdentityVerificationStatus.KEY_CHANGED) {
            return SecureSessionStatus.SECURE_BROKEN
        }
        if(identity.status == IdentityVerificationStatus.REKEY_REQUIRED) {
            return SecureSessionStatus.SECURE_PENDING
        }
        val encodedMode = context.getEncryptionModeStatesSync(address)
        val hasRatchetState = try {
            context.getEncryptedBinaryData(address + RATCHET_SUFFIX) != null
        } catch (_: Exception) {
            return SecureSessionStatus.SECURE_BROKEN
        }

        if(encodedMode == null) {
            return if(hasRatchetState) SecureSessionStatus.SECURE_BROKEN
            else SecureSessionStatus.PLAIN
        }

        val mode = try {
            JSONObject(encodedMode).getString(MODE_FIELD)
        } catch (_: Exception) {
            return SecureSessionStatus.SECURE_BROKEN
        }

        return when(mode) {
            EncryptionController.SecureRequestMode.REQUEST_NONE.name ->
                if(hasRatchetState) SecureSessionStatus.SECURE_BROKEN
                else SecureSessionStatus.PLAIN
            EncryptionController.SecureRequestMode.REQUEST_REQUESTED.name,
            EncryptionController.SecureRequestMode.REQUEST_RECEIVED.name ->
                SecureSessionStatus.SECURE_PENDING
            EncryptionController.SecureRequestMode.REQUEST_ACCEPTED.name ->
                SecureSessionStatus.SECURE_ESTABLISHED
            EncryptionController.SecureRequestMode.REQUEST_BROKEN.name ->
                SecureSessionStatus.SECURE_BROKEN
            else -> SecureSessionStatus.SECURE_BROKEN
        }
    }

    private const val MODE_FIELD = "mode"
    private const val RATCHET_SUFFIX = "_ratchet_state"
}
