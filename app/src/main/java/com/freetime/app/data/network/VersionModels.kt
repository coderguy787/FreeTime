package com.freetime.app.data.network

import kotlinx.serialization.Serializable

@Serializable
data class VersionCheckRequest(
    val clientVersion: String,
    val clientVersionCode: Int,
    val clientType: String = "android"
)

@Serializable
data class VersionCheckResponse(
    val compatible: Boolean,
    val minimumRequiredVersion: String,
    val latestVersion: String,
    val message: String = "",
    val shouldUpdate: Boolean = false,
    val updateUrl: String? = null
)
