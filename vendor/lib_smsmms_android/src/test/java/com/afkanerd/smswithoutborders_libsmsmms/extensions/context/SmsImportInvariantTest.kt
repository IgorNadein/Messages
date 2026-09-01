package com.afkanerd.smswithoutborders_libsmsmms.extensions.context

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class SmsImportInvariantTest {
    @Test
    fun threadDiscoveryDeduplicatesRowsWithoutProviderSpecificGroupBy() {
        ShadowContentResolver.registerProviderInternal(
            "sms",
            ThreadProvider(listOf(7, 7, 3, 7)),
        )
        ShadowContentResolver.registerProviderInternal(
            "mms",
            ThreadProvider(listOf(3, 9, 9)),
        )

        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            listOf(
                Pair("7", false),
                Pair("3", false),
                Pair("3", true),
                Pair("9", true),
            ),
            context.loadRawThreads(),
        )
    }

    @Test
    fun importPreservesProviderThreadIdWithoutCreatingItAgain() {
        val providerThreadId = 777
        ShadowContentResolver.registerProviderInternal("sms", object : ContentProvider() {
            override fun onCreate() = true

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor = MatrixCursor(COLUMNS).apply {
                addRow(
                    arrayOf<Any?>(
                        1L,
                        providerThreadId,
                        "+79990000000",
                        1_700_000_000_000L,
                        1_700_000_000_000L,
                        1,
                        0,
                        Telephony.Sms.MESSAGE_TYPE_INBOX,
                        0,
                        1L,
                        0,
                        null, // MIUI/HyperOS commonly exposes a NULL creator.
                        1,
                        "message",
                    )
                )
            }

            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?,
            ): Int = 0
        })

        val context = RuntimeEnvironment.getApplication()
        val imported = context.loadRawSmsMmsDb(
            threadId = providerThreadId.toString(),
            isMms = false,
        )

        assertEquals(1, imported.size)
        assertEquals(providerThreadId, imported.single().sms!!.thread_id)
        assertEquals(null, imported.single().sms!!.creator)
    }

    private companion object {
        val COLUMNS = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
            Telephony.Sms.TYPE,
            Telephony.Sms.LOCKED,
            Telephony.Sms.SUBSCRIPTION_ID,
            Telephony.Sms.ERROR_CODE,
            Telephony.Sms.CREATOR,
            Telephony.Sms.SEEN,
            Telephony.Sms.BODY,
        )
    }

    private class ThreadProvider(private val threadIds: List<Int>) : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(Telephony.Sms.THREAD_ID)).apply {
            threadIds.forEach { addRow(arrayOf(it)) }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
