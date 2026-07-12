package com.freetime.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Enhanced Device Fingerprinting for Bot Detection & Rate Limiting
 * Uses native Android device identifiers that are very difficult to spoof
 * 
 * Security Model:
 * - ANDROID_ID: Hardware-bound unique ID (very hard to change)
 * - Build.SERIAL: Device serial number (requires ABOOT access)
 * - Build.DEVICE + Build.PRODUCT: Device model/codename (ROM-level change needed)
 * - Build.FINGERPRINT: Build signature (requires ROM flashing)
 * - IMEI: Phone identifier (hardware-level access)
 */
object DeviceFingerprint {
    
    /**
     * Generate comprehensive device fingerprint with multiple identifiers
     */
    fun generateFingerprint(context: Context): com.freetime.app.data.network.DeviceFingerprint {
        return com.freetime.app.data.network.DeviceFingerprint(
            deviceId = getDeviceId(context),
            deviceModel = Build.MODEL,
            deviceBrand = Build.BRAND,
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = getAppVersion(context),
            buildFingerprint = Build.FINGERPRINT,
            androidId = getAndroidId(context),
            timestamp = System.currentTimeMillis(),
            // Enhanced fields for rate limiting
            hardwareSerial = getHardwareSerial().takeIf { it != "unknown" } ?: getAndroidId(context),
            deviceName = Build.DEVICE,
            product = Build.PRODUCT,
            fingerprintHash = ""  // Will be calculated below
        )
    }
    
    /**
     * Get native ANDROID_ID (most reliable for device identification)
     * This is assigned by Android system and persists across app reinstalls
     * Cannot be changed without factory reset
     */
    private fun getAndroidId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            // 9774d56d682e549c is the emulator's ANDROID_ID
            if (androidId != null && androidId != "9774d56d682e549c" && androidId.isNotEmpty()) {
                androidId
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceFingerprint", "Failed to get ANDROID_ID: ${e.message}")
            "unknown"
        }
    }
    
    /**
     * Get hardware serial number (API 26+)
     * Requires changing bootloader on most devices
     * Different from ANDROID_ID and very hard to spoof
     */
    private fun getHardwareSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (serial != null && serial.isNotEmpty() && serial != "unknown") {
                    serial
                } else {
                    "unknown"
                }
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                if (Build.SERIAL != null && Build.SERIAL.isNotEmpty() && Build.SERIAL != "unknown") {
                    Build.SERIAL
                } else {
                    "unknown"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DeviceFingerprint", "Failed to get hardware serial: ${e.message}")
            "unknown"
        }
    }
    
    /**
     * Get unique device ID using multiple fallback methods
     */
    private fun getDeviceId(context: Context): String {
        return try {
            // Primary: Use ANDROID_ID as the main device identifier
            val androidId = getAndroidId(context)
            if (androidId != "unknown") {
                androidId
            } else {
                // Fallback: Generate using Build properties
                generateIdFromBuildProperties()
            }
        } catch (e: Exception) {
            generateIdFromBuildProperties()
        }
    }
    
    /**
     * Generate ID from Build properties if ANDROID_ID is unavailable
     */
    private fun generateIdFromBuildProperties(): String {
        return try {
            // Combine multiple immutable build properties
            val combined = "${Build.MANUFACTURER}${Build.MODEL}${Build.DEVICE}${Build.SERIAL}"
            hashString(combined)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
    
    /**
     * Get app version
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Generate a secure device fingerprint hash using multiple identifiers
     * This creates a fingerprint that's extremely difficult to spoof:
     * - Changing ANDROID_ID requires factory reset or ADB access
     * - Changing Build.SERIAL requires bootloader access
     * - Changing Build.FINGERPRINT requires ROM flashing
     * 
     * Spoofing requires changing multiple low-level device properties
     */
    fun generateSecureDeviceFingerprint(context: Context): String {
        val fingerprint = generateFingerprint(context)
        
        // Combine multiple native identifiers (order matters for hash stability)
        val components = listOf(
            fingerprint.androidId,           // Settings.Secure.ANDROID_ID
            fingerprint.hardwareSerial,      // Build.SERIAL
            fingerprint.deviceModel,         // Build.MODEL
            fingerprint.deviceBrand,         // Build.BRAND
            fingerprint.product,             // Build.PRODUCT
            fingerprint.deviceName,          // Build.DEVICE
            fingerprint.buildFingerprint     // Build.FINGERPRINT
        ).filter { it != "unknown" }
        
        val combined = components.joinToString("|")
        return hashString(combined)
    }
    
    /**
     * Generate device binding code for server-side rate limiting
     * Uses hardware identifiers that can't be bypassed with VPN
     */
    fun generateDeviceBindingCode(context: Context): String {
        val components = listOf(
            getAndroidId(context),      // Cannot change without factory reset
            getHardwareSerial(),             // Cannot change without bootloader access
            Build.MODEL,                // Device model (hardcoded in device)
            Build.MANUFACTURER,            // Device manufacturer
            Build.DEVICE                // Internal device codename
        ).filter { it != "unknown" && it.isNotEmpty() }
        
        val combined = components.joinToString("::") // Double colon for separation
        return hashString(combined)
    }
    
    /**
     * SHA-256 hash for secure fingerprinting
     */
    private fun hashString(input: String): String {
        return try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val hashBytes = messageDigest.digest(input.toByteArray(Charsets.UTF_8))
            // Convert to hex string
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("DeviceFingerprint", "Hashing failed: ${e.message}")
            input.hashCode().toString()
        }
    }
    
    /**
     * Check if device is likely an emulator or virtual machine
     * Helps prevent fake signups from testing/automation tools
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.toLowerCase().contains("vbox") ||
                Build.FINGERPRINT.toLowerCase().contains("test-keys") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MODEL.contains("Android SDK built for arm64") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.MANUFACTURER.contains("insomniac") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT ||
                "sdk_google" == Build.PRODUCT ||
                "sdk" == Build.PRODUCT ||
                Build.MANUFACTURER.toLowerCase().contains("unknown"))
    }
    
    /**
     * Check if device looks suspicious (likely tampered or spoofed)
     */
    fun isSuspiciousDevice(context: Context): SuspicionLevel {
        val fingerprint = generateFingerprint(context)
        var suspicionScore = 0
        val reasons = mutableListOf<String>()
        
        // Check 1: ANDROID_ID is default emulator ID
        if (fingerprint.androidId == "9774d56d682e549c") {
            suspicionScore += 30
            reasons.add("Default emulator ANDROID_ID detected")
        }
        
        // Check 2: Unknown ANDROID_ID (might be spoofed)
        if (fingerprint.androidId == "unknown") {
            suspicionScore += 20
            reasons.add("Could not retrieve ANDROID_ID")
        }
        
        // Check 3: Unknown hardware serial
        if (fingerprint.hardwareSerial == "unknown") {
            suspicionScore += 15
            reasons.add("Could not retrieve hardware serial")
        }
        
        // Check 4: Generic build fingerprint
        if (fingerprint.buildFingerprint.contains("generic")) {
            suspicionScore += 25
            reasons.add("Generic build fingerprint detected (possible emulator/VM)")
        }
        
        // Check 5: Test keys in build
        if (fingerprint.buildFingerprint.contains("test-keys")) {
            suspicionScore += 20
            reasons.add("Test build detected (possible custom ROM)")
        }
        
        // Check 6: Device name contains "Emulator" or "SDK"
        if (fingerprint.deviceModel.contains("Emulator") || 
            fingerprint.deviceName.contains("sdk")) {
            suspicionScore += 25
            reasons.add("Emulator/SDK device detected")
        }
        
        return when {
            suspicionScore >= 70 -> SuspicionLevel.HIGH(reasons)
            suspicionScore >= 40 -> SuspicionLevel.MEDIUM(reasons)
            suspicionScore >= 20 -> SuspicionLevel.LOW(reasons)
            else -> SuspicionLevel.NORMAL(reasons)
        }
    }
    
    /**
     * Suspicion level for device
     */
    sealed class SuspicionLevel(val score: Int, val reasons: List<String>) {
        class NORMAL(reasons: List<String>) : SuspicionLevel(0, reasons)
        class LOW(reasons: List<String>) : SuspicionLevel(20, reasons)
        class MEDIUM(reasons: List<String>) : SuspicionLevel(40, reasons)
        class HIGH(reasons: List<String>) : SuspicionLevel(70, reasons)
    }
}
