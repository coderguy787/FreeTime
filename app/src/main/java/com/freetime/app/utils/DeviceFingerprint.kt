package com.freetime.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object DeviceFingerprint {
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
            hardwareSerial = getHardwareSerial().takeIf { it != "unknown" } ?: getAndroidId(context),
            deviceName = Build.DEVICE,
            product = Build.PRODUCT,
            fingerprintHash = ""
        )
    }

    private fun getAndroidId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

            // known broken android_id value, skip it
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

    private fun getDeviceId(context: Context): String {
        return try {
            val androidId = getAndroidId(context)
            if (androidId != "unknown") {
                androidId
            } else {
                generateIdFromBuildProperties()
            }
        } catch (e: Exception) {
            generateIdFromBuildProperties()
        }
    }

    private fun generateIdFromBuildProperties(): String {
        return try {
            val combined = "${Build.MANUFACTURER}${Build.MODEL}${Build.DEVICE}${Build.SERIAL}"
            hashString(combined)
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun generateSecureDeviceFingerprint(context: Context): String {
        val fingerprint = generateFingerprint(context)

        // only real values go into the hash
        val components = listOf(
            fingerprint.androidId,
            fingerprint.hardwareSerial,
            fingerprint.deviceModel,
            fingerprint.deviceBrand,
            fingerprint.product,
            fingerprint.deviceName,
            fingerprint.buildFingerprint
        ).filter { it != "unknown" }

        val combined = components.joinToString("|")
        return hashString(combined)
    }

    fun generateDeviceBindingCode(context: Context): String {
        val components = listOf(
            getAndroidId(context),
            getHardwareSerial(),
            Build.MODEL,
            Build.MANUFACTURER,
            Build.DEVICE
        ).filter { it != "unknown" && it.isNotEmpty() }

        val combined = components.joinToString("::")
        return hashString(combined)
    }

    private fun hashString(input: String): String {
        return try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val hashBytes = messageDigest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("DeviceFingerprint", "Hashing failed: ${e.message}")
            input.hashCode().toString()
        }
    }

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

    fun isSuspiciousDevice(context: Context): SuspicionLevel {
        val fingerprint = generateFingerprint(context)
        var suspicionScore = 0
        val reasons = mutableListOf<String>()

        if (fingerprint.androidId == "9774d56d682e549c") {
            suspicionScore += 30
            reasons.add("Default emulator ANDROID_ID detected")
        }

        if (fingerprint.androidId == "unknown") {
            suspicionScore += 20
            reasons.add("Could not retrieve ANDROID_ID")
        }

        if (fingerprint.hardwareSerial == "unknown") {
            suspicionScore += 15
            reasons.add("Could not retrieve hardware serial")
        }

        if (fingerprint.buildFingerprint.contains("generic")) {
            suspicionScore += 25
            reasons.add("Generic build fingerprint detected (possible emulator/VM)")
        }

        if (fingerprint.buildFingerprint.contains("test-keys")) {
            suspicionScore += 20
            reasons.add("Test build detected (possible custom ROM)")
        }

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

    sealed class SuspicionLevel(val score: Int, val reasons: List<String>) {
        class NORMAL(reasons: List<String>) : SuspicionLevel(0, reasons)
        class LOW(reasons: List<String>) : SuspicionLevel(20, reasons)
        class MEDIUM(reasons: List<String>) : SuspicionLevel(40, reasons)
        class HIGH(reasons: List<String>) : SuspicionLevel(70, reasons)
    }
}
