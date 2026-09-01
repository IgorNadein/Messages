package com.afkanerd.deku.messages.service

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getCurrentLocale
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.setLocale
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetEnable24HourFormat
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetEnableContextReplies
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetEnableSwipeBehaviour
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetGetDeliveryReports
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetKeepMessagesArchived
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetStoreTelephonyDb
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetTheme
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetCanSwipeBehaviour
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetEnable24HourFormat
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetEnableContextReplies
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetGetDeliveryReports
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetKeepMessagesArchived
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetStoreTelephonyDb
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetTheme

class AndroidAppSettingsService(context: Context) : AppSettingsService {
    private val appContext = context.applicationContext

    override fun snapshot(): AppSettingsSnapshot {
        val tags = appContext.resources.getStringArray(R.array.language_values)
        val labels = appContext.resources.getStringArray(R.array.language_options)
        val languages = tags.mapIndexed { index, tag ->
            LanguageOption(tag, labels.getOrElse(index) { tag })
        }
        val currentTag = appContext.getCurrentLocale()?.toLanguageTag().orEmpty()
        val currentLanguage = languages.firstOrNull { option ->
            currentTag.equals(option.tag, ignoreCase = true) ||
                currentTag.substringBefore('-').equals(option.tag.substringBefore('-'), ignoreCase = true)
        }
        return AppSettingsSnapshot(
            languageTag = currentLanguage?.tag.orEmpty(),
            languageName = currentLanguage?.displayName
                ?: appContext.getCurrentLocale()?.displayName
                ?: labels.firstOrNull().orEmpty(),
            languages = languages,
            themeMode = when(appContext.settingsGetTheme) {
                AppCompatDelegate.MODE_NIGHT_YES -> ThemeMode.DARK
                AppCompatDelegate.MODE_NIGHT_NO -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            },
            storeInSystemDatabase = appContext.settingsGetStoreTelephonyDb,
            deleteFromSystemDatabase = appContext.settingsGetDeleteSystem,
            deliveryReports = appContext.settingsGetGetDeliveryReports,
            swipeActions = appContext.settingsGetEnableSwipeBehaviour,
            keepArchived = appContext.settingsGetKeepMessagesArchived,
            contextReplies = appContext.settingsGetEnableContextReplies,
            use24HourTime = appContext.settingsGetEnable24HourFormat,
        )
    }

    override fun setTheme(mode: ThemeMode): AppSettingsSnapshot {
        val delegateMode = when(mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        appContext.settingsSetTheme(delegateMode)
        AppCompatDelegate.setDefaultNightMode(delegateMode)
        return snapshot()
    }

    override fun setLanguage(tag: String): AppSettingsSnapshot {
        if(snapshot().languages.any { it.tag == tag }) appContext.setLocale(tag)
        return snapshot()
    }

    override fun setBoolean(setting: BooleanSetting, enabled: Boolean): AppSettingsSnapshot {
        when(setting) {
            BooleanSetting.STORE_IN_SYSTEM_DATABASE -> appContext.settingsSetStoreTelephonyDb(enabled)
            BooleanSetting.DELETE_FROM_SYSTEM_DATABASE -> appContext.settingsSetDeleteSystem(enabled)
            BooleanSetting.DELIVERY_REPORTS -> appContext.settingsSetGetDeliveryReports(enabled)
            BooleanSetting.SWIPE_ACTIONS -> appContext.settingsSetCanSwipeBehaviour(enabled)
            BooleanSetting.KEEP_ARCHIVED -> appContext.settingsSetKeepMessagesArchived(enabled)
            BooleanSetting.CONTEXT_REPLIES -> appContext.settingsSetEnableContextReplies(enabled)
            BooleanSetting.USE_24_HOUR_TIME -> appContext.settingsSetEnable24HourFormat(enabled)
        }
        return snapshot()
    }
}
