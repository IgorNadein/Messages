package com.afkanerd.deku.DefaultSMS.extensions.context

import android.database.MatrixCursor
import android.provider.Telephony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsMmsNativesControlsTest {

    @Test
    fun mmsPartParser_preservesStandardColumns() {
        val columns = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.MSG_ID,
            Telephony.Mms.Part.SEQ,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.CHARSET,
            Telephony.Mms.Part.CONTENT_DISPOSITION,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.CONTENT_ID,
            Telephony.Mms.Part.CONTENT_LOCATION,
            Telephony.Mms.Part.CT_START,
            Telephony.Mms.Part.CT_TYPE,
            Telephony.Mms.Part._DATA,
            Telephony.Mms.Part.TEXT,
            "sub_id",
        )
        val cursor = MatrixCursor(columns).apply {
            addRow(arrayOf<Any?>(7, 42L, 1, "image/jpeg", "photo.jpg", 106, "inline", "photo.jpg", "<part>", "photo.jpg", "<start>", "image/jpeg", "/data/part", null, 2L))
            moveToFirst()
        }

        val result = parseRawMmsContentsParts(cursor)

        assertEquals(7, result._id)
        assertEquals(42L, result.mid)
        assertEquals("photo.jpg", result.cl)
        assertEquals(2L, result.sub_id)
    }

    @Test
    fun mmsParsers_acceptProviderWithoutOptionalOemColumns() {
        val partCursor = MatrixCursor(
            arrayOf(
                Telephony.Mms.Part._ID,
                Telephony.Mms.Part.MSG_ID,
                Telephony.Mms.Part.SEQ,
                Telephony.Mms.Part.CONTENT_TYPE,
            )
        ).apply {
            addRow(arrayOf<Any?>(9, 51L, 0, "text/plain"))
            moveToFirst()
        }
        val addrCursor = MatrixCursor(
            arrayOf(
                Telephony.Mms.Addr._ID,
                Telephony.Mms.Addr.MSG_ID,
                Telephony.Mms.Addr.ADDRESS,
                Telephony.Mms.Addr.TYPE,
            )
        ).apply {
            addRow(arrayOf<Any?>(3, "51", "+79990000000", "137"))
            moveToFirst()
        }

        val part = parseRawMmsContentsParts(partCursor)
        val addr = parseRawMmsAddrContentsParts(addrCursor)

        assertNull(part.sub_id)
        assertNull(part.name)
        assertEquals("+79990000000", addr.address)
        assertNull(addr.sub_id)
    }
}
