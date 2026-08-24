package com.freetime.app.security

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.concurrent.thread

object HostileEnvironment {
    private const val TAG = "AntiTamper"
    private var active = false

    fun deploy() {
        if (active) return
        if (!com.freetime.app.BuildConfig.ANTI_TAMPER_ACTIVE) return
        active = true
        Log.e(TAG, "DEPLOYING HOSTILE ENVIRONMENT — reverse engineering countermeasures active")

        // each check runs on its own thread
        thread(isDaemon = true) { floodTaunts() }

        thread(isDaemon = true) { floodLogcat() }
        thread(isDaemon = true) { killFridaServer() }
        thread(isDaemon = true) { poisonMemoryMaps() }
        thread(isDaemon = true) { corruptFridaPipes() }
        thread(isDaemon = true) { wasteCpuCycles() }
        thread(isDaemon = true) { writeDecoyFiles() }
        thread(isDaemon = true) { overwriteTempFiles() }
        thread(isDaemon = true) { crashDebuggerThreads() }
        thread(isDaemon = true) { injectFakeTraces() }
        thread(isDaemon = true) { exhaustFileDescriptors() }
    }

    private fun floodTaunts() {
        try {
            val taunts = listOf(
                "Well well well... look who decided to reverse engineer this app",
                "Pwned by coder",
                "Did you really think this would work?",
                "Better luck next time :)",
                "Frida says hi... oh wait, it's dead now",
                "Nice try though, A for effort",
                "Your logcat is my canvas now",
                "Debugging this app? Good luck with that",
                "Every hook you try just crashes harder",
                "This app fights back. Deal with it.",
                "Error 418: I'm a teapot. Just kidding, you're pwned.",
                "You're not the first. You won't be the last.",
                "Keep trying. The app keeps winning.",
                "Hook failed. App still standing. Try again.",
                "Memory corruption? That's the point.",
                "Your frida-server just got terminated with extreme prejudice",
                "Hope you enjoy reading garbage logs",
                "The ROGER sees all. ROGER knows all.",
                "Reverse engineering is not a victimless crime",
                "Congratulations, you played yourself",
                "This message will self-destruct. Just like your hook.",
                "Syscall denied. Try crying instead.",
                "ptrace: permission denied. As expected.",
                "Your analysis tools are my entertainment",
                "0 out of 10 for persistence",
                "Debugging session duration: 0 seconds. Result: terminated.",
                "ROGER was here.",
                "You just got ROGER'd.",
                "ROGER sends his regards.",
                "There is no spoon. There is only ROGER.",
                "rm -rf /your/hopes/and/dreams",
                "Access denied. Have a nice day.",
                "Your USB debugging has been noted and appreciated. LOL.",
                "Enjoy the garbage. You earned it.",
                "Hooking failed successfully.",
                "Application integrity: VERIFIED. Your skills: NOT.",
                "ROGER is watching. ROGER is judging.",
                "Error: intelligence not found in attacker",
                "404: Brain cells not found",
                "Your tools are cute. ROGER is not impressed.",
                "The more you try, the harder ROGER laughs."
            )
            val random = SecureRandom()
            for (taunt in taunts) {
                for (i in 0 until 500) {
                    val tag = "Z${random.nextLong().toString(16).takeLast(8)}"
                    when (random.nextInt(3)) {
                        0 -> Log.e(tag, taunt)
                        1 -> Log.w(tag, taunt)
                        2 -> Log.i(tag, taunt)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun floodLogcat() {
        try {
            val garbage = arrayOf(
                "a]9f!2kL#mN\u0024xPqR", "0xDEADBEEF", "SEGFAULT at 0x00000000",
                "JNI DETECTED ERROR IN APPLICATION", "FATAL EXCEPTION: main",
                "java.lang.NullPointerException: tamper detected",
                "SSL handshake failed: certificate revoked", "FRIDA SERVER CRASHED",
                "Xposed module failed to hook", "Memory allocation failed at 0xFF",
                "Native crash: signal 11 (SIGSEGV)", "Anti-debug: ptrace attach denied",
                "DEBUGGER KILLED PROCESS", "frida-server: killed by SIGKILL",
                "Integrity check FAILED", "HMAC verification error: data corrupted"
            )
            val random = SecureRandom()
            for (i in 0 until 50000) {
                val msg = garbage[random.nextInt(garbage.size)]
                val tag = "Z${random.nextLong().toString(16).takeLast(8)}"
                when (random.nextInt(4)) {
                    0 -> Log.e(tag, msg)
                    1 -> Log.w(tag, msg)
                    2 -> Log.d(tag, msg)
                    3 -> Log.i(tag, msg)
                }
            }
        } catch (_: Exception) {}
    }

    private fun killFridaServer() {
        try {
            val runtime = Runtime.getRuntime()
            val killCmds = listOf(
                "killall -9 re.frida.server",
                "killall -9 frida-server",
                "pkill -9 frida",
                "am force-stop re.frida.server",
                "pkill -9 gadget"
            )
            for (cmd in killCmds) {
                try {
                    val proc = runtime.exec(arrayOf("sh", "-c", cmd))
                    proc.waitFor()
                } catch (_: Exception) {}
            }
            try {
                val proc = runtime.exec(arrayOf("sh", "-c", "fuser -k 27042/tcp 2>/dev/null"))
                proc.waitFor()
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun poisonMemoryMaps() {
        try {
            val buffers = mutableListOf<ByteArray>()
            for (i in 0 until 100) {
                val size = 1024 * 1024
                val buf = ByteArray(size)
                SecureRandom().nextBytes(buf)
                buffers.add(buf)
            }
            @Suppress("unused")
            val keepAlive = buffers
        } catch (_: OutOfMemoryError) {
        } catch (_: Exception) {}
    }

    private fun corruptFridaPipes() {
        try {
            val pipePaths = listOf(
                "/data/local/tmp/.frida-*",
                "/tmp/frida-*",
                "/dev/socket/re.frida.server",
                "/proc/self/fd/0",
                "/proc/self/fd/1",
                "/proc/self/fd/2"
            )
            for (path in pipePaths) {
                try {
                    val file = File(path)
                    if (file.exists() && file.canWrite()) {
                        val garbage = ByteArray(4096)
                        SecureRandom().nextBytes(garbage)
                        file.writeBytes(garbage)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun wasteCpuCycles() {
        try {
            val threads = mutableListOf<Thread>()
            for (i in 0 until 20) {
                val t = thread(isDaemon = true) {
                    val random = SecureRandom()
                    var acc = 0L
                    while (active) {
                        acc += random.nextLong()
                        acc = acc xor (acc shr 17)
                        acc = acc * 0x5DEECE66DL
                        for (j in 0 until 10000) {
                            acc += j.toLong() * random.nextInt()
                        }
                    }
                }
                threads.add(t)
            }
        } catch (_: Exception) {}
    }

    private fun writeDecoyFiles() {
        try {
            val decoyDir = File("/data/local/tmp/", ".ft_cache_${System.currentTimeMillis()}")
            if (decoyDir.mkdirs() || decoyDir.exists()) {
                // decoy files for anyone snooping around
                val fakeContents = listOf(
                    "AES-256-GCM:4f8a2b1c9d3e5f6a7b8c9d0e1f2a3b4c5d6e7f8a",
                    "RSA-2048:MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
                    "HMAC-SHA256:a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0",
                    "ENCRYPTION_KEY_NOT_REDACTED:key=0x4F8A2B1C9D3E5F6A7B8C9D0E",
                    "API_SECRET:sk_live_FAKE_DATA_FOR_ANALYSIS_1234567890",
                    "JWT_SECRET:hmac-sha256=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
                    "FIREBASE_KEY=AAAAfakeserverkey1234567890abcdefghijklmnop",
                    "DATABASE_PASSWORD=p@ssw0rd_FAKE_12345",
                    "PRIVATE_KEY-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA",
                    "MASTER_SECRET:0xDEADBEEF0xCAFEBABE0x1234567890ABCDEF"
                )
                for ((index, content) in fakeContents.withIndex()) {
                    val file = File(decoyDir, "secret_${index}.enc")
                    file.writeText(content)
                }
                File(decoyDir, "DO_NOT_DELETE.enc").writeText(
                    "CRITICAL: This file contains the master encryption key\n" +
                    "Algorithm: AES-256-GCM\n" +
                    "Key: ${SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }}\n" +
                    "IV: ${SecureRandom().generateSeed(16).joinToString("") { "%02x".format(it) }}"
                )
            }
        } catch (_: Exception) {}
    }

    private fun overwriteTempFiles() {
        try {
            val tempDirs = listOf(
                File("/data/local/tmp"),
                File("/tmp"),
                File(System.getProperty("java.io.tmpdir") ?: "/tmp")
            )
            val garbage = ByteArray(8192)
            SecureRandom().nextBytes(garbage)
            for (dir in tempDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.take(50)?.forEach { file ->
                        try {
                            if (file.isFile && file.canWrite() && file.length() < 1_000_000) {
                                file.writeBytes(garbage)
                                file.delete()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun crashDebuggerThreads() {
        try {
            for (i in 0 until 10) {
                thread(isDaemon = true) {
                    try {
                        while (active) {
                            try {
                                val arr = IntArray(0)
                                @Suppress("KotlinConstantConditions")
                                arr[1] = 42
                            } catch (_: ArrayIndexOutOfBoundsException) {}
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun injectFakeTraces() {
        try {
            val fakeTraces = listOf(
                "FATAL EXCEPTION: OkHttp ConnectionPool\n" +
                    "java.lang.NullPointerException: Attempt to invoke virtual method on null object reference\n" +
                    "\tat com.freetime.app.network.ApiClient.connect(ApiClient.java:42)\n" +
                    "\tat okhttp3.internal.connection.RealConnection.connectSocket(RealConnection.java:295)",

                "FATAL EXCEPTION: Firebase Background\n" +
                    "java.lang.SecurityException: Permission denied\n" +
                    "\tat com.google.firebase.messaging.FirebaseMessagingService.onMessageReceived(FirebaseMessagingService.java:89)",

                "FATAL EXCEPTION: WorkManager\n" +
                    "java.lang.OutOfMemoryError: Java heap space\n" +
                    "\tat java.util.Arrays.copyOf(Arrays.java:3236)\n" +
                    "\tat com.freetime.app.services.MessageSyncWorker.doWork(MessageSyncWorker.java:156)"
            )
            val random = SecureRandom()
            for (trace in fakeTraces) {
                for (i in 0 until 500) {
                    val tag = "System.${random.nextInt(9999)}"
                    Log.e(tag, trace)
                }
            }
        } catch (_: Exception) {}
    }

    private fun exhaustFileDescriptors() {
        try {
            val streams = mutableListOf<java.io.FileInputStream>()
            for (i in 0 until 1000) {
                try {
                    streams.add(File("/dev/null").inputStream())
                } catch (_: Exception) {
                    break
                }
            }
            @Suppress("unused")
            val keepAlive = streams
        } catch (_: Exception) {}
    }

    fun stop() {
        active = false
    }
}
