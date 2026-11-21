package org.fedorahosted.freeotp.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.google.gson.Gson
import org.fedorahosted.freeotp.Code
import org.fedorahosted.freeotp.Token
import org.fedorahosted.freeotp.TokenPersistence
import org.fedorahosted.freeotp.encryptor.Encryptor
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKey

/**
 * The token store: the order and metadata of the tokens in the `tokenStore` prefs, their HMAC
 * secrets in the AndroidKeyStore (under the token UUID), and the mirror of both in the
 * `tokenBackup` prefs (see [TokenPersistence]).
 *
 * Tokens are addressed by UUID. The storage format is unchanged from the former `main.Adapter`.
 * Not thread-safe: use it from one thread at a time.
 */
class TokenRepository @Throws(GeneralSecurityException::class, IOException::class) constructor(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val encryptor = Encryptor(context)
    private val backup: TokenPersistence? = try {
        TokenPersistence(context)
    } catch (e: Exception) {
        Log.e(LOGTAG, "Exception", e)
        null
    }
    private val order: MutableList<String> = readOrder(prefs)

    init {
        compat(context)
    }

    /** The token UUIDs in display order (a snapshot). */
    val ids: List<String> get() = order.toList()

    val size: Int get() = order.size

    fun idAt(position: Int): String = order[position]

    fun indexOf(uuid: String): Int = order.indexOf(uuid)

    /** The token metadata, or null if [uuid] is unknown or its JSON can't be read. */
    fun get(uuid: String): Token? = prefs.getString(uuid, null)?.let { Token.deserialize(it) }

    /** Adds a token at the end and returns its new UUID. */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun add(key: SecretKey, token: Token): String = add(key, token, lock = true, uuid = null)

    /** Appends a token, or replaces it in place if [uuid] is already listed. */
    private fun add(key: SecretKey, token: Token, lock: Boolean, uuid: String?): String {
        val id = uuid ?: UUID.randomUUID().toString()
        val listed = id in order
        Log.i(LOGTAG, "Adding token uuid [$id]")

        keyStore.setEntry(
            id, KeyStore.SecretKeyEntry(key),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                .setUserAuthenticationValidityDurationSeconds(token.period)
                .setUserAuthenticationRequired(token.lock && lock)
                .build(),
        )

        if (!listed) order.add(id)
        if (!storeOrder().putString(id, token.serialize()).commit()) {
            if (!listed) {
                order.removeAt(order.size - 1)
                keyStore.deleteEntry(id)
            }
            throw IOException()
        }

        if (backup?.isProvisioned == true) {
            try {
                val data = encryptor.encryptToken(key)
                backup.save(id, token.serialize(), data["key"], false)
            } catch (e: Exception) {
                Log.e(LOGTAG, "Exception", e)
            }
        }

        Log.i(LOGTAG, "Token added uuid [$id]")
        return id
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun delete(uuid: String) {
        val position = order.indexOf(uuid)
        if (position < 0) return

        order.removeAt(position)
        if (!storeOrder().remove(uuid).commit()) {
            order.add(position, uuid)
            throw IOException()
        }

        if (backup?.isProvisioned == true) backup.remove(uuid)
        keyStore.deleteEntry(uuid)
    }

    /** Moves the token at [from] to [to]. Returns false if the new order couldn't be stored. */
    fun move(from: Int, to: Int): Boolean {
        order.add(to, order.removeAt(from))
        if (storeOrder().commit()) return true

        order.add(from, order.removeAt(to))
        return false
    }

    /**
     * Sorts the tokens by last use, most recent first; never used tokens keep their relative
     * order at the end. Returns false if the new order couldn't be stored.
     */
    fun sortByMostRecentlyUsed(): Boolean {
        val sorted = order.sortedByDescending { get(it)?.lastUsed ?: 0L }
        val previous = order.toList()
        order.clear()
        order.addAll(sorted)
        if (storeOrder().commit()) return true

        order.clear()
        order.addAll(previous)
        return false
    }

    @Throws(IOException::class)
    fun setLabel(uuid: String, account: String?, issuer: String?) {
        val token = get(uuid) ?: throw IOException("Unknown token $uuid")
        token.issuer = issuer
        token.label = account

        if (!storeOrder().putString(uuid, token.serialize()).commit()) throw IOException()

        if (backup?.isProvisioned == true) {
            try {
                backup.save(uuid, token.serialize(), null, true)
            } catch (e: Exception) {
                Log.e(LOGTAG, "Exception", e)
            }
        }
    }

    /**
     * Generates the current code and records the token as used. Other key errors yield an
     * "ERROR" code, as before.
     */
    @Throws(UserNotAuthenticatedException::class, KeyPermanentlyInvalidatedException::class)
    fun getCode(uuid: String): Code {
        val stored = prefs.getString(uuid, null)
        if (stored == null) {
            Log.e(LOGTAG, "Stored is null")
            return errorCode()
        }
        val token = Token.deserialize(stored)
        if (token == null) {
            Log.e(LOGTAG, "Token is null")
            return errorCode()
        }

        return try {
            val code = token.getCode(keyStore.getKey(uuid, null))
            token.setLastUsed(System.currentTimeMillis())
            prefs.edit().putString(uuid, token.serialize()).apply()
            code
        } catch (e: UserNotAuthenticatedException) {
            Log.e(LOGTAG, "Exception", e)
            throw e
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.e(LOGTAG, "Exception", e)
            throw e
        } catch (e: GeneralSecurityException) {
            Log.e(LOGTAG, "Exception", e)
            errorCode()
        }
    }

    /**
     * Restores all tokens from the `tokenBackup` prefs, appending them in backup order under
     * their original UUIDs. Runs PBKDF2, so it is slow. Returns the restored UUIDs; tokens that
     * fail to restore are logged and skipped. A restored UUID that is still listed (its keystore
     * key was lost) gets its key back and keeps its position instead of being listed twice.
     */
    @Throws(TokenPersistence.BadPasswordException::class)
    fun restore(password: String): List<String> {
        val backup = backup ?: return emptyList()
        if (!backup.isProvisioned) return emptyList()

        val restored = mutableListOf<String>()
        try {
            for (item in backup.restore(password)) {
                try {
                    restored += add(item.key, item.token, item.token.lock, item.uuid)
                } catch (e: Exception) {
                    Log.e(LOGTAG, "Exception", e)
                }
            }
        } catch (e: TokenPersistence.BadPasswordException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOGTAG, "Exception", e)
        }
        return restored
    }

    private fun storeOrder(): SharedPreferences.Editor =
        prefs.edit().putString(ORDER, GSON.toJson(order))

    /** Migrates pre-2.0 tokens from the `tokens` prefs, newest first, removing each migrated one. */
    private fun compat(context: Context) {
        val sp = context.getSharedPreferences(COMPAT, Context.MODE_PRIVATE)
        val items = readOrder(sp)
        val size = items.size

        for (i in size downTo 1) {
            val key = items.removeAt(i - 1)
            val value = sp.getString(key, null)
            if (value == null) {
                if (!sp.edit().putString(ORDER, GSON.toJson(items)).commit()) items.add(i - 1, key)
                continue
            }

            val uuid = try {
                val pair = Token.compat(value)
                add(pair.first, pair.second, lock = false, uuid = null)
            } catch (e: Exception) {
                items.add(i - 1, key)
                Log.e(LOGTAG, "Exception", e)
                continue
            }

            if (!sp.edit().putString(ORDER, GSON.toJson(items)).remove(key).commit()) {
                // Keep the legacy token and undo the migration, so it is retried next time.
                items.add(i - 1, key)
                try {
                    delete(uuid)
                } catch (e: Exception) {
                    Log.e(LOGTAG, "Exception", e)
                }
            }
        }

        if (size > 0 && items.isEmpty()) sp.edit().remove(ORDER).apply()
    }

    companion object {
        private const val LOGTAG = "TokenRepository"
        private const val COMPAT = "tokens"
        private const val ORDER = "tokenOrder"
        private const val NAME = "tokenStore"
        private val GSON = Gson()

        private fun errorCode() = Code("ERROR", 15)

        private fun readOrder(prefs: SharedPreferences): MutableList<String> =
            GSON.fromJson(prefs.getString(ORDER, "[]"), Array<String>::class.java)
                ?.toMutableList() ?: mutableListOf()
    }
}
