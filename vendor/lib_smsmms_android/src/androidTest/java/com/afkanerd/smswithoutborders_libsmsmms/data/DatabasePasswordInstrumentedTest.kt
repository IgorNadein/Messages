package com.afkanerd.smswithoutborders_libsmsmms.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabasePasswordInstrumentedTest {
    @Test
    fun generatedPasswordIsNonZeroStableAndClearedOnlyAfterClose() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val alias = "database_password_instrumented_test_v1"
        lateinit var firstBackingArray: ByteArray
        val firstCopy = Cryptography.getDatabasePassword(context, alias).use { secret ->
            var copy = ByteArray(0)
            secret.useRaw { raw ->
                firstBackingArray = raw
                assertFalse(raw.all { it == 0.toByte() })
                copy = raw.copyOf()
            }
            copy
        }
        assertTrue(firstBackingArray.all { it == 0.toByte() })

        val secondCopy = Cryptography.getDatabasePassword(context, alias).use { secret ->
            var copy = ByteArray(0)
            secret.useRaw { copy = it.copyOf() }
            copy
        }
        assertArrayEquals(firstCopy, secondCopy)
        firstCopy.fill(0)
        secondCopy.fill(0)
    }
}
