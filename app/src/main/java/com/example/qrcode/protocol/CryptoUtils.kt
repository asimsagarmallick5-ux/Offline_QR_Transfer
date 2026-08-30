package com.example.qrcode.protocol

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16
    private const val PBKDF2_ITERATIONS = 10000
    private const val SALT_SIZE = 16

    fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH * 8, iv))
        
        val ciphertext = cipher.doFinal(data)
        
        // Return Salt + IV + Ciphertext
        return salt + iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        if (encryptedData.size < SALT_SIZE + GCM_IV_LENGTH + GCM_TAG_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted data")
        }

        val salt = encryptedData.sliceArray(0 until SALT_SIZE)
        val iv = encryptedData.sliceArray(SALT_SIZE until SALT_SIZE + GCM_IV_LENGTH)
        val ciphertext = encryptedData.sliceArray(SALT_SIZE + GCM_IV_LENGTH until encryptedData.size)
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH * 8, iv))
        
        return cipher.doFinal(ciphertext)
    }
}
