/*
 * FreeOTP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fedorahosted.freeotp;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;

import org.fedorahosted.freeotp.encryptor.EncryptedKey;
import org.fedorahosted.freeotp.encryptor.MasterKey;
import org.fedorahosted.freeotp.data.TokenRepository;
import org.fedorahosted.freeotp.utils.Time;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the on-disk backup format.
 *
 * The fixtures in {@code androidTest/assets/golden/} were produced once by the pre-migration Java
 * code: a provisioned master password plus one TOTP and one HOTP token (RFC 6238 / RFC 4226 test
 * secret), exported with {@code TokenPersistence.copyBackupToExternal}. {@code backup.ser} is that
 * export, {@code backup_prefs.json} the same {@code tokenBackup} prefs map in readable form, and
 * {@code master_key.hex} the raw master key.
 *
 * These tests must keep passing unchanged. If one fails, existing users' backups would no longer
 * restore: fix the code, never regenerate the fixtures.
 */
@RunWith(AndroidJUnit4.class)
public class BackupFormatGoldenTest {
    static final String PASSWORD = "G0lden-Backup-Passw0rd";
    static final long LAST_USED = 1700000000000L;
    /* Secret is Base32 of "12345678901234567890", the RFC 4226 / RFC 6238 SHA1 test secret. */
    static final String TOTP_URI = "otpauth://totp/Golden%20TOTP:alice@example.com"
            + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Golden%20TOTP&algorithm=SHA1"
            + "&digits=8&period=30&image=https%3A%2F%2Fexample.com%2Fgolden.png&color=673AB7"
            + "&lock=false";
    static final String HOTP_URI = "otpauth://hotp/Golden%20HOTP:bob"
            + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1&digits=6";

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String MASTER = "masterKey";

    private final Time mOriginalTime = Time.INSTANCE;
    private JSONObject mPrefs;
    private SecretKey mMasterKey;


    private static byte[] asset(String name) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("golden/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; )
                out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    /* Token UUIDs in the backup: every key that is neither the master key nor a "-token" entry. */
    private List<String> uuids() {
        List<String> uuids = new ArrayList<>();
        for (Iterator<String> it = mPrefs.keys(); it.hasNext(); ) {
            String key = it.next();
            if (!key.equals(MASTER) && !key.endsWith("-token"))
                uuids.add(key);
        }
        return uuids;
    }

    private Token token(String uuid) throws Exception {
        return Token.deserialize(mPrefs.getString(uuid + "-token"));
    }

    private Context contextWithResolver() {
        final android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new Context() {
            @Override
            public ContentResolver getContentResolver() {
                return target.getContentResolver();
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        mPrefs = new JSONObject(new String(asset("backup_prefs.json"), StandardCharsets.UTF_8));
        mMasterKey = new SecretKeySpec(
                hex(new String(asset("master_key.hex"), StandardCharsets.US_ASCII).trim()), "AES");
        Time.INSTANCE = new Time() {
            @Override
            public long current() {
                return 59000L; // RFC 6238 test time T = 59 s
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        Time.INSTANCE = mOriginalTime;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        for (String uuid : uuids())
            ks.deleteEntry(uuid);
    }

    @Test
    public void fixtureHasMasterKeyAndTwoTokens() {
        assertTrue(mPrefs.has(MASTER));
        assertEquals(2, uuids().size());
        assertEquals(5, mPrefs.length());
    }

    // The fixture's master key is wrapped with PBKDF2-HmacSHA512, which Android only has from API 26
    // on; below that, backups made on newer devices can't be decrypted (see MIGRATION.md).
    @Test
    public void masterKeyJsonDecryptsWithPassword() throws Exception {
        MasterKey mk = new Gson().fromJson(mPrefs.getString(MASTER), MasterKey.class);
        assertArrayEquals(mMasterKey.getEncoded(), mk.decrypt(PASSWORD).getEncoded());
    }

    // The fixture's master key is wrapped with PBKDF2-HmacSHA512, which Android only has from API 26
    // on; below that, backups made on newer devices can't be decrypted (see MIGRATION.md).
    @Test
    public void masterKeyJsonRejectsWrongPassword() throws Exception {
        MasterKey mk = new Gson().fromJson(mPrefs.getString(MASTER), MasterKey.class);
        try {
            mk.decrypt("not-the-password");
            fail("Decrypting with a wrong password must fail");
        } catch (Exception expected) {
        }
    }

    @Test
    public void masterKeyJsonRoundTripsUnchanged() throws Exception {
        String json = mPrefs.getString(MASTER);
        assertEquals(json, new Gson().toJson(new Gson().fromJson(json, MasterKey.class)));
    }

    @Test
    public void encryptedKeysDecryptWithMasterKey() throws Exception {
        for (String uuid : uuids()) {
            String json = new JSONObject(mPrefs.getString(uuid)).getString("key");
            EncryptedKey ek = new Gson().fromJson(json, EncryptedKey.class);
            SecretKey key = ek.decrypt(mMasterKey);
            assertEquals("HmacSHA1", key.getAlgorithm());
            assertArrayEquals(SECRET, key.getEncoded());
            assertEquals(json, new Gson().toJson(ek));
        }
    }

    @Test
    public void tokenJsonDeserializesWithAllFields() throws Exception {
        int totp = 0, hotp = 0;
        for (String uuid : uuids()) {
            String json = mPrefs.getString(uuid + "-token");
            Token t = Token.deserialize(json);
            assertEquals(json, t.serialize());

            if (t.getType() == Token.Type.TOTP) {
                totp++;
                assertEquals("Golden TOTP", t.getIssuer());
                assertEquals("alice@example.com", t.getLabel());
                assertEquals(30, t.getPeriod());
                assertEquals("https://example.com/golden.png", t.getImage());
                assertEquals("673AB7", t.getColor());
                assertFalse(t.getLock());
                assertEquals(Long.valueOf(LAST_USED), t.getLastUsed());
            } else {
                hotp++;
                assertEquals(Token.Type.HOTP, t.getType());
                assertEquals("Golden HOTP", t.getIssuer());
                assertEquals("bob", t.getLabel());
                assertNull(t.getImage());
                assertNull(t.getColor());
                assertNull(t.getLastUsed());
            }
        }
        assertEquals(1, totp);
        assertEquals(1, hotp);
    }

    @Test
    public void tokenJsonProducesRfcCodes() throws Exception {
        SecretKey key = new SecretKeySpec(SECRET, "HmacSHA1");
        for (String uuid : uuids()) {
            Token t = token(uuid);
            if (t.getType() == Token.Type.TOTP) {
                assertEquals("94287082", t.getCode(key).getCode()); // RFC 6238, T = 59 s
            } else {
                assertEquals("287082", t.getCode(key).getCode()); // RFC 4226, counter 1
                assertEquals("359152", t.getCode(key).getCode()); // RFC 4226, counter 2
            }
        }
    }

    // The fixture's master key is wrapped with PBKDF2-HmacSHA512, which Android only has from API 26
    // on; below that, backups made on newer devices can't be decrypted (see MIGRATION.md).
    @Test
    public void exportedBackupFileRestores() throws Exception {
        Context ctx = contextWithResolver();
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getCacheDir(), "golden-backup.ser");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(asset("backup.ser"));
        }

        TokenPersistence tp = new TokenPersistence(ctx);
        tp.restoreBackupFromExternal(Uri.fromFile(file));
        assertTrue(file.delete());

        /* The imported prefs must equal the readable fixture, entry for entry. */
        SharedPreferences bkp = ctx.getSharedPreferences("tokenBackup", 0);
        assertEquals(mPrefs.length(), bkp.getAll().size());
        for (Iterator<String> it = mPrefs.keys(); it.hasNext(); ) {
            String key = it.next();
            assertEquals(key, mPrefs.getString(key), bkp.getString(key, null));
        }

        try {
            tp.restore("not-the-password");
            fail("Restoring with a wrong password must fail");
        } catch (TokenPersistence.BadPasswordException expected) {
        }

        List<TokenPersistence.RestoredData> restored = tp.restore(PASSWORD);
        assertEquals(2, restored.size());
        for (TokenPersistence.RestoredData r : restored) {
            assertTrue(uuids().contains(r.uuid));
            assertArrayEquals(SECRET, r.key.getEncoded());
            assertEquals(token(r.uuid).serialize(), r.token.serialize());
        }
    }

    // The fixture's master key is wrapped with PBKDF2-HmacSHA512, which Android only has from API 26
    // on; below that, backups made on newer devices can't be decrypted (see MIGRATION.md).
    @Test
    public void restoredTokensGenerateCodesThroughRepository() throws Exception {
        Context ctx = contextWithResolver();
        SharedPreferences.Editor ed = ctx.getSharedPreferences("tokenBackup", 0).edit();
        for (Iterator<String> it = mPrefs.keys(); it.hasNext(); ) {
            String key = it.next();
            ed.putString(key, mPrefs.getString(key));
        }
        ed.commit();

        TokenRepository repository = new TokenRepository(ctx);
        repository.restore(PASSWORD);
        assertEquals(2, repository.getSize());

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < repository.getSize(); i++)
            codes.add(repository.getCode(repository.idAt(i)).getCode());
        codes.sort(null);
        assertEquals(Arrays.asList("287082", "94287082"), codes);
    }
}
