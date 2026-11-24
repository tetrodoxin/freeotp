package org.fedorahosted.freeotp

import android.content.Context
import android.net.Uri
import android.security.keystore.UserNotAuthenticatedException
import org.apache.commons.codec.binary.Base32
import org.fedorahosted.freeotp.data.TokenRepository
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.Key
import java.security.KeyStore
import java.util.UUID

/**
 * Parsing `otpauth://` URIs and migrating pre-2.0 tokens (URIs and JSON in the old `tokens`
 * preferences), over variations of every URI part.
 */
@RunWith(Parameterized::class)
class TokenCompatTest(
    private val layout: Layout,
    private val type: String,
    private val issuer: String?,
    private val label: String,
    private val secret: String,
    private val algorithm: String?,
    private val digits: String?,
    private val period: String?,
    private val counter: String?,
    private val image: String?,
    private val color: String?,
    private val lock: String?,
) {
    /** Where the issuer goes: nowhere, the label path, the `issuer` parameter, or both. */
    enum class Layout { NONE, PATH, PARAM, BOTH }

    private val context = MemoryContext()
    private val old = context.getSharedPreferences("tokens", Context.MODE_PRIVATE)
    private val current = context.getSharedPreferences("tokenStore", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private val expectedIssuer get() = if (layout == Layout.NONE) null else issuer
    private val expectedLock get() = lock.equals("true", ignoreCase = true)

    private fun makeJson() = JSONObject().apply {
        put("counter", counter?.toInt() ?: 0)
        put("period", period?.toInt() ?: 30)
        put("digits", digits?.toInt() ?: 6)
        put("algo", algorithm ?: "SHA1")
        put("type", type.uppercase())
        put("label", label)
        put("secret", JSONArray(Base32().decode(secret.uppercase()).map { it.toInt() }))
        if (layout == Layout.PATH || layout == Layout.BOTH) put("issuerExt", issuer)
        if (layout == Layout.PARAM || layout == Layout.BOTH) put("issuerInt", issuer)
        if (image != null) put("image", image)
    }

    private fun makeUri(): Uri = Uri.Builder().scheme("otpauth").authority(type).apply {
        appendEncodedPath(if (layout == Layout.PATH || layout == Layout.BOTH) "$issuer:$label" else label)
        if (layout == Layout.PARAM || layout == Layout.BOTH) appendQueryParameter("issuer", issuer)
        appendQueryParameter("secret", secret)
        listOf(
            "algorithm" to algorithm, "digits" to digits, "period" to period, "counter" to counter,
            "image" to image, "color" to color, "lock" to lock,
        ).forEach { (name, value) -> if (value != null) appendQueryParameter(name, value) }
    }.build()

    /** Puts one token into the old `tokens` preferences. */
    private fun storeOld(token: String) {
        old.edit().clear().putString("tokenOrder", "[\"foo\"]").putString("foo", token).commit()
    }

    /** The only token in `tokenStore`, which must be listed under a UUID with its key in the Keystore. */
    private fun storedToken(): Pair<Key, Token> {
        val order = JSONArray(current.getString("tokenOrder", null))
        assertEquals(1, order.length())
        val uuid = UUID.fromString(order.getString(0)).toString()
        assertTrue(keyStore.containsAlias(uuid))
        return keyStore.getKey(uuid, null) to Token.deserialize(current.getString(uuid, null))
    }

    private fun assertMigrated(token: Token, key: Key, checkColorAndLock: Boolean) {
        assertEquals(Token.Type.valueOf(type.uppercase()), token.type)
        assertEquals(expectedIssuer, token.issuer)
        assertEquals(label, token.label)
        assertEquals(image, token.image)
        if (checkColorAndLock) {
            assertEquals(color, token.color)
            assertEquals(expectedLock, token.lock)
        }
        assertNotNull(token.getCode(key))
    }

    @Test
    fun uriToken() {
        val pair = Token.parse(makeUri())

        assertEquals(Token.Type.valueOf(type.uppercase()), pair.second.type)
        assertEquals(expectedIssuer, pair.second.issuer)
        assertEquals(label, pair.second.label)
        assertEquals(image, pair.second.image)
        assertEquals(color, pair.second.color)
        assertEquals(expectedLock, pair.second.lock)
        assertNotNull(pair.second.getCode(pair.first))
    }

    @Test
    fun uriCompat() {
        storeOld(makeUri().toString())
        TokenRepository(context) // migrates

        assertEquals(0, old.all.size)
        assertEquals(2, current.all.size)
        val (key, token) = storedToken()
        assertMigrated(token, key, checkColorAndLock = true)
    }

    @Test
    fun jsonCompat() {
        storeOld(makeJson().toString())
        TokenRepository(context) // migrates

        assertEquals(0, old.all.size)
        assertEquals(2, current.all.size)
        val (key, token) = storedToken()
        assertMigrated(token, key, checkColorAndLock = false)
    }

    @Test
    fun tokenCompatBackupRestore() {
        val password = "MyM4sterPassw0rd"
        val backup = context.getSharedPreferences("tokenBackup", Context.MODE_PRIVATE)
        storeOld(makeUri().toString())
        TokenPersistence(context).provision(password)
        TokenRepository(context) // migrates and backs up

        // The backup holds the master key and the token's key and metadata.
        assertEquals(3, backup.all.size)

        // Delete the token, then restore it.
        val oldUuid = JSONArray(current.getString("tokenOrder", null)).getString(0)
        current.edit().clear().commit()
        keyStore.deleteEntry(oldUuid)
        assertEquals(0, current.all.size)
        TokenRepository(context).restore(password)

        val (key, token) = storedToken()
        try {
            assertMigrated(token, key, checkColorAndLock = false)
        } catch (_: UserNotAuthenticatedException) {
        }
    }

    companion object {
        /* `lock=true` tokens can't be added without a secure lock screen, which the emulator lacks
         * by default ("Secure lock screen must be enabled to create keys requiring user
         * authentication"). Migration doesn't need to test it. */
        private val SETS: List<List<Any?>> = listOf(
            listOf(Layout.PARAM, Layout.NONE, Layout.PATH, Layout.BOTH),
            listOf("hotp", "HOTP", "totp", "TOTP"),
            listOf("foo", "είμαι πάπια", "Steam"),
            listOf("baz", "είμαι αρκούδα"),
            listOf("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "gezdgnbvgy3tqojqgezdgnbvgy3tqojq"),
            listOf(null, "SHA1", "SHA224", "SHA256", "SHA384", "SHA512"),
            listOf(null, "6", "7", "8", "9"),
            listOf(null, "30", "60"),
            listOf(null, "0", "1234143"),
            listOf(null),
            listOf(null, "000000", "FFFFFF"),
            listOf(null, "false"),
        )

        /** All first values, then each value of each set with the first values of the others. */
        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<Array<Any?>> {
            val defaults = SETS.map { it.first() }
            return listOf(defaults.toTypedArray()) + SETS.flatMapIndexed { i, set ->
                set.map { value -> defaults.toMutableList().also { it[i] = value }.toTypedArray() }
            }
        }
    }
}
