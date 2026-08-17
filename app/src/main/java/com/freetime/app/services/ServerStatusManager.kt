package com.freetime.app.services

import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL

/**
 * ServerStatusManager - Tracks whether the FreeTime server is reachable.
 *
 * The server is considered "up" when the WebSocket is CONNECTED, or when a
 * lightweight health check to the API base URL succeeds. When the server goes
 * down, the app switches to degraded mode: only private text messages remain
 * available, while group chats and media (GIFs, attachments, media downloads)
 * are disabled and the user is notified.
 */
object ServerStatusManager {

    private const val TAG = "ServerStatusManager"

    /** How often to poll the health endpoint. */
    private const val POLL_INTERVAL_MS = 15_000L

    /** HTTP connect/read timeout for the health check. */
    private const val HEALTH_CHECK_TIMEOUT_MS = 5_000

    /** Consecutive failed checks required before declaring the server down. */
    private const val FAILURE_THRESHOLD = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _isServerDown = MutableStateFlow(false)
    val isServerDown: StateFlow<Boolean> = _isServerDown.asStateFlow()

    private var consecutiveFailures = 0
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        pollingJob = scope.launch {
            while (isActive) {
                checkOnce(context)
                delay(POLL_INTERVAL_MS)
            }
        }
        // React immediately to a real-time connection loss/recovery.
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
        val wsConnected = WebSocketManager.getInstance().getConnectionState() == ConnectionState.CONNECTED
        val healthy = try {
            wsConnected || pingHealthEndpoint()
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Health check error: ${e.message}")
            false
        }

        if (healthy) {
            if (consecutiveFailures != 0) {
                android.util.Log.d(TAG, "Health check OK - clearing failures")
            }
            consecutiveFailures = 0
            setServerDown(context, false)
        } else {
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
        val connection = URL(healthUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.readTimeout = HEALTH_CHECK_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            val code = connection.responseCode
            return code in 200..399
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Health check failed for $healthUrl: ${e.message}")
            return false
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun setServerDown(context: Context, down: Boolean) {
        if (_isServerDown.value == down) return
        _isServerDown.value = down
        if (down) {
            android.util.Log.w(TAG, "Server outage detected - notifying user")
            try {
                NotificationHelper.showServerDownNotification(context.applicationContext)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to show server-down notification: ${e.message}")
            }
        } else {
            android.util.Log.i(TAG, "Server is back online")
        }
    }
}
