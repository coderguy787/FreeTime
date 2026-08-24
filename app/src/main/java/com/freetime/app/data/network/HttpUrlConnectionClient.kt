package com.freetime.app.data.network

import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object HttpUrlConnectionClient {
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    init {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        // global ssl context for the self-signed cert
        try {
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            android.util.Log.w("HttpUrlConnectionClient", "Failed to set SSL context: ${e.message}")
        }
    }

    fun post(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        android.util.Log.d("HttpUrlConnectionClient", "Making POST request to: $url")

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.doInput = true

            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FreetimeApp/1.0")

            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }

            val outputStream = connection.outputStream
            outputStream.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            android.util.Log.d("HttpUrlConnectionClient", "Response code: $responseCode")

            // error responses come on a separate stream
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            inputStream?.close()

            android.util.Log.d("HttpUrlConnectionClient", "Response received: ${response.take(100)}...")

            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode: $response")
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        android.util.Log.d("HttpUrlConnectionClient", "Making GET request to: $url")

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doInput = true

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FreetimeApp/1.0")

            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            android.util.Log.d("HttpUrlConnectionClient", "Response code: $responseCode")

            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val response = inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            inputStream?.close()

            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode: $response")
            }

            return response
        } finally {
            connection.disconnect()
        }
    }
}
