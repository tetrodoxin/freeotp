package org.fedorahosted.freeotp

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.data.TokenRepository
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** Backing up tokens and restoring them into an empty `tokenStore`, also several times. */
@RunWith(AndroidJUnit4::class)
class TokenPersistenceTest {
    private val context = MemoryContext()
    private val current = prefs("tokenStore")
    private val backup = prefs("tokenBackup")
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun prefs(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val uris = mapOf(
        "gitlab" to "otpauth://totp/gitlab.com:test@redhat.com?secret=nnzl34nyp6ylf5ic43zp5eti2ccgcxuu7ogqflrdzgod7lxo3w4urubx&issuer=gitlab.com",
        "GitHub" to "otpauth://totp/GitHub:testuser?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=GitHub",
        "Red Hat" to "otpauth://hotp/OATH38714F5A?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1&digits=6&issuer=Red%20Hat",
        "test1" to "otpauth://hotp/test1?secret=rvtvsq3l3fgt6dbye76o436vln47q3i45yc227yr4t2hmtmp2el6nzfb&algorithm=SHA256&digits=6&period=30&counter=0",
        "test2" to "otpauth://hotp/test2?secret=7pkajopuoqtxifd5fjx75ymeijowdeuablj7q6dzhglhpglskbyswt4e&algorithm=SHA512&digits=8&period=60&counter=0",
    )

    private val order: List<String>
        get() = JSONArray(current.getString("tokenOrder", null)).let { a -> List(a.length()) { a.getString(it) } }

    private fun addAll(repository: TokenRepository) =
        uris.values.forEach { uri -> Token.parse(uri).let { repository.add(it.first, it.second) } }

    private fun deleteAll(repository: TokenRepository) = repeat(repository.size) { repository.delete(repository.idAt(0)) }

    /** Wipes the tokens and their keys, then restores them from the backup. */
    private fun wipeAndRestore(password: String): TokenRepository {
        order.forEach { keyStore.deleteEntry(it) }
        current.edit().clear().commit()
        assertEquals(0, current.all.size)

        return TokenRepository(context).also {
            assertEquals(0, it.size)
            it.restore(password)
        }
    }

    /** Every listed token has a UUID, a key in the Keystore and generates a code. */
    private fun validateTokens(repository: TokenRepository, count: Int) {
        assertEquals(count, order.size)
        for (id in order) {
            val uuid = UUID.fromString(id).toString()
            assertTrue(keyStore.containsAlias(uuid))
            val token = Token.deserialize(current.getString(uuid, null))
            assertNotNull(token.label)
            assertNotNull(token.getCode(keyStore.getKey(uuid, null)))
        }
        assertEquals(count, repository.size)
    }

    /** Copies the string entries of [from] into [to], replacing what [to] had. */
    private fun copy(from: SharedPreferences, to: SharedPreferences) {
        to.edit().apply {
            clear()
            from.all.forEach { (key, value) -> if (value is String) putString(key, value) }
        }.commit()
    }

    @Test
    fun addRemoveTokensBackupRestore() {
        val password = "MyM4sterPassw0rd"
        TokenPersistence(context).provision(password)
        val repository = TokenRepository(context)
        addAll(repository)
        assertEquals(uris.size, repository.size)

        repository.delete(repository.idAt(0))
        repository.delete(repository.idAt(0))
        val remaining = uris.size - 2
        assertEquals(remaining, repository.size)
        assertEquals(remaining, backup.all.size / 2)

        validateTokens(wipeAndRestore(password), remaining)
    }

    @Test
    fun tokenBackupRestoreMultiPass() {
        val backupCopy = prefs("tokenBackupCopy")
        val persistence = TokenPersistence(context)
        val originalPassword = "backup"
        val newPassword = "redhat"

        persistence.provision(originalPassword)
        val repository = TokenRepository(context)
        addAll(repository)

        repeat(2) {
            copy(backup, backupCopy)
            // Simulate app removal and reinstall.
            deleteAll(repository)
            current.edit().clear().commit()
            assertEquals(0, current.all.size)

            // A new password; the old backup is put back afterwards so the new master key doesn't
            // overwrite it.
            persistence.provision(newPassword)
            copy(backupCopy, backup)
            repository.restore(originalPassword)

            validateTokens(repository, uris.size)
        }
    }

    /** Migrated pre-2.0 tokens are backed up and restored too. */
    @Test
    fun tokenCompatBackupRestore() {
        val password = "MyM4sterPassw0rd"
        prefs("tokens").edit().apply {
            putString("tokenOrder", JSONArray(uris.keys).toString())
            uris.forEach { (name, uri) -> putString(name, uri) }
        }.commit()

        TokenPersistence(context).provision(password)
        TokenRepository(context) // migrates and backs up

        validateTokens(wipeAndRestore(password), uris.size)
    }
}
