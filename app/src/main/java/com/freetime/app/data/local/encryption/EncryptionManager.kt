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

class EncryptionManager(private val context: Context) {
    private var aead: Aead? = null
    private val PREF_FILE_NAME = "freetime_crypto_prefs"
    private val KEYSET_NAME = "freetime_master_key"
    private val MASTER_KEY_URI = "android-keystore://freetime_master_key"

    init {
        initializeEncryption()
    }

    private fun initializeEncryption() {
        try {
            AeadConfig.register()

            // encryption keys live in the android keystore
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle

            aead = keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: Exception) {
            // keystore failure surfaces as an error on first use
            e.printStackTrace()
        }
    }

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

    // aes-cbc path for peer media keys
    fun decryptMediaBytes(encryptedData: ByteArray, keyBase64: String, ivBase64: String): ByteArray {
        return try {
            val keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT)
            val ivBytes = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)

            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec = javax.crypto.spec.IvParameterSpec(ivBytes)

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec)

            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Media decryption failed: ${e.message}")
        }
    }

    fun generateSalt(length: Int = 32): String {
        val random = java.security.SecureRandom()
        val salt = ByteArray(length)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.DEFAULT)
    }

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

    fun verifyPassword(password: String, salt: String, hashedPassword: String): Boolean {
        return try {
            val computed = hashPassword(password, salt)
            computed == hashedPassword
        } catch (e: Exception) {
            false
        }
    }
}
