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

package org.fedorahosted.freeotp

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import org.fedorahosted.freeotp.data.TokenRepository
import org.fedorahosted.freeotp.encryptor.EncryptedKey
import org.fedorahosted.freeotp.encryptor.MasterKey
import org.fedorahosted.freeotp.utils.Time
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Pins the on-disk backup format.
 *
 * The fixtures in `androidTest/assets/golden/` were produced once by the pre-migration Java code:
 * a provisioned master password plus one TOTP and one HOTP token (RFC 6238 / RFC 4226 test
 * secret), exported with `TokenPersistence.copyBackupToExternal`. `backup.ser` is that export,
 * `backup_prefs.json` the same `tokenBackup` prefs map in readable form, and `master_key.hex` the
 * raw master key.
 *
 * These tests must keep passing unchanged. If one fails, existing users' backups would no longer
 * restore: fix the code, never regenerate the fixtures.
 */
@RunWith(AndroidJUnit4::class)
class BackupFormatGoldenTest {
    private val originalTime = Time.INSTANCE
    private lateinit var prefs: JSONObject
    private lateinit var masterKey: SecretKey

    /** Token UUIDs in the backup: every key that is neither the master key nor a "-token" entry. */
    private val uuids: List<String>
        get() = prefs.keys().asSequence().filter { it != MASTER && !it.endsWith("-token") }.toList()

    private val prefKeys: List<String>
        get() = prefs.keys().asSequence().toList()

    private fun token(uuid: String): Token = Token.deserialize(prefs.getString("$uuid-token"))

    @Before
    fun setUp() {
        prefs = JSONObject(String(asset("backup_prefs.json"), Charsets.UTF_8))
        masterKey = SecretKeySpec(hex(String(asset("master_key.hex"), Charsets.US_ASCII).trim()), "AES")
        Time.INSTANCE = object : Time() {
            override fun current() = 59000L // RFC 6238 test time T = 59 s
        }
    }

    @After
    fun tearDown() {
        Time.INSTANCE = originalTime
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        uuids.forEach { keyStore.deleteEntry(it) }
    }

    @Test
    fun fixtureHasMasterKeyAndTwoTokens() {
        assertTrue(prefs.has(MASTER))
        assertEquals(2, uuids.size)
        assertEquals(5, prefs.length())
    }

    @Test
    fun masterKeyJsonDecryptsWithPassword() {
        val mk = Gson().fromJson(prefs.getString(MASTER), MasterKey::class.java)
        assertArrayEquals(masterKey.encoded, mk.decrypt(PASSWORD).encoded)
    }

    @Test
    fun masterKeyJsonRejectsWrongPassword() {
        val mk = Gson().fromJson(prefs.getString(MASTER), MasterKey::class.java)
        assertThrows(Exception::class.java) { mk.decrypt("not-the-password") }
    }

    @Test
    fun masterKeyJsonRoundTripsUnchanged() {
        val json = prefs.getString(MASTER)
        assertEquals(json, Gson().toJson(Gson().fromJson(json, MasterKey::class.java)))
    }

    @Test
    fun encryptedKeysDecryptWithMasterKey() {
        for (uuid in uuids) {
            val json = JSONObject(prefs.getString(uuid)).getString("key")
            val ek = Gson().fromJson(json, EncryptedKey::class.java)
            val key = ek.decrypt(masterKey)
            assertEquals("HmacSHA1", key.algorithm)
            assertArrayEquals(SECRET, key.encoded)
            assertEquals(json, Gson().toJson(ek))
        }
    }

    @Test
    fun tokenJsonDeserializesWithAllFields() {
        val tokens = uuids.map { uuid ->
            val json = prefs.getString("$uuid-token")
            Token.deserialize(json).also { assertEquals(json, it.serialize()) }
        }

        val totp = tokens.single { it.type == Token.Type.TOTP }
        assertEquals("Golden TOTP", totp.issuer)
        assertEquals("alice@example.com", totp.label)
        assertEquals(30, totp.period)
        assertEquals("https://example.com/golden.png", totp.image)
        assertEquals("673AB7", totp.color)
        assertFalse(totp.lock)
        assertEquals(LAST_USED, totp.lastUsed)

        val hotp = tokens.single { it.type == Token.Type.HOTP }
        assertEquals("Golden HOTP", hotp.issuer)
        assertEquals("bob", hotp.label)
        assertNull(hotp.image)
        assertNull(hotp.color)
        assertNull(hotp.lastUsed)

        assertEquals(2, tokens.size)
    }

    @Test
    fun tokenJsonProducesRfcCodes() {
        val key = SecretKeySpec(SECRET, "HmacSHA1")
        for (uuid in uuids) {
            val t = token(uuid)
            if (t.type == Token.Type.TOTP) {
                assertEquals("94287082", t.getCode(key).code) // RFC 6238, T = 59 s
            } else {
                assertEquals("287082", t.getCode(key).code) // RFC 4226, counter 1
                assertEquals("359152", t.getCode(key).code) // RFC 4226, counter 2
            }
        }
    }

    @Test
    fun exportedBackupFileRestores() {
        val context = MemoryContext()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "golden-backup.ser")
        file.writeBytes(asset("backup.ser"))

        val persistence = TokenPersistence(context)
        persistence.restoreBackupFromExternal(Uri.fromFile(file))
        assertTrue(file.delete())

        // The imported prefs must equal the readable fixture, entry for entry.
        val backup = context.getSharedPreferences("tokenBackup", Context.MODE_PRIVATE)
        assertEquals(prefs.length(), backup.all.size)
        for (key in prefKeys) assertEquals(key, prefs.getString(key), backup.getString(key, null))

        assertThrows(TokenPersistence.BadPasswordException::class.java) { persistence.restore("not-the-password") }

        val restored = persistence.restore(PASSWORD)
        assertEquals(2, restored.size)
        for (r in restored) {
            assertTrue(r.uuid in uuids)
            assertArrayEquals(SECRET, r.key.encoded)
            assertEquals(token(r.uuid).serialize(), r.token.serialize())
        }
    }

    @Test
    fun restoredTokensGenerateCodesThroughRepository() {
        val context = MemoryContext()
        context.getSharedPreferences("tokenBackup", Context.MODE_PRIVATE).edit().apply {
            for (key in prefKeys) putString(key, prefs.getString(key))
        }.commit()

        val repository = TokenRepository(context)
        repository.restore(PASSWORD)
        assertEquals(2, repository.size)

        val codes = List(repository.size) { repository.getCode(repository.idAt(it)).code }.sorted()
        assertEquals(listOf("287082", "94287082"), codes)
    }

    companion object {
        const val PASSWORD = "G0lden-Backup-Passw0rd"
        const val LAST_USED = 1700000000000L

        /* Secret is Base32 of "12345678901234567890", the RFC 4226 / RFC 6238 SHA1 test secret. */
        const val TOTP_URI = "otpauth://totp/Golden%20TOTP:alice@example.com" +
            "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Golden%20TOTP&algorithm=SHA1" +
            "&digits=8&period=30&image=https%3A%2F%2Fexample.com%2Fgolden.png&color=673AB7" +
            "&lock=false"
        const val HOTP_URI = "otpauth://hotp/Golden%20HOTP:bob" +
            "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1&digits=6"

        private val SECRET = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        private const val MASTER = "masterKey"

        private fun asset(name: String): ByteArray =
            InstrumentationRegistry.getInstrumentation().context.assets.open("golden/$name").use { it.readBytes() }

        private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
