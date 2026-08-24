package com.freetime.app.data.network

import android.util.Log
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SignupDiagnostics {
    private const val TAG = "SIGNUP_DIAGNOSTICS"

    data class DiagnosticResult(
        val testName: String,
        val passed: Boolean,
        val message: String,
        val details: Map<String, Any?> = emptyMap()
    )

    // debug helper for signup issues
    suspend fun runFullDiagnostics(): List<DiagnosticResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<DiagnosticResult>()

            results.add(testServerConnectivity())

            results.add(testSignupEndpoint())

            results.add(testNetworkConfiguration())

            results.add(testRequestSerialization())

            results
        }
    }

    private suspend fun testServerConnectivity(): DiagnosticResult {
        return try {
            val baseUrl = ApiClient.getBaseUrl()
            val healthUrl = baseUrl.trimEnd('/') + "/health"

            val response = withContext(Dispatchers.IO) {
                RawSocketHttpClient.post(healthUrl, "{}")
            }

            Log.d(TAG, "Health check response: ${response.take(100)}")

            if (response.contains("\"status\"")) {
                DiagnosticResult(
                    testName = "Server Connectivity",
                    passed = true,
                    message = "Server is reachable",
                    details = mapOf("url" to baseUrl, "responseLength" to response.length)
                )
            } else {
                DiagnosticResult(
                    testName = "Server Connectivity",
                    passed = false,
                    message = "Server returned invalid response",
                    details = mapOf("response" to response.take(200))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server connectivity test failed", e)
            DiagnosticResult(
                testName = "Server Connectivity",
                passed = false,
                message = "Cannot reach server: ${e.message}",
                details = mapOf("error" to e.message, "cause" to e.cause?.toString())
            )
        }
    }

    private suspend fun testSignupEndpoint(): DiagnosticResult {
        return try {
            val baseUrl = ApiClient.getBaseUrl()
            val diagnosticUrl = baseUrl.trimEnd('/') + "/api/diagnostic/signup"

            val response = withContext(Dispatchers.IO) {
                RawSocketHttpClient.post(diagnosticUrl, "{}")
            }

            Log.d(TAG, "Signup diagnostic response: ${response.take(200)}")

            if (response.contains("\"signupEnabled\"")) {
                DiagnosticResult(
                    testName = "Signup Endpoint",
                    passed = true,
                    message = "Signup endpoint is ready",
                    details = mapOf("responseLength" to response.length)
                )
            } else {
                DiagnosticResult(
                    testName = "Signup Endpoint",
                    passed = false,
                    message = "Signup endpoint not ready",
                    details = mapOf("response" to response.take(200))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signup endpoint test failed", e)
            DiagnosticResult(
                testName = "Signup Endpoint",
                passed = false,
                message = "Cannot access signup endpoint: ${e.message}",
                details = mapOf("error" to e.message)
            )
        }
    }

    private fun testNetworkConfiguration(): DiagnosticResult {
        return try {
            val baseUrl = ApiClient.getBaseUrl()
            val isLocalhost = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")
            val isHttps = baseUrl.startsWith("https")

            val details = mapOf(
                "baseUrl" to baseUrl,
                "isLocalhost" to isLocalhost,
                "isHttps" to isHttps,
                "urlValid" to baseUrl.isNotEmpty()
            )

            DiagnosticResult(
                testName = "Network Configuration",
                passed = true,
                message = "Network configuration loaded",
                details = details
            )
        } catch (e: Exception) {
            DiagnosticResult(
                testName = "Network Configuration",
                passed = false,
                message = "Failed to load network config: ${e.message}",
                details = mapOf("error" to e.message)
            )
        }
    }

    private fun testRequestSerialization(): DiagnosticResult {
        return try {
            val deviceFingerprint = DeviceFingerprint(
                deviceId = "test-device",
                deviceModel = "Pixel",
                deviceBrand = "Google",
                osVersion = "14",
                appVersion = "1.0",
                buildFingerprint = "test",
                androidId = "test-android-id"
            )

            val request = SignUpRequest(
                username = "testuser",
                email = "test@example.com",
                displayName = "Test User",
                password = "Test@123456",
                confirmPassword = "Test@123456",
                deviceFingerprint = deviceFingerprint
            )

            // local check only
            val json = com.google.gson.Gson().toJson(request)
            val parsed = JSONObject(json)

            val hasAllFields = listOf("username", "email", "displayName", "password", "confirmPassword", "deviceFingerprint")
                .all { parsed.has(it) }

            DiagnosticResult(
                testName = "Request Serialization",
                passed = hasAllFields,
                message = if (hasAllFields) "Request format valid" else "Request missing fields",
                details = mapOf(
                    "serializedLength" to json.length,
                    "hasUsername" to parsed.has("username"),
                    "hasEmail" to parsed.has("email"),
                    "hasDeviceFingerprint" to parsed.has("deviceFingerprint")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Request serialization test failed", e)
            DiagnosticResult(
                testName = "Request Serialization",
                passed = false,
                message = "Request serialization failed: ${e.message}",
                details = mapOf("error" to e.message)
            )
        }
    }

    fun logResults(results: List<DiagnosticResult>) {
        Log.d(TAG, "===== Signup Diagnostics Results =====")
        results.forEach { result ->
            val status = if (result.passed) " PASS" else " FAIL"
            Log.d(TAG, "$status - ${result.testName}")
            Log.d(TAG, " Message: ${result.message}")
            result.details.forEach { (key, value) ->
                Log.d(TAG, " $key: $value")
            }
        }
        Log.d(TAG, "====================================")
    }
}
