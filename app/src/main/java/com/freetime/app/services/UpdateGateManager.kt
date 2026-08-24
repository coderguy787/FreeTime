package com.freetime.app.services

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.freetime.app.data.local.SharedPreferencesHelper
import com.freetime.app.data.network.VersionInfoResponse

object UpdateGateManager {
    const val GATE_DELAY_MS = 5 * 60 * 1000L

    var gateInfo by mutableStateOf<VersionInfoResponse?>(null)
        private set
    var showGate by mutableStateOf(false)
        private set

    suspend fun check(context: Context) {
        val info = AppUpdateManager.checkForUpdate(context) ?: return
        evaluate(context, info)
    }

    // forces the update screen when the version is too old
    fun evaluate(context: Context, info: VersionInfoResponse) {
        if (showGate) return
        if (!AppUpdateManager.isUpdateAvailable(info)) return
        if (info.downloadUrl.isNullOrEmpty()) return

        val prefs = SharedPreferencesHelper(context)
        if (!info.updateId.isNullOrEmpty() && info.updateId == prefs.getGateSkippedUpdateId()) return

        val launchedMs = parseLaunchedAt(info.launchedAt)
        val elapsed = if (launchedMs > 0) System.currentTimeMillis() - launchedMs else GATE_DELAY_MS + 1
        if (elapsed < GATE_DELAY_MS) return

        gateInfo = info
        showGate = true
    }

    fun skip(context: Context) {
        val id = gateInfo?.updateId
        if (!id.isNullOrEmpty()) {
            SharedPreferencesHelper(context).setGateSkippedUpdateId(id)
        }
        hide()
    }

    fun hide() {
        showGate = false
        gateInfo = null
    }

    private fun parseLaunchedAt(launchedAt: String?): Long {
        if (launchedAt.isNullOrEmpty()) return -1L
        return try {
            java.time.Instant.parse(launchedAt).toEpochMilli()
        } catch (e: Exception) {
            -1L
        }
    }
}
