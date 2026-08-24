package com.freetime.app.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

object AntiTamperManager {
    private const val TAG = "AntiTamper"

    // obfuscated names so the checks aren't obvious in the apk
    private val _0x4F = arrayOf(
        "frida", "xposed", "substrate", "cYanide",
        "gadget", "gadget.so", "frida-server", "frida_agent",
        "re.frida.server", "libfrida", "linjector"
    )

    private val _0x7A = arrayOf(
        "/sbin/su", "/system/bin/su", "/system/xbin/su",
        "/data/local/su", "/su/bin/su", "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk", "/system/app/BusyBox.apk",
        "/data/adb/magisk", "/cache/su"
    )

    private val _0x3B = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.noshufou.android.su",
        "com.devadvance.rootcloak",
        "com.saurik.substrate",
        "me.phh.superuser"
    )

    private val _0x9E = setOf(
        "com.android.vending",
        "com.android.packageinstaller",
        "com.android.packageinstaller.permission",
        "com.google.android.packageinstaller"
    )

    fun detectTampering(context: Context): Boolean {
        try {
            if (checkFrida()) { android.util.Log.e(TAG, "TAMPER: Frida detected"); return true }
            if (checkFridaPort()) { android.util.Log.e(TAG, "TAMPER: Frida port detected"); return true }
            if (checkFridaMemory()) { android.util.Log.e(TAG, "TAMPER: Frida memory detected"); return true }
            if (checkDebugger()) { android.util.Log.e(TAG, "TAMPER: Debugger detected"); return true }
            if (checkRootBinaries()) { android.util.Log.e(TAG, "TAMPER: Root binaries detected"); return true }
            if (checkRootApps()) { android.util.Log.e(TAG, "TAMPER: Root apps detected"); return true }
            if (checkEmulator()) { android.util.Log.e(TAG, "TAMPER: Emulator detected"); return true }
            if (checkSuspiciousFiles()) { android.util.Log.e(TAG, "TAMPER: Suspicious files detected"); return true }
            if (checkAPKIntegrity(context)) { android.util.Log.e(TAG, "TAMPER: APK integrity failed"); return true }
            if (checkSuspiciousProcesses()) { android.util.Log.e(TAG, "TAMPER: Suspicious processes detected"); return true }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Detection check crashed (NOT treating as tamper): ${e.message}")
        }
        return false
    }

    fun isCompromised(context: Context): Boolean {
        if (!com.freetime.app.BuildConfig.ANTI_TAMPER_ACTIVE) return false
        return detectTampering(context)
    }

    private fun checkFrida(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists()) return false
            val reader = BufferedReader(InputStreamReader(mapsFile.inputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val lower = line?.lowercase() ?: continue
                if (_0x4F.any { lower.contains(it) }) {
                    reader.close()
                    android.util.Log.e(TAG, "Frida detected in memory maps")
                    return true
                }
            }
            reader.close()
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkFridaPort(): Boolean {
        return try {
            // detects frida on its default port
            val socket = ServerSocket(27042)
            socket.close()
            false
        } catch (e: java.net.BindException) {
            android.util.Log.e(TAG, "Frida port 27042 is in use")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkFridaMemory(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val proc = runtime.exec(arrayOf("sh", "-c", "cat /proc/self/maps 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            val suspiciousPatterns = listOf("frida", "gadget", "gmain", "gdb")
            while (reader.readLine().also { line = it } != null) {
                val lower = line?.lowercase() ?: continue
                if (suspiciousPatterns.any { pattern ->
                    lower.contains(pattern) && lower.contains("r-xp")
                }) {
                    reader.close()
                    proc.destroy()
                    android.util.Log.e(TAG, "Frida memory region detected")
                    return true
                }
            }
            reader.close()
            proc.destroy()
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkDebugger(): Boolean {
        return try {
            if (Debug.isDebuggerConnected()) {
                android.util.Log.e(TAG, "Debugger is attached")
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRootBinaries(): Boolean {
        return _0x7A.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkRootApps(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val proc = runtime.exec(arrayOf("sh", "-c", "pm list packages 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            val packages = mutableListOf<String>()
            while (reader.readLine().also { line = it } != null) {
                line?.let { packages.add(it) }
            }
            reader.close()
            proc.destroy()
            _0x3B.any { pkg -> packages.any { it.contains(pkg) } }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkEmulator(): Boolean {
        return try {
            val props = mapOf(
                "ro.product.model" to listOf("sdk", "google_sdk", "generic"),
                "ro.product.brand" to listOf("generic", "google"),
                "ro.hardware" to listOf("goldfish", "ranchu"),
                "ro.kernel.qemu" to listOf("1"),
                "ro.product.device" to listOf("generic", "generic_x86"),
                "ro.boot.hardware" to listOf("goldfish", "ranchu")
            )
            val propsFile = File("/system/build.prop")
            if (!propsFile.exists()) return false
            val reader = BufferedReader(InputStreamReader(propsFile.inputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                for ((key, values) in props) {
                    if (l.startsWith(key)) {
                        val value = l.substringAfter("=").trim().lowercase()
                        if (values.any { value.contains(it) }) {
                            reader.close()
                            android.util.Log.e(TAG, "Emulator detected: $key=$value")
                            return true
                        }
                    }
                }
            }
            reader.close()
            val emuFiles = listOf(
                "/dev/socket/qemud",
                "/dev/qemu_pipe",
                "/system/lib/libc_malloc_debug_qemu.so"
            )
            emuFiles.any { File(it).exists() }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSuspiciousFiles(): Boolean {
        val suspicious = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/sdcard/Download/frida-server",
            "/sdcard/Download/apktool",
            "/data/data/com.topjohnwu.magisk",
            "/data/local/tmp/xposed",
            "/data/local/tmp/cydia_substrate"
        )
        return suspicious.any { path ->
            try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun checkAPKIntegrity(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }

            if (signatures.isNullOrEmpty()) {
                android.util.Log.e(TAG, "No signatures found — APK may be tampered")
                return true
            }

            val sig = signatures[0]
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sig.toByteArray())
            val hashHex = hash.joinToString("") { "%02x".format(it) }

            val expectedHash = getExpectedSignatureHash()
            if (expectedHash != null && hashHex != expectedHash) {
                android.util.Log.e(TAG, "APK signature mismatch — possible repackaging")
                return true
            }

            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "APK integrity check failed: ${e.message}")
            true
        }
    }

    private fun getExpectedSignatureHash(): String? {
        return null
    }

    private fun checkDevSettings(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 &&
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSuspiciousProcesses(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val proc = runtime.exec(arrayOf("sh", "-c", "ps 2>/dev/null"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            val suspiciousProcessNames = listOf("frida", "objection", "jadx", "dex2jar", "apktool")
            while (reader.readLine().also { line = it } != null) {
                val lower = line?.lowercase() ?: continue
                if (suspiciousProcessNames.any { lower.contains(it) }) {
                    reader.close()
                    proc.destroy()
                    android.util.Log.e(TAG, "Suspicious process detected: ${line?.trim()}")
                    return true
                }
            }
            reader.close()
            proc.destroy()
            false
        } catch (e: Exception) {
            false
        }
    }

    fun triggerBlockedScreen(context: Context) {
        android.util.Log.e(TAG, "TAMPERING DETECTED — launching blocked screen")
        val intent = Intent(context, com.freetime.app.security.BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }
}
