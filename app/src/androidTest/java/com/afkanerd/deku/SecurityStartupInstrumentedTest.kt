package com.afkanerd.deku

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afkanerd.deku.security.SecureOutboundSmsPolicy
import com.afkanerd.deku.security.SecureInboundSmsPolicy
import com.afkanerd.deku.security.SecureMessageCodec
import com.afkanerd.smswithoutborders_libsmsmms.data.DatabaseImpl
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityKeyManager
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.IdentityVerificationStatus
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SavedEncryptedModes
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.getEncryptionModeStatesSync
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.removeEncryptionModeStates
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.removeSessionKeypairValues
import com.afkanerd.smswithoutborders_libsmsmms.security.OutboundSmsPolicyRegistry
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSms
import com.afkanerd.smswithoutborders_libsmsmms.security.InboundSmsPolicyRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityStartupInstrumentedTest {
    @Test
    fun renewalRotatesSessionKeyButPreservesVerifiedIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val address = "instrumentation-renewal-contact"
        val remoteIdentity = ByteArray(32) { index -> (index + 11).toByte() }

        IdentityKeyManager.recordRemoteIdentity(context, address, remoteIdentity)
        assertTrue(IdentityKeyManager.verifyContact(context, address, remoteIdentity))
        val permanentIdentityBefore = IdentityKeyManager.localPublicKey(context)

        val firstRequest = EncryptionController.renewSession(context, address)
        val firstSessionKey = SecureMessageCodec.decodeKeyExchangeOrNull(firstRequest)!!.publicKey
        val secondRequest = EncryptionController.renewSession(context, address)
        val secondSessionKey = SecureMessageCodec.decodeKeyExchangeOrNull(secondRequest)!!.publicKey

        assertFalse(firstSessionKey.contentEquals(secondSessionKey))
        assertArrayEquals(permanentIdentityBefore, IdentityKeyManager.localPublicKey(context))
        assertEquals(
            IdentityVerificationStatus.VERIFIED,
            IdentityKeyManager.getContactIdentity(context, address).status,
        )
        val mode = SavedEncryptedModes.deserialize(
            context.getEncryptionModeStatesSync(address)
        )
        assertEquals(EncryptionController.SecureRequestMode.REQUEST_REQUESTED, mode.mode)
        assertEquals(EncryptionController.SessionRole.INITIATOR, mode.role)

        context.removeEncryptionModeStates(address)
        context.removeSessionKeypairValues(address)
    }

    @Test
    fun applicationInstallsFailClosedOutboundPolicyBeforeComponentsRun() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(context is MessagesApplication)
        assertTrue(InboundSmsPolicyRegistry.policy is SecureInboundSmsPolicy)
        assertTrue(OutboundSmsPolicyRegistry.policy is SecureOutboundSmsPolicy)
    }

    @Test
    fun inboundPlaintextPassesThroughWithoutBeingClassifiedAsCiphertext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val processed = InboundSmsPolicyRegistry.evaluate(
            context,
            InboundSms("instrumentation-plain-contact", "ordinary SMS"),
        )

        assertEquals("ordinary SMS", processed.displayText)
        assertEquals(null, processed.secureTransportText)
    }

    @Test
    fun sqlCipherPasswordRemainsValidForPooledConnections() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = DatabaseImpl.getDatabaseImpl(context)

            // Room/SQLCipher opens non-primary read connections after the initial
            // writable connection. Clearing the factory passphrase early caused these
            // reads to fail with SQLiteNotADatabaseException on a real device.
            withContext(Dispatchers.IO) {
                coroutineScope {
                    List(16) { threadId ->
                        async {
                            database.threadsDao()!!.getUnreadCount(threadId).first()
                        }
                    }.awaitAll()
                }
            }
        }
    }
}
