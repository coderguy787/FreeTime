package com.freetime.app.utils

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

/**
 * ✅ NEW: Global state manager for triggering group list refresh
 * Used to synchronize group removal across navigation boundaries
 */
object GroupRefreshManager {
    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: State<Int> = _refreshTrigger
    
    fun triggerRefresh() {
        _refreshTrigger.value++
        android.util.Log.d("GroupRefreshManager", "✓ Groups refresh triggered (key=${_refreshTrigger.value})")
    }
}
