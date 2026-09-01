package com.afkanerd.deku.messages.service

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationGroupPreferencesInstrumentedTest {
    @Test
    fun deletingCategoryRemovesOnlyCategoryMetadataAndPersistsEmptyState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "conversation_groups_test_${System.nanoTime()}"
        try {
            val store = ConversationGroupPreferences(context, preferencesName)
            val id = store.create("Переписки", setOf(17, 42))

            assertNotNull(id)
            assertEquals(setOf(17, 42), store.groups.value.single().threadIds)
            assertTrue(store.delete(requireNotNull(id)))
            assertTrue(store.groups.value.isEmpty())

            val reloaded = ConversationGroupPreferences(context, preferencesName)
            assertTrue(reloaded.groups.value.isEmpty())
        } finally {
            context.getSharedPreferences(preferencesName, 0).edit().clear().commit()
        }
    }
}
