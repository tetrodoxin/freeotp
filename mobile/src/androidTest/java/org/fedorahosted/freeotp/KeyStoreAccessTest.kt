package org.fedorahosted.freeotp

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** The security model relies on a Keystore key that can encrypt but never decrypt. */
@RunWith(AndroidJUnit4::class)
class KeyStoreAccessTest {
    @Test
    fun test() {
        val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // NOTE: We can't use PURPOSE_WRAP here because there is no wrap-only granularity.
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .build()
        keyStore.setEntry("master", KeyStore.SecretKeyEntry(secretKey), protection)

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = keyStore.getKey("master", null)
            val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }

            // Encrypt using the KeyStore protected key.
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(secret)
            val gcm = GCMParameterSpec(128, cipher.iv)

            // Decrypt using the in-memory key.
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcm)
            assertArrayEquals(secret, cipher.doFinal(ciphertext))

            // Decrypting with the KeyStore protected key MUST fail for our security model to work.
            assertThrows(InvalidKeyException::class.java) { cipher.init(Cipher.DECRYPT_MODE, key, gcm) }
        } finally {
            keyStore.deleteEntry("master")
        }
    }
}
