package com.freetime.app

import com.freetime.app.data.network.RawSocketHttpClient
import java.net.HttpURLConnection
import java.net.URL

object TestHttpUrlConnection {
    // manual test only, not used by any screen
    suspend fun testLoginAPI(): String {
        return try {
            android.util.Log.d("TEST_HTTP", "Starting test with RawSocketHttpClient...")

            val loginJson = """{"username":"<TEST_USER>","password":"<TEST_PASSWORD>"}"""
            val url = com.freetime.app.BuildConfig.API_BASE_URL + "login"

            android.util.Log.d("TEST_HTTP", "Calling API: $url")
            val response = RawSocketHttpClient.post(url, loginJson)
            android.util.Log.d("TEST_HTTP", "Response received: ${response.take(100)}...")

            response
        } catch (e: Exception) {
            android.util.Log.e("TEST_HTTP", "Error: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
}
