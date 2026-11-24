package org.fedorahosted.freeotp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.apache.commons.codec.binary.Base32
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenRFC4226Test {
    /** RFC 4226, Appendix D, also implicitly testing the defaults digits=6, counter=0, SHA1. */
    @Test
    fun test() {
        val secret = Base32().encodeAsString("12345678901234567890".toByteArray())
        val pair = Token.parseUnsafe("otpauth://hotp/foo?secret=$secret")

        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        assertEquals(expected, expected.map { pair.second.getCode(pair.first).code })
    }
}
