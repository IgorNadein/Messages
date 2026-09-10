package com.afkanerd.deku.messages.service

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.LanguageOption
import com.afkanerd.deku.messages.domain.MediaTransport
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.deku.messages.domain.SimTransportSettings
import com.afkanerd.deku.attachments.transport.MediaTransportPreference
import com.afkanerd.deku.attachments.cloud.CloudProvider
import com.afkanerd.deku.attachments.cloud.CloudStorageConfigStore
import com.afkanerd.deku.attachments.cloud.CloudStorageProfile
import com.afkanerd.deku.attachments.cloud.CloudStorageCredentials
import com.afkanerd.deku.attachments.cloud.CloudObjectStoreFactory
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.security.SecureMessageTransportPreference
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getCurrentLocale
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.setLocale
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetEnable24HourFormat
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetEnableContextReplies
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetGetDeliveryReports
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetKeepMessagesArchived
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetStoreTelephonyDb
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetTheme
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetDeleteSystem
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetEnable24HourFormat
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetEnableContextReplies
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetGetDeliveryReports
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetKeepMessagesArchived
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetStoreTelephonyDb
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsSetTheme
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getSimCardInformation

class AndroidAppSettingsService(context: Context) : AppSettingsService {
    private val appContext = context.applicationContext
    private val cloudStorage = CloudStorageConfigStore(appContext)

    override fun snapshot(): AppSettingsSnapshot {
        val cloudProfile = cloudStorage.profile()
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
        val simTransportSettings = runCatching {
            appContext.getSimCardInformation().orEmpty()
                .sortedBy { it.simSlotIndex }
                .map { info ->
                    val subscriptionId = info.subscriptionId.toLong()
                    SimTransportSettings(
                        subscriptionId = subscriptionId,
                        displayName = info.displayName?.toString().orEmpty()
                            .ifBlank { "SIM ${info.simSlotIndex + 1}" },
                        slotIndex = info.simSlotIndex,
                        secureMessageTransport = SecureMessageTransportPreference.selected(
                            appContext,
                            subscriptionId,
                        ),
                        mediaTransport = MediaTransportPreference.selected(
                            appContext,
                            subscriptionId,
                        ),
                    )
                }
        }.getOrDefault(emptyList())
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
            keepArchived = appContext.settingsGetKeepMessagesArchived,
            contextReplies = appContext.settingsGetEnableContextReplies,
            use24HourTime = appContext.settingsGetEnable24HourFormat,
            secureMessageTransport = SecureMessageTransportPreference.selected(appContext),
            mediaTransport = MediaTransportPreference.selected(appContext),
            simTransportSettings = simTransportSettings,
            cloudStorageConfigured = cloudStorage.isConfigured(),
            cloudStorageProvider = cloudProfile?.provider?.name,
            cloudStorageEndpoint = cloudProfile?.endpoint.orEmpty(),
            cloudStorageFolder = cloudProfile?.folder ?: "Messages",
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
            BooleanSetting.KEEP_ARCHIVED -> appContext.settingsSetKeepMessagesArchived(enabled)
            BooleanSetting.CONTEXT_REPLIES -> appContext.settingsSetEnableContextReplies(enabled)
            BooleanSetting.USE_24_HOUR_TIME -> appContext.settingsSetEnable24HourFormat(enabled)
        }
        return snapshot()
    }

    override fun setSecureMessageTransport(
        transport: SecureMessageTransport,
    ): AppSettingsSnapshot {
        SecureMessageTransportPreference.setSelected(appContext, transport)
        return snapshot()
    }

    override fun setSecureMessageTransport(
        subscriptionId: Long,
        transport: SecureMessageTransport,
    ): AppSettingsSnapshot {
        SecureMessageTransportPreference.setSelected(appContext, subscriptionId, transport)
        return snapshot()
    }

    override fun setMediaTransport(transport: MediaTransport): AppSettingsSnapshot {
        MediaTransportPreference.setSelected(appContext, transport)
        return snapshot()
    }

    override fun setMediaTransport(
        subscriptionId: Long,
        transport: MediaTransport,
    ): AppSettingsSnapshot {
        MediaTransportPreference.setSelected(appContext, subscriptionId, transport)
        return snapshot()
    }

    override suspend fun configureCloudStorage(
        provider: String,
        endpoint: String,
        folder: String,
        accessToken: String,
    ): AppSettingsSnapshot {
        val profile = CloudStorageProfile(
            provider = CloudProvider.valueOf(provider),
            endpoint = endpoint,
            folder = folder,
        )
        val pendingCloudTransfers = Datastore.getDatastore(appContext).attachmentTransferDao()
            .countCloudTransfersRequiringAccount()
        val existingProfile = cloudStorage.profile()
        val changesStorageLocation = existingProfile != null && (
            existingProfile.provider != profile.provider ||
                (profile.provider == CloudProvider.SELF_HOSTED &&
                    existingProfile.endpoint.trimEnd('/') != profile.endpoint.trim().trimEnd('/'))
            )
        check(!changesStorageLocation || pendingCloudTransfers == 0) {
            appContext.getString(R.string.oneui_cloud_storage_switch_blocked)
        }
        CloudObjectStoreFactory.create(
            CloudStorageCredentials(profile, accessToken.trim())
        ).validate()
        cloudStorage.save(profile, accessToken)
        AttachmentManager.get(appContext).resumePending()
        return snapshot()
    }

    override suspend fun clearCloudStorage(): AppSettingsSnapshot {
        check(Datastore.getDatastore(appContext).attachmentTransferDao()
            .countCloudTransfersRequiringAccount() == 0
        ) { appContext.getString(R.string.oneui_cloud_storage_disconnect_blocked) }
        cloudStorage.clear()
        MediaTransportPreference.replaceAll(
            appContext,
            MediaTransport.CLOUD_STORAGE,
            MediaTransport.MMS,
        )
        return snapshot()
    }
}
