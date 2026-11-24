package org.fedorahosted.freeotp.encryptor

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class EncryptedKeyCipherSpecTest {
    @Test
    fun generatedAesKeyRoundTripsThroughEncryptedKey() {
        val encryptionKey = generateAesKey()
        val plaintext = generateAesKey()

        val decrypted = EncryptedKey.encrypt(encryptionKey, plaintext).decrypt(encryptionKey)

        assertArrayEquals(plaintext.encoded, decrypted.encoded)
    }

    @Test
    fun masterKeyPasswordRecoverySuppliesAesKeyForEncryptedKey() {
        val password = "cipher-spec-test-password"
        val recoveredEncryptionKey = MasterKey.generate(password).decrypt(password)
        val plaintext = generateAesKey()

        val decrypted = EncryptedKey.encrypt(recoveredEncryptionKey, plaintext).decrypt(recoveredEncryptionKey)

        assertArrayEquals(plaintext.encoded, decrypted.encoded)
    }

    @Test
    fun legacyEncodedAesKeyDecryptsExistingEncryptedKeyRecord() {
        val legacyEncryptionKey = generateAesKey()
        val plaintext = generateAesKey()
        val existingRecord = EncryptedKey.encrypt(legacyEncryptionKey, plaintext)
        val reconstructedLegacyKey = SecretKeySpec(legacyEncryptionKey.encoded, "AES")

        val decrypted = existingRecord.decrypt(reconstructedLegacyKey)

        assertArrayEquals(plaintext.encoded, decrypted.encoded)
    }

    private fun generateAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
}
