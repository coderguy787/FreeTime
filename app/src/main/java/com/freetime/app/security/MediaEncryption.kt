package com.freetime.app.security

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ✅ UPDATED: Media Encryption utility for secure end-to-end sharing of media.
 * Uses AES-256-CBC for encryption matching backend implementation.
 * Per-file random keys enable secure sharing without server trust.
 */
class MediaEncryption(private val context: Context) {

    companion object {
        private const val ALGORITHM = "AES"
        private const val BLOCK_MODE = "CBC"
        private const val PADDING = "PKCS5Padding"
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 16 // 128 bits for CBC
        private const val ENCRYPTED_MEDIA_DIR = "encrypted_media"
        private const val KEYSTORE_ALIAS = "freetime_local_media_key"
    }

    /**
     * Generate a new random AES-256 key for a specific media file.
     * @return Base64 encoded key string
     */
    fun generateMediaKey(): String {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE)
        val secretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    /**
     * ✅ UPDATED: Encrypt media data using AES-256-CBC.
     * @param fileData Raw bytes to encrypt
     * @param base64Key The encryption key (Base64 encoded 256-bit key)
     * @return Combined IV + EncryptedData (IV is stored in first 16 bytes)
     */
    fun encryptMedia(fileData: ByteArray, base64Key: String): ByteArray {
        val keyBytes = decodeKeyMaterial(base64Key)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        
        val encryptedData = cipher.doFinal(fileData)
        
        // Return IV + Encrypted Data (matching backend format)
        return iv + encryptedData
    }

    /**
     * ✅ UPDATED: Decrypt media data using AES-256-CBC.
     * @param encryptedBytes Combined IV + EncryptedData (IV in first 16 bytes)
     * @param base64Key The decryption key (Base64 encoded 256-bit key)
     * @return Decrypted raw bytes
     */
    fun decryptMedia(encryptedBytes: ByteArray, base64Key: String): ByteArray {
        val keyBytes = decodeKeyMaterial(base64Key)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)
        
        // Extract IV from first 16 bytes and encrypted data from remainder
        val iv = encryptedBytes.sliceArray(0 until IV_SIZE)
        val encryptedData = encryptedBytes.sliceArray(IV_SIZE until encryptedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        return cipher.doFinal(encryptedData)
    }

    /**
     * Decode the provided key material which may be Base64 (standard or URL-safe) or hex.
     * Valid AES key sizes are 16, 24 or 32 bytes. Try multiple decodings and return the
     * first one that matches an expected AES key length. Otherwise throw an informative exception.
     */
    private fun decodeKeyMaterial(keyStr: String): ByteArray {
        // Try common Base64 variants
        val base64Variants = listOf(Base64.NO_WRAP, Base64.DEFAULT, Base64.URL_SAFE or Base64.NO_WRAP)
        for (flag in base64Variants) {
            try {
                val decoded = Base64.decode(keyStr, flag)
                if (decoded.size == 16 || decoded.size == 24 || decoded.size == 32) return decoded
            } catch (_: IllegalArgumentException) {
                // ignore and try next
            }
        }

        // Try adding padding if missing
        try {
            val padded = padBase64(keyStr)
            val decoded = Base64.decode(padded, Base64.DEFAULT)
            if (decoded.size == 16 || decoded.size == 24 || decoded.size == 32) return decoded
        } catch (_: Exception) {
        }

        // Try hex decoding (common server-side mistake)
        val hex = keyStr.replace("0x", "", true).replace("[^0-9A-Fa-f]".toRegex(), "")
        if (hex.length % 2 == 0 && hex.length >= 32) {
            try {
                val bytes = ByteArray(hex.length / 2)
                for (i in bytes.indices) {
                    val idx = i * 2
                    bytes[i] = hex.substring(idx, idx + 2).toInt(16).toByte()
                }
                if (bytes.size == 16 || bytes.size == 24 || bytes.size == 32) return bytes
            } catch (_: Exception) {
            }
        }

        // If nothing matched, throw useful error
        val sample = when {
            keyStr.length <= 64 -> keyStr
            else -> keyStr.take(64) + "..."
        }
        throw IllegalArgumentException("Unsupported key material or invalid key size after decoding for key='$sample' (decoded length not 16/24/32)")
    }

    private fun padBase64(s: String): String {
        val mod = s.length % 4
        return if (mod == 0) s else s + "=".repeat(4 - mod)
    }

    /**
     * ✅ NEW: Encrypt media data and return IV and encrypted data separately.
     * @param fileData Raw bytes to encrypt
     * @param base64Key The encryption key (Base64 encoded 256-bit key)
     * @return Pair of (Encrypted bytes, IV in Base64)
     */
    fun encryptMediaWithIv(fileData: ByteArray, base64Key: String): Pair<ByteArray, String> {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)
        
        // Generate random IV
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        
        val encryptedData = cipher.doFinal(fileData)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        
        return Pair(encryptedData, ivBase64)
    }

    /**
     * Legacy support or local-only storage (using Keystore)
     */
    // ... (Keystore methods could be kept if needed for local-only private vault)
}
