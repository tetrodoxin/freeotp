package org.fedorahosted.freeotp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.data.TokenRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** UUID-based operations of [TokenRepository]; storage-level coverage is in [TokenStoreTest]. */
@RunWith(AndroidJUnit4::class)
class TokenRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: TokenRepository
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun add(issuer: String): String {
        val pair = Token.parseUnsafe("otpauth://hotp/$issuer:alice?secret=$SECRET&counter=0")
        return repository.add(pair.first, pair.second)
    }

    @Before
    fun setUp() {
        context = Context()
        repository = TokenRepository(context)
    }

    @After
    fun tearDown() {
        repository.ids.forEach { keyStore.deleteEntry(it) }
    }

    @Test
    fun addAppendsAndStoresKey() {
        val a = add("A")
        val b = add("B")

        assertEquals(listOf(a, b), repository.ids)
        assertEquals("B", repository.get(b)!!.issuer)
        assertTrue(keyStore.containsAlias(b))
        assertEquals(listOf(a, b), TokenRepository(context).ids)
    }

    @Test
    fun deleteRemovesTokenAndKey() {
        val a = add("A")
        val b = add("B")

        repository.delete(a)

        assertEquals(listOf(b), repository.ids)
        assertNull(repository.get(a))
        assertFalse(keyStore.containsAlias(a))
        assertEquals(listOf(b), TokenRepository(context).ids)
    }

    @Test
    fun deleteUnknownIsIgnored() {
        val a = add("A")
        repository.delete("00000000-0000-0000-0000-000000000000")
        assertEquals(listOf(a), repository.ids)
    }

    @Test
    fun moveIsPersisted() {
        val a = add("A")
        val b = add("B")
        val c = add("C")

        assertTrue(repository.move(0, 2))

        assertEquals(listOf(b, c, a), repository.ids)
        assertEquals(listOf(b, c, a), TokenRepository(context).ids)
    }

    @Test
    fun setLabelKeepsSecretAndType() {
        val a = add("A")
        val before = repository.get(a)!!

        repository.setLabel(a, "bob", "Example")

        val after = TokenRepository(context).get(a)!!
        assertEquals("bob", after.label)
        assertEquals("Example", after.issuer)
        assertEquals(before.type, after.type)
        assertEquals(before.period, after.period)
    }

    @Test
    fun getCodeRecordsLastUseAndSortsByIt() {
        val a = add("A")
        val b = add("B")
        val c = add("C")

        assertEquals(RFC4226_FIRST, repository.getCode(c).code)
        Thread.sleep(5)
        assertEquals(RFC4226_FIRST, repository.getCode(b).code)

        assertTrue(repository.sortByMostRecentlyUsed())

        // Most recent first; the never used token goes last.
        assertEquals(listOf(b, c, a), repository.ids)
        assertEquals(listOf(b, c, a), TokenRepository(context).ids)
    }

    @Test
    fun getCodeOfUnknownTokenIsError() {
        assertEquals("ERROR", repository.getCode("00000000-0000-0000-0000-000000000000").code)
    }

    private companion object {
        /** The RFC 4226 test secret "12345678901234567890". */
        const val SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        const val RFC4226_FIRST = "755224"
    }
}
