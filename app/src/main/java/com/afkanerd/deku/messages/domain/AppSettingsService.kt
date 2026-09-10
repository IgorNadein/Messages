package com.afkanerd.deku.messages.domain

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class SecureMessageTransport {
    STANDARD_SMS,
    DATA_SMS,
}

/** User-selected transport for newly sent media. Receiving is transport-agnostic. */
enum class MediaTransport {
    MMS,
    DATA_SMS,
    STANDARD_SMS,
    CLOUD_STORAGE,
}

enum class BooleanSetting {
    STORE_IN_SYSTEM_DATABASE,
    DELETE_FROM_SYSTEM_DATABASE,
    DELIVERY_REPORTS,
    KEEP_ARCHIVED,
    CONTEXT_REPLIES,
    USE_24_HOUR_TIME,
}

data class LanguageOption(
    val tag: String,
    val displayName: String,
)

data class SimTransportSettings(
    val subscriptionId: Long,
    val displayName: String,
    val slotIndex: Int,
    val secureMessageTransport: SecureMessageTransport,
    val mediaTransport: MediaTransport,
)

data class AppSettingsSnapshot(
    val languageTag: String,
    val languageName: String,
    val languages: List<LanguageOption>,
    val themeMode: ThemeMode,
    val storeInSystemDatabase: Boolean,
    val deleteFromSystemDatabase: Boolean,
    val deliveryReports: Boolean,
    val keepArchived: Boolean,
    val contextReplies: Boolean,
    val use24HourTime: Boolean,
    val secureMessageTransport: SecureMessageTransport = SecureMessageTransport.STANDARD_SMS,
    val mediaTransport: MediaTransport = MediaTransport.MMS,
    val simTransportSettings: List<SimTransportSettings> = emptyList(),
    val cloudStorageConfigured: Boolean = false,
    val cloudStorageProvider: String? = null,
    val cloudStorageEndpoint: String = "",
    val cloudStorageFolder: String = "Messages",
)

interface AppSettingsService {
    fun snapshot(): AppSettingsSnapshot

    fun setTheme(mode: ThemeMode): AppSettingsSnapshot

    fun setLanguage(tag: String): AppSettingsSnapshot

    fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot

    fun setSecureMessageTransport(transport: SecureMessageTransport): AppSettingsSnapshot = snapshot()

    fun setSecureMessageTransport(
        subscriptionId: Long,
        transport: SecureMessageTransport,
    ): AppSettingsSnapshot = setSecureMessageTransport(transport)

    fun setMediaTransport(transport: MediaTransport): AppSettingsSnapshot = snapshot()

    fun setMediaTransport(
        subscriptionId: Long,
        transport: MediaTransport,
    ): AppSettingsSnapshot = setMediaTransport(transport)

    suspend fun configureCloudStorage(
        provider: String,
        endpoint: String,
        folder: String,
        accessToken: String,
    ): AppSettingsSnapshot = snapshot()

    suspend fun clearCloudStorage(): AppSettingsSnapshot = snapshot()
}
