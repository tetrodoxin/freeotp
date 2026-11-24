package org.fedorahosted.freeotp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.data.TokenRepository
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** How [TokenRepository] changes the `tokenStore` preferences and the Keystore. */
@RunWith(AndroidJUnit4::class)
class TokenStoreTest {
    private val pair = Token.parseUnsafe("otpauth://hotp/foo:bar?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
    private val context = MemoryContext()
    private val store = context.getSharedPreferences("tokenStore", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private lateinit var initialOrder: List<String>
    private lateinit var repository: TokenRepository

    private val order: List<String>
        get() = JSONArray(store.getString("tokenOrder", null)).let { a -> List(a.length()) { a.getString(it) } }

    private val size: Int
        get() = store.all.size

    /** Starts with an empty Keystore and four ordered UUIDs without tokens. */
    @Before
    fun reset() {
        keyStore.aliases().toList().forEach { keyStore.deleteEntry(it) }
        initialOrder = List(4) { UUID.randomUUID().toString() }
        store.edit().putString("tokenOrder", JSONArray(initialOrder).toString()).commit()
        repository = TokenRepository(context)
    }

    @Test
    fun addToken() {
        assertEquals(1, size)
        repository.add(pair.first, pair.second)
        assertEquals(2, size)

        val uuid = order.last()
        assertEquals(initialOrder + uuid, order)
        assertEquals(pair.second.serialize(), store.getString(uuid, null))
        assertTrue(keyStore.containsAlias(uuid))
        assertFalse(keyStore.containsAlias("foo"))
    }

    @Test
    fun deleteToken() {
        repository.add(pair.first, pair.second)
        val uuid = order.last()
        assertEquals(initialOrder + uuid, order)

        assertEquals(2, size)
        repository.delete(repository.idAt(repository.size - 1))
        assertEquals(1, size)
        assertEquals(initialOrder, order)

        assertFalse(store.contains(uuid))
        assertFalse(keyStore.containsAlias(uuid))
        assertFalse(keyStore.containsAlias("foo"))
    }

    @Test
    fun moveToken() {
        repository.move(1, 2)

        assertEquals(1, size)
        assertEquals(listOf(initialOrder[0], initialOrder[2], initialOrder[1], initialOrder[3]), order)
    }

    @Test
    fun moveTokenSame() {
        repository.move(1, 1)

        assertEquals(1, size)
        assertEquals(initialOrder, order)
    }
}
