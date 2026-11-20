package org.fedorahosted.freeotp.ui.manualadd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.R
import org.fedorahosted.freeotp.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualAddFormTest {
    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun defaultsProduceTheSameUriAsBeforeTheMigration() {
        val uri = ManualAddForm(account = "jdoe@example.com", issuer = "Example Inc", secret = secret).toUri()
        assertEquals(
            "otpauth://hotp/Example%20Inc%3Ajdoe%40example.com?secret=$secret" +
                "&algorithm=SHA256&digits=6&period=30&counter=0",
            uri.toString(),
        )
    }

    @Test
    fun totpHasNoCounter() {
        val uri = ManualAddForm(account = "a", issuer = "b", secret = secret, type = OtpType.TOTP,
            digits = 8, algorithm = "SHA1", period = 60).toUri()
        assertEquals("otpauth://totp/b%3Aa?secret=$secret&algorithm=SHA1&digits=8&period=60", uri.toString())
    }

    @Test
    fun uriParsesAsToken() {
        val pair = Token.parse(ManualAddForm(account = "jdoe", issuer = "Example", secret = secret,
            type = OtpType.TOTP, digits = 8).toUri())
        assertEquals("Example", pair.second.issuer)
        assertEquals("jdoe", pair.second.label)
        assertEquals(Token.Type.TOTP, pair.second.type)
        assertEquals(30, pair.second.period)
        assertEquals("HmacSHA256", pair.first.algorithm)
    }

    @Test
    fun validation() {
        assertEquals(R.string.manual_empty_secret, ManualAddForm().validate())
        assertEquals(R.string.manual_malformed_issuer_account, ManualAddForm(secret = secret, issuer = "a:b").validate())
        assertEquals(R.string.manual_malformed_issuer_account, ManualAddForm(secret = secret, account = "a:b").validate())
        assertNull(ManualAddForm(secret = secret).validate())
    }
}
