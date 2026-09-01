package com.afkanerd.deku.messages.domain

/**
 * High-level boundary for debug-only message tools.
 *
 * The presentation layer deliberately has no access to the message database,
 * Telephony APIs, or notification implementation.
 */
interface DeveloperToolsService {
    suspend fun triggerSampleMmsNotification(): Boolean

    suspend fun clearLocalMessageHistory(): Boolean

    suspend fun exportNativeMessageDatabase(destinationUri: String): Boolean

    suspend fun importNativeMessageDatabase(sourceUri: String): NativeMessageImportSummary

    suspend fun clearNativeMessageDatabase(): Boolean
}

data class NativeMessageImportSummary(
    val mmsCount: Int,
    val mmsPartCount: Int,
    val mmsAddressCount: Int,
)
