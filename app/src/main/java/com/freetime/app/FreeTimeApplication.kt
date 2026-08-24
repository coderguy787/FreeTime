package com.freetime.app

import android.app.Application
import android.util.Log
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.freetime.app.data.local.database.FreeTimeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FreeTimeApplication : Application(), ImageLoaderFactory {
    companion object {
        private const val TAG = "FreeTimeApp"
        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // security check before anything else loads
            if (com.freetime.app.security.AntiTamperManager.isCompromised(this)) {
                com.freetime.app.security.AntiTamperManager.triggerBlockedScreen(this)
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("FreeTimeApp", "Anti-tamper check failed (NOT blocking): ${e.message}")
        }

            registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(activity: android.app.Activity) {
                resumed++
                val wasInForeground = com.freetime.app.notifications.NotificationHelper.isAppInForeground
                com.freetime.app.notifications.NotificationHelper.isAppInForeground = resumed > 0
                if (!wasInForeground && resumed > 0) {
                    com.freetime.app.notifications.NotificationHelper.cancelAllNotifications(activity)
                }
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                resumed--
                com.freetime.app.notifications.NotificationHelper.isAppInForeground = resumed > 0
            }
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        Log.d(TAG, " FreeTime Application starting...")

        initializeFirebase()

        com.freetime.app.notifications.NotificationHelper.createNotificationChannels(this)

        initializeGlobalSSLContext()

        initializeTink()

        registerGlobalChatHistoryDeletedListener()

        Log.d(TAG, " FreeTime Application fully initialized")
    }

    private fun initializeFirebase() {
        try {
            Log.d(TAG, " Attempting to initialize Firebase...")
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app != null) {
                    Log.d(TAG, " Firebase App initialized successfully: ${app.name}")
                } else {
                    Log.e(TAG, " FirebaseApp.initializeApp returned null - check google-services.json")
                }
            } else {
                Log.d(TAG, " Firebase App already initialized")
            }

            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d(TAG, " Firebase FCM Token obtained: ${token.substring(0, Math.min(token.length, 20))}...")
                    } else {
                        Log.w(TAG, " Failed to get Firebase FCM Token", task.exception)
                    }
                }
                Log.d(TAG, " Firebase FCM initialized successfully")
            } catch (e: IllegalStateException) {
                Log.e(TAG, " Firebase Messaging initialization failed: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, " Failed to initialize Firebase: ${e.message}", e)
        }
    }

    private fun initializeTink() {
        try {
            Log.d(TAG, " Initializing Tink Encryption...")
            com.google.crypto.tink.aead.AeadConfig.register()
            Log.d(TAG, " Tink Encryption initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, " Failed to initialize Tink: ${e.message}", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        Log.d(TAG, " Creating custom Coil ImageLoader with SSL trust-all support...")

        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            }
        )

        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .components {
                add(ImageDecoderDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .build()
    }

    private fun registerGlobalChatHistoryDeletedListener() {
        try {
            val wsManager = com.freetime.app.services.WebSocketManager.getInstance()
            wsManager.addListener(object : com.freetime.app.services.WebSocketManager.WebSocketListener {
                override fun onChatHistoryDeleted(data: com.freetime.app.services.WebSocketManager.ChatHistoryDeletedData) {
                    val chatPartnerId = data.recipientId
                    if (chatPartnerId.isEmpty()) return
                    applicationScope.launch {
                        try {
                            val db = FreeTimeDatabase.getInstance(this@FreeTimeApplication)
                            db.messageDao().deleteAllMessagesInChat(chatPartnerId)
                            Log.d(TAG, " Global listener: local DB cleared for chat with $chatPartnerId")
                        } catch (e: Exception) {
                            Log.e(TAG, " Global listener: failed to clear local DB: ${e.message}")
                        }
                    }
                }

                override fun onGroupHistoryCleared(data: com.freetime.app.services.WebSocketManager.GroupHistoryClearedData) {
                    applicationScope.launch {
                        try {
                            val db = FreeTimeDatabase.getInstance(this@FreeTimeApplication)
                            db.messageDao().deleteAllMessagesInChat(data.groupId)
                            Log.d(TAG, " Global listener: group history cleared for ${data.groupId}")
                        } catch (e: Exception) {
                            Log.e(TAG, " Global listener: failed to clear group history: ${e.message}")
                        }
                    }
                }

                override fun onGroupMessageDeleted(data: com.freetime.app.services.WebSocketManager.GroupMessageDeletedData) {
                    applicationScope.launch {
                        try {
                            val db = FreeTimeDatabase.getInstance(this@FreeTimeApplication)
                            db.messageDao().deleteMessageById(data.messageId)
                            Log.d(TAG, " Global listener: group message ${data.messageId} deleted")
                        } catch (e: Exception) {
                            Log.e(TAG, " Global listener: failed to delete group message: ${e.message}")
                        }
                    }
                }
            })
            Log.d(TAG, " Global chat history deleted listener registered")
        } catch (e: Exception) {
            Log.e(TAG, " Failed to register global chat history deleted listener: ${e.message}")
        }
    }

    private fun initializeGlobalSSLContext() {
        try {
            Log.d(TAG, " Initializing Global SSL Context...")

            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                }
            )

            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // ssl setup for the self-signed server cert
            SSLContext.setDefault(sslContext)

            Log.d(TAG, " Global SSL Context initialized successfully!")
            Log.d(TAG, " Protocol: TLSv1.2")
            Log.d(TAG, " Trust Manager: Accepts all certificates (self-signed)")
            Log.d(TAG, " Scope: All HTTPS connections (Socket.IO, OkHttp, etc)")

        } catch (e: Exception) {
            Log.e(TAG, " CRITICAL: Failed to initialize SSL context: ${e.message}", e)
            Log.e(TAG, " Socket.IO and HTTPS connections may fail!")
            Log.e(TAG, " Stack trace:")
            e.printStackTrace()
        }
    }
}
