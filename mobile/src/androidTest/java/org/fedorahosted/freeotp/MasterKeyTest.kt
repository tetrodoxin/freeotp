package org.fedorahosted.freeotp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.fedorahosted.freeotp.encryptor.MasterKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException

@RunWith(AndroidJUnit4::class)
class MasterKeyTest {
    @Test
    fun test() {
        val password = "MyM4sterPassw0rd"
        val original = MasterKey.generate(password)
        val read = Gson().fromJson(Gson().toJson(original), MasterKey::class.java)

        assertArrayEquals(original.decrypt(password).encoded, read.decrypt(password).encoded)
        assertEquals("AES", original.decrypt(password).algorithm)
        assertEquals("AES", read.decrypt(password).algorithm)

        assertThrows(AEADBadTagException::class.java) { original.decrypt("Incorrectpwd") }
    }
}
