package com.afkanerd.deku.messages.service

import android.content.Context
import com.afkanerd.deku.messages.domain.ConversationGroup
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persistent user-created inbox categories. Message rows remain in the messaging database. */
internal class ConversationGroupPreferences(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val _groups = MutableStateFlow(load())

    val groups: StateFlow<List<ConversationGroup>> = _groups.asStateFlow()

    @Synchronized
    fun create(name: String, threadIds: Set<Int>): String? {
        val normalizedName = normalizedName(name) ?: return null
        if(_groups.value.any { it.name.equals(normalizedName, ignoreCase = true) }) return null
        val group = ConversationGroup(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            threadIds = threadIds,
        )
        return if(save(_groups.value + group)) group.id else null
    }

    @Synchronized
    fun update(id: String, name: String, threadIds: Set<Int>): Boolean {
        val normalizedName = normalizedName(name) ?: return false
        if(_groups.value.any {
                it.id != id && it.name.equals(normalizedName, ignoreCase = true)
            }
        ) return false
        if(_groups.value.none { it.id == id }) return false
        return save(
            _groups.value.map { group ->
                if(group.id == id) group.copy(name = normalizedName, threadIds = threadIds)
                else group
            }
        )
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if(_groups.value.none { it.id == id }) return false
        return save(_groups.value.filterNot { it.id == id })
    }

    private fun save(groups: List<ConversationGroup>): Boolean {
        val payload = json.encodeToString(
            groups.map { StoredGroup(it.id, it.name, it.threadIds.sorted()) }
        )
        if(!preferences.edit().putString(GROUPS_KEY, payload).commit()) return false
        _groups.value = groups
        return true
    }

    private fun load(): List<ConversationGroup> = runCatching {
        val payload = preferences.getString(GROUPS_KEY, null) ?: return emptyList()
        json.decodeFromString<List<StoredGroup>>(payload).mapNotNull { stored ->
            val name = normalizedName(stored.name) ?: return@mapNotNull null
            stored.id.takeIf(String::isNotBlank)?.let { id ->
                ConversationGroup(id = id, name = name, threadIds = stored.threadIds.toSet())
            }
        }
    }.getOrDefault(emptyList())

    private fun normalizedName(name: String): String? = name.trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    @Serializable
    private data class StoredGroup(
        val id: String,
        val name: String,
        val threadIds: List<Int>,
    )

    private companion object {
        const val PREFERENCES_NAME = "conversation_groups"
        const val GROUPS_KEY = "groups_v1"
        const val MAX_NAME_LENGTH = 40
    }
}
