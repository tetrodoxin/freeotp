package org.fedorahosted.freeotp

import org.apache.commons.codec.binary.Base32
import org.fedorahosted.freeotp.utils.Time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** RFC 6238, Appendix B, also implicitly testing the default period of 30 s. */
@RunWith(Parameterized::class)
class TokenRFC6238Test(
    private val time: Long,
    private val algorithm: String,
    private val code: String,
    private val secret: String,
) {
    private val realTime = Time.INSTANCE

    @After
    fun restoreTime() {
        Time.INSTANCE = realTime
    }

    @Test
    fun test() {
        val encoded = Base32().encodeAsString(secret.toByteArray())
        val pair = Token.parseUnsafe("otpauth://totp/foo?secret=$encoded&digits=8&algorithm=$algorithm")

        Time.INSTANCE = object : Time() {
            override fun current() = time * 1000
        }

        assertEquals(code, pair.second.getCode(pair.first).code)
    }

    companion object {
        private const val SHA1 = "12345678901234567890"
        private const val SHA256 = "12345678901234567890123456789012"
        private const val SHA512 = "1234567890123456789012345678901234567890123456789012345678901234"

        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<Array<Any>> = listOf(
            arrayOf(59L, "SHA1", "94287082", SHA1),
            arrayOf(59L, "SHA256", "46119246", SHA256),
            arrayOf(59L, "SHA512", "90693936", SHA512),
            arrayOf(1111111109L, "SHA1", "07081804", SHA1),
            arrayOf(1111111109L, "SHA256", "68084774", SHA256),
            arrayOf(1111111109L, "SHA512", "25091201", SHA512),
            arrayOf(1111111111L, "SHA1", "14050471", SHA1),
            arrayOf(1111111111L, "SHA256", "67062674", SHA256),
            arrayOf(1111111111L, "SHA512", "99943326", SHA512),
            arrayOf(1234567890L, "SHA1", "89005924", SHA1),
            arrayOf(1234567890L, "SHA256", "91819424", SHA256),
            arrayOf(1234567890L, "SHA512", "93441116", SHA512),
            arrayOf(2000000000L, "SHA1", "69279037", SHA1),
            arrayOf(2000000000L, "SHA256", "90698825", SHA256),
            arrayOf(2000000000L, "SHA512", "38618901", SHA512),
            arrayOf(20000000000L, "SHA1", "65353130", SHA1),
            arrayOf(20000000000L, "SHA256", "77737706", SHA256),
            arrayOf(20000000000L, "SHA512", "47863826", SHA512),
        )
    }
}
