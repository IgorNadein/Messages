package com.afkanerd.deku.messages.domain

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class BooleanSetting {
    STORE_IN_SYSTEM_DATABASE,
    DELETE_FROM_SYSTEM_DATABASE,
    DELIVERY_REPORTS,
    SWIPE_ACTIONS,
    KEEP_ARCHIVED,
    CONTEXT_REPLIES,
    USE_24_HOUR_TIME,
}

data class LanguageOption(
    val tag: String,
    val displayName: String,
)

data class AppSettingsSnapshot(
    val languageTag: String,
    val languageName: String,
    val languages: List<LanguageOption>,
    val themeMode: ThemeMode,
    val storeInSystemDatabase: Boolean,
    val deleteFromSystemDatabase: Boolean,
    val deliveryReports: Boolean,
    val swipeActions: Boolean,
    val keepArchived: Boolean,
    val contextReplies: Boolean,
    val use24HourTime: Boolean,
)

interface AppSettingsService {
    fun snapshot(): AppSettingsSnapshot

    fun setTheme(mode: ThemeMode): AppSettingsSnapshot

    fun setLanguage(tag: String): AppSettingsSnapshot

    fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot
}
