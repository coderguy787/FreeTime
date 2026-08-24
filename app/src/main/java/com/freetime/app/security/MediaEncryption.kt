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

class MediaEncryption(private val context: Context) {
    companion object {
        private const val ALGORITHM = "AES"
        private const val BLOCK_MODE = "CBC"
        private const val PADDING = "PKCS5Padding"
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 16
        private const val ENCRYPTED_MEDIA_DIR = "encrypted_media"
        private const val KEYSTORE_ALIAS = "freetime_local_media_key"
    }

    fun generateMediaKey(): String {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE)
        val secretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    fun encryptMedia(fileData: ByteArray, base64Key: String): ByteArray {
        val keyBytes = decodeKeyMaterial(base64Key)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encryptedData = cipher.doFinal(fileData)

        // iv is prepended to the ciphertext
        return iv + encryptedData
    }

    fun decryptMedia(encryptedBytes: ByteArray, base64Key: String): ByteArray {
        val keyBytes = decodeKeyMaterial(base64Key)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)

        val iv = encryptedBytes.sliceArray(0 until IV_SIZE)
        val encryptedData = encryptedBytes.sliceArray(IV_SIZE until encryptedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        return cipher.doFinal(encryptedData)
    }

    // keys may arrive as base64 or hex, try both
    private fun decodeKeyMaterial(keyStr: String): ByteArray {
        val base64Variants = listOf(Base64.NO_WRAP, Base64.DEFAULT, Base64.URL_SAFE or Base64.NO_WRAP)
        for (flag in base64Variants) {
            try {
                val decoded = Base64.decode(keyStr, flag)
                if (decoded.size == 16 || decoded.size == 24 || decoded.size == 32) return decoded
            } catch (_: IllegalArgumentException) {
            }
        }

        try {
            val padded = padBase64(keyStr)
            val decoded = Base64.decode(padded, Base64.DEFAULT)
            if (decoded.size == 16 || decoded.size == 24 || decoded.size == 32) return decoded
        } catch (_: Exception) {
        }

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

    fun encryptMediaWithIv(fileData: ByteArray, base64Key: String): Pair<ByteArray, String> {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encryptedData = cipher.doFinal(fileData)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Pair(encryptedData, ivBase64)
    }

}
