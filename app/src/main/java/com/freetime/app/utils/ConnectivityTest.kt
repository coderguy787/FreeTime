package com.freetime.app.utils

import com.freetime.app.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

object ConnectivityTest {
    suspend fun testServerConnection(): ConnectivityResult = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getInstance().healthCheck()
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        ConnectivityResult(
                            isConnected = true,
                            statusCode = response.code(),
                            message = "Server is online",
                            details = "Health check passed - ${body.status}",
                            timestamp = System.currentTimeMillis()
                        )
                    } else {
                        ConnectivityResult(
                            isConnected = false,
                            statusCode = response.code(),
                            message = "Empty response body",
                            details = "Server returned 200 but no data",
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
                response.code() == 404 -> ConnectivityResult(
                    isConnected = false,
                    statusCode = response.code(),
                    message = "Health endpoint not found",
                    details = "Server exists but health check endpoint is missing",
                    timestamp = System.currentTimeMillis()
                )
                response.code() == 503 -> ConnectivityResult(
                    isConnected = false,
                    statusCode = response.code(),
                    message = "Server unavailable",
                    details = "Server returned 503 - Service Unavailable",
                    timestamp = System.currentTimeMillis()
                )
                else -> ConnectivityResult(
                    isConnected = false,
                    statusCode = response.code(),
                    message = "Server returned ${response.code()}",
                    details = response.message(),
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: HttpException) {
            ConnectivityResult(
                isConnected = false,
                statusCode = e.code(),
                message = "HTTP Error: ${e.message}",
                details = "Server returned ${e.code()} - ${e.response()?.message() ?: "Unknown error"}",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: IOException) {
            // status 0 means no connection
            ConnectivityResult(
                isConnected = false,
                statusCode = 0,
                message = "Network Error: Connection Failed",
                details = "Unable to reach server at ${ApiClient.getBaseUrl()}\n${e.message}",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            ConnectivityResult(
                isConnected = false,
                statusCode = 0,
                message = "Unknown Error: ${e.javaClass.simpleName}",
                details = "${e.message ?: "No error details available"}",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // basic connection test
    suspend fun runComprehensiveTest(): ComprehensiveTestResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<ConnectivityResult>()

        results.add(testServerConnection())

        return@withContext ComprehensiveTestResult(
            allPassed = results.all { it.isConnected },
            results = results,
            timestamp = System.currentTimeMillis(),
            summary = generateSummary(results)
        )
    }

    private fun generateSummary(results: List<ConnectivityResult>): String {
        val passed = results.count { it.isConnected }
        val total = results.size

        return buildString {
            appendLine("=== Connectivity Test Summary ===")
            appendLine("Passed: $passed/$total")
            appendLine()

            results.forEach { result ->
                val status = if (result.isConnected) " PASS" else " FAIL"
                appendLine("$status - ${result.message}")
                if (result.details.isNotBlank()) {
                    appendLine(" Details: ${result.details}")
                }
            }

            appendLine()
            appendLine("Server: ${ApiClient.getBaseUrl()}")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
        }
    }
}

data class ConnectivityResult(
    val isConnected: Boolean,
    val statusCode: Int,
    val message: String,
    val details: String,
    val timestamp: Long
)

data class ComprehensiveTestResult(
    val allPassed: Boolean,
    val results: List<ConnectivityResult>,
    val timestamp: Long,
    val summary: String
)
