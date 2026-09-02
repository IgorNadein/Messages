package java.com.afkanerd.deku.DefaultSMS

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

//@SmallTest
@RunWith(AndroidJUnit4::class)
class MmsTest {
    @Test
    fun pduParserTest() {
        val pduRawBytes = listOf(-116, -126, -104, 51, 66, 55, 56, 57, 66, 56, 48, 0, -115, -111,
            -118, -128, -114, 3, 1, -57, -20, -120, 5, -127, 3, 9, 58, -128, -125, 104, 116, 116,
            112, 58, 47, 47, 109, 109, 115, 46, 100, 117, 46, 97, 101, 58, 56, 48, 48, 50, 47, 51,
            66, 55, 56, 57, 66, 56, 48, 0, -119, 25, -128, 43, 57, 55, 49, 53, 56, 49, 52, 52, 50,
            56, 50, 49, 47, 84, 89, 80, 69, 61, 80, 76, 77, 78, 0, -106, 9, -22, 78, 101, 119, 32,
            77, 77, 83, 0).map { it.toByte() }.toByteArray()

        val pdu = PduParser(pduRawBytes).parse()

        assertNotNull(pdu)
        assertEquals(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND, pdu.messageType)
    }

}
