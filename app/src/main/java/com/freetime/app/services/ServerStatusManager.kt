package com.freetime.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.freetime.app.data.network.ApiClient
import com.freetime.app.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

object ServerStatusManager {
    private const val TAG = "ServerStatusManager"

    private const val POLL_INTERVAL_MS = 15_000L
    private const val HEALTH_CHECK_TIMEOUT_MS = 5_000L
    private const val FAILURE_THRESHOLD = 3
    private const val STARTUP_GRACE_PERIOD_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _isServerDown = MutableStateFlow(false)
    val isServerDown: StateFlow<Boolean> = _isServerDown.asStateFlow()

    private var consecutiveFailures = 0
    private var started = false
    private var startTimeMs = 0L

    private val httpClient: OkHttpClient by lazy {
        // server health check (self-signed cert)
        val trustAllCerts = arrayOf<TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        startTimeMs = System.currentTimeMillis()
        pollingJob = scope.launch {
            while (isActive) {
                checkOnce(context)
                delay(POLL_INTERVAL_MS)
            }
        }
        scope.launch {
            WebSocketManager.getInstance().connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED && _isServerDown.value) {
                    android.util.Log.i(TAG, "WebSocket reconnected - marking server up")
                    setServerDown(context, false)
                } else if (state == ConnectionState.FAILED) {
                    android.util.Log.w(TAG, "WebSocket FAILED - running immediate health check")
                    checkOnce(context)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        started = false
        pollingJob?.cancel()
        pollingJob = null
        consecutiveFailures = 0
        _isServerDown.value = false
    }

    fun isDown(): Boolean = _isServerDown.value

    private suspend fun checkOnce(context: Context) {
        val elapsed = System.currentTimeMillis() - startTimeMs
        val inGracePeriod = elapsed < STARTUP_GRACE_PERIOD_MS

        val wsConnected = WebSocketManager.getInstance().getConnectionState() == ConnectionState.CONNECTED
        val healthy = try {
            wsConnected || pingHealthEndpoint()
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Health check error: ${e.message}")
            false
        }

        if (healthy) {
            consecutiveFailures = 0
            if (_isServerDown.value) {
                android.util.Log.i(TAG, "Health check OK after outage - marking server up")
                setServerDown(context, false)
            }
        } else {
            if (inGracePeriod) {
                android.util.Log.d(TAG, "Health check failed but within startup grace period (${elapsed / 1000}s / ${STARTUP_GRACE_PERIOD_MS / 1000}s) - not marking down")
                return
            }
            consecutiveFailures++
            if (consecutiveFailures >= FAILURE_THRESHOLD) {
                android.util.Log.w(TAG, "Server unreachable after $consecutiveFailures checks - marking DOWN")
                setServerDown(context, true)
            }
        }
    }

    private fun pingHealthEndpoint(): Boolean {
        val base = ApiClient.getBaseUrl().trimEnd('/')
        val healthUrl = "$base/health"
        return try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            response.close()
            response.code in 200..399
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Health check failed for $healthUrl: ${e.message}")
            false
        }
    }

    private fun setServerDown(context: Context, down: Boolean) {
        if (_isServerDown.value == down) return
        _isServerDown.value = down
        if (down) {
            if (isSlowNetwork(context)) {
                android.util.Log.w(TAG, "Server unreachable on slow network - notifying user")
                try {
                    NotificationHelper.showServerUnstableNotification(context.applicationContext)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to show server-unstable notification: ${e.message}")
                }
            } else {
                android.util.Log.w(TAG, "Server unreachable but on fast network - skipping unstable notification")
            }
        } else {
            consecutiveFailures = 0
            android.util.Log.i(TAG, "Server is back online")
            scope.launch {
                try {
                    OfflineMessageQueue.flush(context.applicationContext)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to flush offline queue: ${e.message}")
                }
            }
        }
    }

    private fun isSlowNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return false
        }
        val downMbps = caps.linkDownstreamBandwidthKbps / 1000
        return downMbps < 5
    }
}
