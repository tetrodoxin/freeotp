package org.fedorahosted.freeotp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TokenUriInvalidTest(private val uri: String, private val exception: Class<out Token.InvalidUriException>) {
    @Test
    fun test() {
        val thrown = assertThrows(Exception::class.java) { Token.parse(uri) }
        assertEquals(exception, thrown.javaClass)
    }

    companion object {
        private const val SECRET = "secret=GAYTEMZUGU3DOOBZGAYTEMZUGU"

        @JvmStatic
        @Parameterized.Parameters
        fun data(): List<Array<Any>> = listOf(
            arrayOf("foo", Token.InvalidSchemeException::class.java),
            arrayOf("https://google.com", Token.InvalidSchemeException::class.java),
            arrayOf("otpauth://potp", Token.InvalidTypeException::class.java),
            arrayOf("otpauth://totp", Token.InvalidLabelException::class.java),
            arrayOf("otpauth://totp/", Token.InvalidSecretException::class.java),
            arrayOf("otpauth://totp/foo:bar:baz", Token.InvalidLabelException::class.java),
            arrayOf("otpauth://totp/bar", Token.InvalidSecretException::class.java),
            arrayOf("otpauth://totp/bar?secret=00000000000000000000000000", Token.InvalidSecretException::class.java),
            arrayOf("otpauth://totp/bar?secret=GAYTEMZUGU3DOOBZGAYTEMZU", Token.UnsafeSecretException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&algorithm=foo", Token.InvalidAlgorithmException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&algorithm=MD5", Token.UnsafeAlgorithmException::class.java),
            arrayOf("otpauth://hotp/bar?$SECRET&counter=-1", Token.InvalidCounterException::class.java),
            arrayOf("otpauth://hotp/bar?$SECRET&counter=18446744073709551615", Token.InvalidCounterException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&digits=-1", Token.InvalidDigitsException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&digits=5", Token.UnsafeDigitsException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&digits=10", Token.UnsafeDigitsException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&period=-1", Token.InvalidPeriodException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&period=4", Token.InvalidPeriodException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&period=2147483648", Token.InvalidPeriodException::class.java),

            arrayOf("otpauth://totp/bar?$SECRET&color=00000", Token.InvalidColorException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&color=0000000", Token.InvalidColorException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&color=00000000", Token.InvalidColorException::class.java),
            arrayOf("otpauth://totp/bar?$SECRET&color=GHIJKL", Token.InvalidColorException::class.java),

            // Digit bounds with an alternate token code alphabet.
            arrayOf("otpauth://totp/Steam:bar?$SECRET&digits=4", Token.UnsafeDigitsException::class.java),
            arrayOf("otpauth://totp/Steam:bar?$SECRET&digits=7", Token.UnsafeDigitsException::class.java),
        )
    }
}
