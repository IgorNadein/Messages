package java.com.afkanerd.deku;

import android.content.Context;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.afkanerd.deku.Datastore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)

public class RoomMigrationTest {
    private static final String TEST_DB = "messages-room-migration-test";

    @Rule
    public MigrationTestHelper helper;

    Context context;
    public RoomMigrationTest() {
        this.context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                Datastore.class.getCanonicalName(), new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrate32To33PreservesSecureDataSmsDefaults() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(TEST_DB, 32);
        database.execSQL(
                "INSERT INTO AttachmentTransfer (" +
                        "transferId,address,identityFingerprint,subscriptionId,outgoing," +
                        "mediaType,mimeType,filename,originalSize,encodedSize,totalChunks,sha256," +
                        "codec,width,height,sampleRate,durationMs,status,sourcePath,partialPath," +
                        "completedPath,ratchetOffer,receivedBitmap,sentBitmap,acknowledgedBitmap," +
                        "controlSequence,smsSent,smsDelivered,smsReceived,retryCount,lastError," +
                        "createdAt,updatedAt,expiresAt" +
                        ") VALUES ('legacy-transfer','+79990000000',X'00',1,1,'FILE'," +
                        "'application/octet-stream','legacy.bin',1,1,1,X'00','',0,0,0,0," +
                        "'SENDING',NULL,NULL,NULL,NULL,X'',X'',X'',0,0,0,0,0,NULL,1,1,2)"
        );
        database.execSQL(
                "INSERT INTO AttachmentOfferFragment (transferId,fragmentIndex,totalFragments," +
                        "address,subscriptionId,payload,receivedAt) VALUES " +
                        "('legacy-offer',0,1,'+79990000000',1,X'01',1)"
        );
        database.close();

        database = helper.runMigrationsAndValidate(TEST_DB, 33, true, migration32To33());
        try (Cursor cursor = database.query(
                "SELECT protection, transport FROM AttachmentTransfer " +
                        "WHERE transferId='legacy-transfer'")) {
            cursor.moveToFirst();
            assertEquals("SECURE", cursor.getString(0));
            assertEquals("DATA_SMS", cursor.getString(1));
        }
        try (Cursor cursor = database.query(
                "SELECT flags FROM AttachmentOfferFragment WHERE transferId='legacy-offer'")) {
            cursor.moveToFirst();
            assertEquals(0, cursor.getInt(0));
        }
    }

    @Test
    public void migrate33To34PreservesLegacyTransferAndAddsTransportFields() throws IOException {
        SupportSQLiteDatabase database = helper.createDatabase(TEST_DB, 33);
        database.execSQL(
                "INSERT INTO AttachmentTransfer (" +
                        "transferId,address,identityFingerprint,subscriptionId,outgoing," +
                        "protection,transport,mediaType,mimeType,filename,originalSize,encodedSize," +
                        "totalChunks,sha256,codec,width,height,sampleRate,durationMs,status," +
                        "sourcePath,partialPath,completedPath,ratchetOffer,receivedBitmap,sentBitmap," +
                        "acknowledgedBitmap,controlSequence,smsSent,smsDelivered,smsReceived," +
                        "retryCount,lastError,createdAt,updatedAt,expiresAt" +
                        ") VALUES ('legacy-v33','+79990000000',X'00',1,1,'SECURE','DATA_SMS'," +
                        "'FILE','application/octet-stream','legacy.bin',1,1,1,X'00','',0,0,0,0," +
                        "'SENDING',NULL,NULL,NULL,NULL,X'',X'',X'',0,0,0,0,0,NULL,1,1,2)"
        );
        database.close();

        database = helper.runMigrationsAndValidate(TEST_DB, 34, true, migration33To34());
        try (Cursor cursor = database.query(
                "SELECT protection,transport,chunkPlaintextBytes,remoteProvider," +
                        "remoteLocator,remoteDeleteLocator FROM AttachmentTransfer " +
                        "WHERE transferId='legacy-v33'")) {
            cursor.moveToFirst();
            assertEquals("SECURE", cursor.getString(0));
            assertEquals("DATA_SMS", cursor.getString(1));
            assertEquals(78, cursor.getInt(2));
            assertEquals(true, cursor.isNull(3));
            assertEquals(true, cursor.isNull(4));
            assertEquals(true, cursor.isNull(5));
        }
    }

    private Migration migration32To33() {
        try {
            Class<?> generated = Class.forName(
                    "com.afkanerd.deku.Datastore_AutoMigration_32_33_Impl"
            );
            java.lang.reflect.Constructor<?> constructor = generated.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Migration) constructor.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Generated migration 32 -> 33 is unavailable", error);
        }
    }

    private Migration migration33To34() {
        try {
            Class<?> generated = Class.forName(
                    "com.afkanerd.deku.Datastore_AutoMigration_33_34_Impl"
            );
            java.lang.reflect.Constructor<?> constructor = generated.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Migration) constructor.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Generated migration 33 -> 34 is unavailable", error);
        }
    }

}
