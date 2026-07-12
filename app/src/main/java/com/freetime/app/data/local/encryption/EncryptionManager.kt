package com.freetime.app.data.local.encryption

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encryption utility for all sensitive data
 * Uses Google Tink library for secure encryption/decryption
 * AES-256-GCM encryption with Android Keystore for key persistence
 */
class EncryptionManager(private val context: Context) {
    private var aead: Aead? = null
    private val PREF_FILE_NAME = "freetime_crypto_prefs"
    private val KEYSET_NAME = "freetime_master_key"
    private val MASTER_KEY_URI = "android-keystore://freetime_master_key"

    init {
        initializeEncryption()
    }

    /**
     * Initialize encryption with Tink and Android Keystore
     */
    private fun initializeEncryption() {
        try {
            AeadConfig.register()
            
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                
            aead = keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            // Depending on requirements, throw or handle gracefully
        }
    }

    /**
     * Encrypt sensitive data
     * @param plaintext The data to encrypt
     * @param associatedData Optional associated data for authentication
     * @return Base64 encoded encrypted data
     */
    fun encrypt(plaintext: String, associatedData: String? = null): String {
        return try {
            val aead = this.aead ?: throw RuntimeException("Encryption not initialized")
            val ciphertext = aead.encrypt(
                plaintext.toByteArray(Charsets.UTF_8),
                associatedData?.toByteArray(Charsets.UTF_8)
            )
            Base64.encodeToString(ciphertext, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Encryption failed", e)
        }
    }

    /**
     * Decrypt encrypted data
     * @param encryptedData Base64 encoded encrypted data
     * @param associatedData Optional associated data that was used during encryption
     * @return Decrypted plaintext
     */
    fun decrypt(encryptedData: String, associatedData: String? = null): String {
        return try {
            val aead = this.aead ?: throw RuntimeException("Encryption not initialized")
            val ciphertext = Base64.decode(encryptedData, Base64.DEFAULT)
            val plaintext = aead.decrypt(
                ciphertext,
                associatedData?.toByteArray(Charsets.UTF_8)
            )
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Decryption failed", e)
        }
    }

    /**
     * Encrypt bytes (for media files)
     */
    fun encryptBytes(plainBytes: ByteArray, associatedData: String? = null): ByteArray {
        return try {
            val aead = this.aead ?: throw RuntimeException("Encryption not initialized")
            aead.encrypt(
                plainBytes,
                associatedData?.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Encryption failed", e)
        }
    }

    /**
     * Decrypt bytes (for media files)
     */
    fun decryptBytes(encryptedBytes: ByteArray, associatedData: String? = null): ByteArray {
        return try {
            val aead = this.aead ?: throw RuntimeException("Encryption not initialized")
            aead.decrypt(
                encryptedBytes,
                associatedData?.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Decryption failed", e)
        }
    }

    /**
     * Decrypt bytes using AES-256-CBC (standard for server-side media)
     * @param encryptedData The raw encrypted bytes from server
     * @param keyBase64 The base64 encoded 32-byte AES key
     * @param ivBase64 The base64 encoded 16-byte IV
     * @return Decrypted plaintext bytes
     */
    fun decryptMediaBytes(encryptedData: ByteArray, keyBase64: String, ivBase64: String): ByteArray {
        return try {
            val keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT)
            val ivBytes = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)
            
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec = javax.crypto.spec.IvParameterSpec(ivBytes)
            
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            // Server format is IV + encryptedData, but we receive raw data and IV separately
            // so we just decrypt the data directly
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Media decryption failed: ${e.message}")
        }
    }

    /**
     * Generate a secure random salt for password hashing
     */
    fun generateSalt(length: Int = 32): String {
        val random = java.security.SecureRandom()
        val salt = ByteArray(length)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.DEFAULT)
    }

    /**
     * Hash password with salt (for local authentication)
     */
    fun hashPassword(password: String, salt: String): String {
        return try {
            val saltBytes = Base64.decode(salt, Base64.DEFAULT)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(saltBytes)
            val hashedPassword = digest.digest(password.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashedPassword, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Password hashing failed", e)
        }
    }

    /**
     * Verify password with salt
     */
    fun verifyPassword(password: String, salt: String, hashedPassword: String): Boolean {
        return try {
            val computed = hashPassword(password, salt)
            computed == hashedPassword
        } catch (e: Exception) {
            false
        }
    }
}
