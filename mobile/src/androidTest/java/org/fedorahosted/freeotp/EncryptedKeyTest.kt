package org.fedorahosted.freeotp

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fedorahosted.freeotp.encryptor.EncryptedKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class EncryptedKeyTest {
    private fun generateKey(algorithm: String): SecretKey =
        KeyGenerator.getInstance(algorithm).apply { init(256) }.generateKey()

    @Test
    fun test() {
        val token = generateKey("HmacSHA512")
        val key = generateKey("AES")

        val result = EncryptedKey.encrypt(key, token).decrypt(key)

        assertArrayEquals(token.encoded, result.encoded)
        assertEquals(token.algorithm, result.algorithm)
    }

    @Test
    fun testUTF8() {
        val plaintext = "火尸木火1十寥日烤"
        val key = generateKey("AES")

        val encrypted = EncryptedKey.encrypt(key, SecretKeySpec(plaintext.toByteArray(), "AES"))

        assertEquals(plaintext, String(encrypted.decrypt(key).encoded, Charsets.UTF_8))
    }

    @Test
    fun testSecretKeyFactoryDerivedCallerKeyRoundTrip() {
        val spec = PBEKeySpec("password".toCharArray(), "encrypted-key-test-salt".toByteArray(), 1000, 256)
        try {
            val derivedKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
            val callerKey = SecretKeySpec(derivedKey.encoded, "AES")
            val originalKey = generateKey("HmacSHA512")

            val decryptedKey = EncryptedKey.encrypt(callerKey, originalKey).decrypt(callerKey)

            assertArrayEquals(originalKey.encoded, decryptedKey.encoded)
            assertEquals(originalKey.algorithm, decryptedKey.algorithm)
        } finally {
            spec.clearPassword()
        }
    }
}
