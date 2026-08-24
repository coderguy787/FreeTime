package com.freetime.app.utils

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

object GroupRefreshManager {
    // tells screens to reload their groups
    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: State<Int> = _refreshTrigger

    fun triggerRefresh() {
        _refreshTrigger.value++
        android.util.Log.d("GroupRefreshManager", " Groups refresh triggered (key=${_refreshTrigger.value})")
    }
}
