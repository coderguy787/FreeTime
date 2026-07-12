package com.freetime.app

import android.app.Application
import android.util.Log
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FreeTime Application Class
 * Initializes app-wide configuration on startup
 * CRITICAL: SSL context configuration runs here BEFORE any networking code
 */
class FreeTimeApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "FreeTimeApp"
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(activity: android.app.Activity) {
                resumed++
                com.freetime.app.notifications.NotificationHelper.isAppInForeground = resumed > 0
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
        
        Log.d(TAG, "🚀 FreeTime Application starting...")
        
        // ✅ CRITICAL: Initialize Firebase first for FCM notifications
        initializeFirebase()
        
        // ✅ NEW: Initialize Notification Channels early
        com.freetime.app.notifications.NotificationHelper.createNotificationChannels(this)
        
        // ✅ CRITICAL: Configure global SSL context for self-signed certificates
        initializeGlobalSSLContext()
        
        // ✅ NEW: Initialize Tink Encryption globally
        initializeTink()
        
        // ✅ NEW: Initialize WebRTC globally
        // This prevents JNI crashes if WebRTC events fire before local initialization
        initializeWebRTC()
        
        Log.d(TAG, "✅ FreeTime Application fully initialized")
    }

    private fun initializeFirebase() {
        try {
            Log.d(TAG, "🔥 Attempting to initialize Firebase...")
            // ✅ Initialize Firebase App if not already done
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app != null) {
                    Log.d(TAG, "🔥 Firebase App initialized successfully: ${app.name}")
                } else {
                    Log.e(TAG, "🔥 FirebaseApp.initializeApp returned null - check google-services.json")
                }
            } else {
                Log.d(TAG, "🔥 Firebase App already initialized")
            }
            
            // ✅ Initialize Firebase Messaging for FCM
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d(TAG, "🔥 Firebase FCM Token obtained: ${token.substring(0, Math.min(token.length, 20))}...")
                    } else {
                        Log.w(TAG, "🔥 Failed to get Firebase FCM Token", task.exception)
                    }
                }
                Log.d(TAG, "✅ Firebase FCM initialized successfully")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "❌ Firebase Messaging initialization failed: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Firebase: ${e.message}", e)
        }
    }

    private fun initializeTink() {
        try {
            Log.d(TAG, "🔒 Initializing Tink Encryption...")
            com.google.crypto.tink.aead.AeadConfig.register()
            Log.d(TAG, "✅ Tink Encryption initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Tink: ${e.message}", e)
        }
    }

    private fun initializeWebRTC() {
        try {
            Log.d(TAG, "📞 Initializing WebRTC PeerConnectionFactory...")
            org.webrtc.PeerConnectionFactory.initialize(
                org.webrtc.PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
            Log.d(TAG, "✅ WebRTC initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize WebRTC: ${e.message}", e)
        }
    }

    /**
     * ✅ COIL CUSTOM LOADER: Trust self-signed certificates for image loading
     * This is required for profile pictures (PFPs) to show up when using self-signed HTTPS.
     */
    override fun newImageLoader(): ImageLoader {
        Log.d(TAG, "🖼️ Creating custom Coil ImageLoader with SSL trust-all support...")
        
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
            .build()
    }
    
    /**
     * Initialize global SSL context with permissive trust manager
     * This affects ALL HTTPS connections including:
     * - Socket.IO polling (XHR long-polling over HTTPS)
     * - OkHttp API client
     * - Any other networking library
     */
    private fun initializeGlobalSSLContext() {
        try {
            Log.d(TAG, "🔒 Initializing Global SSL Context...")
            
            // Create a trust manager that accepts all certificates (self-signed support)
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                }
            )
            
            // Initialize SSL context with TLSv1.2
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // Set as default for all HTTPS connections
            SSLContext.setDefault(sslContext)
            
            Log.d(TAG, "🔒 Global SSL Context initialized successfully!")
            Log.d(TAG, "   Protocol: TLSv1.2")
            Log.d(TAG, "   Trust Manager: Accepts all certificates (self-signed)")
            Log.d(TAG, "   Scope: All HTTPS connections (Socket.IO, OkHttp, etc)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Failed to initialize SSL context: ${e.message}", e)
            Log.e(TAG, "   Socket.IO and HTTPS connections may fail!")
            Log.e(TAG, "   Stack trace:")
            e.printStackTrace()
        }
    }
}
