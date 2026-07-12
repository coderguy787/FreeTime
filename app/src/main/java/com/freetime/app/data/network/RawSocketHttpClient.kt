package com.freetime.app.data.network

import android.os.Build
import com.freetime.app.data.models.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.IOException
import java.net.Socket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Raw socket HTTP client that bypasses all HTTP client implementations
 * This is necessary for Android 14+ which wraps HttpURLConnection with OkHttp
 */
object RawSocketHttpClient {
    private var requestTimeout: Int = 30000 // Increased to 30 seconds for more reliable network handling
    private var isCancelled = false
    
    /**
     * Create a trust manager that accepts all certificates (for development with self-signed certs)
     */
    private fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
        }
    }
    
    /**
     * Create an SSL context that accepts all certificates
     */
    private fun createInsecureSSLContext(): SSLContext {
        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, arrayOf<TrustManager>(createTrustAllManager()), java.security.SecureRandom())
        return sslContext
    }
    /**
     * Set timeout for socket operations (in milliseconds)
     */
    fun setTimeoutMs(timeoutMs: Int) {
        requestTimeout = timeoutMs
    }
    
    /**
     * Cancel current request
     */
    fun cancelRequest() {
        isCancelled = true
    }
    
    /**
     * Reset cancellation flag
     */
    fun resetCancellation() {
        isCancelled = false
    }
    
    /**
     * Direct connection attempt (no fallback)
     */
    private fun tryDirect(
        host: String,
        port: Int,
        block: (String, Int) -> String
    ): String {
        android.util.Log.d("RawSocketHttpClient", "Attempting connection to $host:$port")
        return block(host, port)
    }
    
    /**
     * Make a raw HTTP POST request using Socket (suspending version)
     * MUST run on Dispatchers.IO to avoid NetworkOnMainThreadException on Android 14+
     */
    suspend fun post(urlString: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            if (isCancelled) throw java.io.InterruptedIOException("Request cancelled")
            postSync(urlString, jsonBody, headers)
        }
    }
    
    /**
     * Synchronous version of post - do not call directly from main thread
     */
    private fun postSync(urlString: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        if (isCancelled) throw java.io.InterruptedIOException("Request cancelled")
        
        android.util.Log.d("RawSocketHttpClient", "Making raw socket POST request to: $urlString (timeout: ${requestTimeout}ms)")
        
        try {
            // Parse URL
            val url = java.net.URL(urlString)
            val host = url.host
            val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
            val path = if (url.path.isEmpty()) "/" else url.path + (if (url.query != null) "?${url.query}" else "")
            
            android.util.Log.d("RawSocketHttpClient", "Connecting to $host:$port, path=$path")
            
            // Try with fallback to IP if domain fails
            return tryDirect(host, port) { actualHost, actualPort ->
                makePostRequest(actualHost, actualPort, path, jsonBody, headers)
            }
        } catch (e: Exception) {
            android.util.Log.e("RawSocketHttpClient", "Error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Make the actual POST request on a socket
     */
    private fun makePostRequest(
        host: String,
        port: Int,
        path: String,
        jsonBody: String,
        headers: Map<String, String>
    ): String {
        // Create socket - use SSLSocket for HTTPS (port 443)
        val isHttps = port == 443
        val socket: Socket = if (isHttps) {
            android.util.Log.d("RawSocketHttpClient", "Creating SSLSocket for HTTPS connection")
            // Use insecure SSL context to accept self-signed certificates (development)
            val sslContext = createInsecureSSLContext()
            val sslSocketFactory = sslContext.socketFactory
            val sslSocket = sslSocketFactory.createSocket() as SSLSocket
            sslSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
            sslSocket
        } else {
            android.util.Log.d("RawSocketHttpClient", "Creating plain Socket for HTTP connection")
            Socket()
        }
        
        val socketAddress = java.net.InetSocketAddress(host, port)
        // Connect with 15s timeout, read timeout with 25s (giving more time for response)
        socket.connect(socketAddress, 15000)
        socket.soTimeout = 25000 // Read timeout set higher to wait for complete response
        
        try {
            // Build HTTP request
            val request = buildHttpRequest("POST", path, host, port, jsonBody, headers)
            android.util.Log.d("RawSocketHttpClient", "Sending request:\n${request.take(200)}...")
            
            // Send request with explicit flush
            val outputStream = socket.outputStream
            outputStream.write(request.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            
            // Read response with proper header/body separation
            val inputStream = socket.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            
            // Read status line (first line of HTTP response)
            var statusLine: String? = reader.readLine()
            if (statusLine == null) {
                throw IOException("Empty response from server")
            }
            android.util.Log.d("RawSocketHttpClient", "Status line: $statusLine")
            
            // Read headers
            var contentLength = 0
            var isChunked = false
            val headerLines = mutableListOf<String>()
            var line: String? = reader.readLine()
            
            while (line != null && line.isNotEmpty()) {
                headerLines.add(line)
                when {
                    line.startsWith("Content-Length:", ignoreCase = true) -> {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                    line.startsWith("Transfer-Encoding:", ignoreCase = true) -> {
                        isChunked = line.contains("chunked", ignoreCase = true)
                    }
                }
                line = reader.readLine()
            }
            
            android.util.Log.d("RawSocketHttpClient", "Headers: ${headerLines.size}, ContentLength: $contentLength")
            
            // Read body based on content-length or chunked encoding
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                // Read exactly contentLength bytes with retry logic
                val charArray = CharArray(contentLength)
                var totalRead = 0
                val startTime = System.currentTimeMillis()
                val readTimeout = 25000L // Max time to wait for complete body
                
                while (totalRead < contentLength) {
                    // Check for timeout
                    if (System.currentTimeMillis() - startTime > readTimeout) {
                        android.util.Log.w("RawSocketHttpClient", "Timeout reading Content-Length body: got $totalRead/$contentLength bytes after ${System.currentTimeMillis() - startTime}ms")
                        break
                    }
                    
                    try {
                        val charsToRead = contentLength - totalRead
                        val charsRead = reader.read(charArray, totalRead, charsToRead)
                        
                        if (charsRead == -1) {
                            // EOF reached before reading all bytes - may happen with streaming
                            android.util.Log.w("RawSocketHttpClient", "EOF before reading all $contentLength bytes, got $totalRead")
                            break
                        }
                        
                        totalRead += charsRead
                        android.util.Log.d("RawSocketHttpClient", "Read $charsRead chars, total: $totalRead/$contentLength")
                        
                    } catch (e: SocketTimeoutException) {
                        // Socket read timeout - this is expected when waiting for more data
                        android.util.Log.d("RawSocketHttpClient", "Socket read timeout while reading body at $totalRead/$contentLength bytes - retrying...")
                        // Don't break, just retry - socket will timeout again if server is really done
                    } catch (e: Exception) {
                        android.util.Log.w("RawSocketHttpClient", "Exception reading body at $totalRead/$contentLength: ${e.message}")
                        break
                    }
                }
                
                if (totalRead > 0) {
                    bodyBuilder.append(charArray, 0, totalRead)
                }
                android.util.Log.d("RawSocketHttpClient", "Finished reading Content-Length body: $totalRead/$contentLength bytes")
                
            } else if (isChunked) {
                // Read chunked encoding (simple implementation)
                var chunkLine: String? = reader.readLine()
                while (chunkLine != null && chunkLine.isNotEmpty()) {
                    val chunkSize = chunkLine.trim().toIntOrNull(16) ?: 0
                    if (chunkSize == 0) break
                    
                    val chunkData = CharArray(chunkSize)
                    reader.read(chunkData)
                    bodyBuilder.append(chunkData)
                    reader.readLine() // read CRLF after chunk
                    chunkLine = reader.readLine()
                }
            } else {
                // No Content-Length and not chunked - read until EOF with timeout
                // This is common for streaming responses or Connection: close
                val buf = CharArray(4096)
                var charsRead: Int
                val startTime = System.currentTimeMillis()
                val maxWaitTime = 25000L // Max time to wait for complete body
                var totalChars = 0
                var consecutiveTimeouts = 0
                
                while (true) {
                    // Check for overall timeout
                    val elapsedTime = System.currentTimeMillis() - startTime
                    if (elapsedTime > maxWaitTime) {
                        android.util.Log.w("RawSocketHttpClient", "Timeout reading body without Content-Length after ${elapsedTime}ms, got $totalChars chars")
                        break
                    }
                    
                    try {
                        charsRead = reader.read(buf)
                        
                        if (charsRead == -1) {
                            // EOF reached - this is normal end of response
                            android.util.Log.d("RawSocketHttpClient", "EOF reached after reading $totalChars chars")
                            break
                        }
                        
                        bodyBuilder.append(buf, 0, charsRead)
                        totalChars += charsRead
                        consecutiveTimeouts = 0 // Reset timeout counter on successful read
                        
                        android.util.Log.d("RawSocketHttpClient", "Read $charsRead chars, total: $totalChars")
                        
                    } catch (e: SocketTimeoutException) {
                        // Socket timeout - might be waiting for more data or server closing connection
                        consecutiveTimeouts++
                        
                        if (totalChars > 0) {
                            // We have data, so timeout might mean server is done
                            android.util.Log.d("RawSocketHttpClient", "Socket timeout while reading (timeout #$consecutiveTimeouts), have $totalChars chars - likely EOF from server")
                            if (consecutiveTimeouts >= 1) {
                                // One timeout with data is enough to assume EOF
                                break
                            }
                        } else if (consecutiveTimeouts >= 3) {
                            // Multiple timeouts with no data means server isn't responding
                            android.util.Log.w("RawSocketHttpClient", "Multiple socket timeouts with no data received")
                            break
                        }
                        
                        android.util.Log.d("RawSocketHttpClient", "Socket timeout #$consecutiveTimeouts, retrying...")
                        // Continue - the socket will timeout again if there's no more data
                        
                    } catch (e: Exception) {
                        // Any other exception, log and break
                        android.util.Log.w("RawSocketHttpClient", "Exception reading body: ${e.message}")
                        break
                    }
                }
                
                android.util.Log.d("RawSocketHttpClient", "Finished reading body without Content-Length: $totalChars chars")
            }
            
            val body = bodyBuilder.toString().trim()
            android.util.Log.d("RawSocketHttpClient", "Response body: ${body.take(100)}...")
            
            return body
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                android.util.Log.w("RawSocketHttpClient", "Error closing socket: ${e.message}")
            }
        }
    }
    
    /**
     * Build raw HTTP request
     */
    private fun buildHttpRequest(
        method: String,
        path: String,
        host: String,
        port: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("$method $path HTTP/1.1\r\n")
        sb.append("Host: $host${if (port != 80 && port != 443) ":$port" else ""}\r\n")
        sb.append("Content-Type: application/json\r\n")
        sb.append("Content-Length: ${body.length}\r\n")
        sb.append("Connection: close\r\n")
        sb.append("User-Agent: FreeTimeApp/1.0 Android/${android.os.Build.VERSION.RELEASE}\r\n")
        
        // Add custom headers
        for ((key, value) in headers) {
            sb.append("$key: $value\r\n")
        }
        
        sb.append("\r\n")
        if (body.isNotEmpty()) {
            sb.append(body)
        }
        
        return sb.toString()
    }
    
    /**
     * Make a raw HTTP GET request using Socket (suspending version)
     */
    suspend fun get(urlString: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            getSync(urlString, headers)
        }
    }
    
    /**
     * Synchronous version of GET - do not call directly from main thread
     */
    private fun getSync(urlString: String, headers: Map<String, String> = emptyMap()): String {
        android.util.Log.d("RawSocketHttpClient", "Making raw socket GET request to: $urlString")
        
        try {
            val url = java.net.URL(urlString)
            val host = url.host
            val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
            val path = if (url.path.isEmpty()) "/" else url.path + (if (url.query != null) "?${url.query}" else "")
            
            // Try with fallback to IP if domain fails
            return tryDirect(host, port) { actualHost, actualPort ->
                makeGetRequest(actualHost, actualPort, path, headers)
            }
        } catch (e: Exception) {
            android.util.Log.e("RawSocketHttpClient", "GET Error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Make the actual GET request on a socket
     */
    private fun makeGetRequest(
        host: String,
        port: Int,
        path: String,
        headers: Map<String, String>
    ): String {
        // Create socket with connect timeout
        val socket = Socket()
        val socketAddress = java.net.InetSocketAddress(host, port)
        socket.connect(socketAddress, (requestTimeout / 2).coerceAtLeast(2500))
        socket.soTimeout = (requestTimeout / 2).coerceAtLeast(2500)
        
        try {
            val request = buildHttpRequest("GET", path, host, port, "", headers)
            
            // Send request with explicit flush
            val outputStream = socket.outputStream
            outputStream.write(request.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
            
            // Read response with proper header/body separation
            val inputStream = socket.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            
            // Read status line
            var statusLine: String? = reader.readLine()
            if (statusLine == null) {
                throw IOException("Empty response from server")
            }
            android.util.Log.d("RawSocketHttpClient", "GET Status line: $statusLine")
            
            // Read headers
            var contentLength = 0
            var isChunked = false
            var line: String? = reader.readLine()
            
            while (line !=null && line.isNotEmpty()) {
                when {
                    line.startsWith("Content-Length:", ignoreCase = true) -> {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                    line.startsWith("Transfer-Encoding:", ignoreCase = true) -> {
                        isChunked = line.contains("chunked", ignoreCase = true)
                    }
                }
                line = reader.readLine()
            }
            
            // Read body
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val charsRead = reader.read(charArray, totalRead, contentLength - totalRead)
                    if (charsRead == -1) {
                        bodyBuilder.append(charArray, 0, totalRead)
                        break
                    }
                    totalRead += charsRead
                }
                if (totalRead == contentLength) {
                    bodyBuilder.append(charArray)
                }
            } else if (isChunked) {
                // Read chunked encoding
                var chunkLine: String? = reader.readLine()
                while (chunkLine != null && chunkLine.isNotEmpty()) {
                    val chunkSize = chunkLine.trim().toIntOrNull(16) ?: 0
                    if (chunkSize == 0) break
                    
                    val chunkData = CharArray(chunkSize)
                    reader.read(chunkData)
                    bodyBuilder.append(chunkData)
                    reader.readLine()
                    chunkLine = reader.readLine()
                }
            } else {
                // No Content-Length - read until EOF with timeout
                val buf = CharArray(1024)
                var charsRead: Int
                val startTime = System.currentTimeMillis()
                val maxWaitTime = this.requestTimeout.toLong()
                
                while (true) {
                    if (System.currentTimeMillis() - startTime > maxWaitTime) {
                        android.util.Log.w("RawSocketHttpClient", "Timeout reading GET body without Content-Length")
                        break
                    }
                    
                    try {
                        charsRead = reader.read(buf)
                        if (charsRead == -1) break
                        bodyBuilder.append(buf, 0, charsRead)
                    } catch (e: java.net.SocketTimeoutException) {
                        android.util.Log.d("RawSocketHttpClient", "Socket timeout on GET (expected for Connection: close)")
                        break
                    }
                }
            }
            
            val body = bodyBuilder.toString().trim()
            android.util.Log.d("RawSocketHttpClient", "GET Response: ${body.take(100)}...")
            return body
        } finally {
            socket.close()
        }
    }
    
    /**
     * Make a raw HTTP PUT request using Socket (suspending version)
     */
    suspend fun put(urlString: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            putSync(urlString, jsonBody, headers)
        }
    }
    
    /**
     * Synchronous version of PUT - do not call directly from main thread
     */
    private fun putSync(urlString: String, jsonBody: String, headers: Map<String, String> = emptyMap()): String {
        android.util.Log.d("RawSocketHttpClient", "Making raw socket PUT request to: $urlString")
        
        try {
            val url = java.net.URL(urlString)
            val host = url.host
            val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
            val path = if (url.path.isEmpty()) "/" else url.path + (if (url.query != null) "?${url.query}" else "")
            
            // Try with fallback to IP if domain fails
            return tryDirect(host, port) { actualHost, actualPort ->
                makePutRequest(actualHost, actualPort, path, jsonBody, headers)
            }
        } catch (e: Exception) {
            android.util.Log.e("RawSocketHttpClient", "PUT Error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Make the actual PUT request on a socket
     */
    private fun makePutRequest(
        host: String,
        port: Int,
        path: String,
        jsonBody: String,
        headers: Map<String, String>
    ): String {
        val socket = Socket(host, port)
        socket.soTimeout = 30000
        
        try {
            val request = buildHttpRequest("PUT", path, host, port, jsonBody, headers)
            val writer = PrintWriter(socket.outputStream, true)
            writer.write(request)
            writer.flush()
            
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            val response = StringBuilder()
            var line: String? = reader.readLine()
            
            var contentLength = 0
            while (line != null && line.isNotEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
                response.append(line).append("\n")
                line = reader.readLine()
            }
            
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                val charsRead = reader.read(charArray)
                if (charsRead > 0) {
                    bodyBuilder.append(charArray, 0, charsRead)
                }
            } else {
                while (reader.readLine()?.also { bodyBuilder.append(it).append("\n") } != null) {
                    // Keep reading
                }
            }
            
            val body = bodyBuilder.toString().trim()
            android.util.Log.d("RawSocketHttpClient", "PUT Response: ${body.take(100)}...")
            return body
        } finally {
            socket.close()
        }
    }
    
    /**
     * Make a raw HTTP DELETE request using Socket (suspending version)
     */
    suspend fun delete(urlString: String, headers: Map<String, String> = emptyMap()): String {
        return withContext(Dispatchers.IO) {
            deleteSync(urlString, headers)
        }
    }
    
    /**
     * Synchronous version of DELETE - do not call directly from main thread
     */
    private fun deleteSync(urlString: String, headers: Map<String, String> = emptyMap()): String {
        android.util.Log.d("RawSocketHttpClient", "Making raw socket DELETE request to: $urlString")
        
        try {
            val url = java.net.URL(urlString)
            val host = url.host
            val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
            val path = if (url.path.isEmpty()) "/" else url.path + (if (url.query != null) "?${url.query}" else "")
            
            // Try with fallback to IP if domain fails
            return tryDirect(host, port) { actualHost, actualPort ->
                makeDeleteRequest(actualHost, actualPort, path, headers)
            }
        } catch (e: Exception) {
            android.util.Log.e("RawSocketHttpClient", "DELETE Error: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Make the actual DELETE request on a socket
     */
    private fun makeDeleteRequest(
        host: String,
        port: Int,
        path: String,
        headers: Map<String, String>
    ): String {
        val socket = Socket(host, port)
        socket.soTimeout = 30000
        
        try {
            val request = buildHttpRequest("DELETE", path, host, port, "", headers)
            val writer = PrintWriter(socket.outputStream, true)
            writer.write(request)
            writer.flush()
            
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            val response = StringBuilder()
            var line: String? = reader.readLine()
            
            var contentLength = 0
            while (line != null && line.isNotEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
                response.append(line).append("\n")
                line = reader.readLine()
            }
            
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                val charsRead = reader.read(charArray)
                if (charsRead > 0) {
                    bodyBuilder.append(charArray, 0, charsRead)
                }
            } else {
                while (reader.readLine()?.also { bodyBuilder.append(it).append("\n") } != null) {
                    // Keep reading
                }
            }
            
            val body = bodyBuilder.toString().trim()
            android.util.Log.d("RawSocketHttpClient", "DELETE Response: ${body.take(100)}...")
            return body
        } finally {
            socket.close()
        }
    }
}

/**
 * Extension function to make POST with HttpResponse wrapper
 */
suspend fun RawSocketHttpClient.postResponse(
    urlString: String,
    jsonBody: String,
    headers: Map<String, String> = emptyMap()
): HttpResponse {
    return try {
        val body = this.post(urlString, jsonBody, headers)
        HttpResponse(statusCode = 200, body = body, headers = emptyMap())
    } catch (e: Exception) {
        HttpResponse(statusCode = 500, body = e.message ?: "Unknown error", headers = emptyMap())
    }
}

/**
 * Extension function to make GET with HttpResponse wrapper
 */
suspend fun RawSocketHttpClient.getResponse(
    urlString: String,
    headers: Map<String, String> = emptyMap()
): HttpResponse {
    return try {
        val body = this.get(urlString, headers)
        HttpResponse(statusCode = 200, body = body, headers = emptyMap())
    } catch (e: Exception) {
        HttpResponse(statusCode = 500, body = e.message ?: "Unknown error", headers = emptyMap())
    }
}

/**
 * Extension function to make PUT with HttpResponse wrapper
 */
suspend fun RawSocketHttpClient.putResponse(
    urlString: String,
    jsonBody: String,
    headers: Map<String, String> = emptyMap()
): HttpResponse {
    return try {
        val body = this.put(urlString, jsonBody, headers)
        HttpResponse(statusCode = 200, body = body, headers = emptyMap())
    } catch (e: Exception) {
        HttpResponse(statusCode = 500, body = e.message ?: "Unknown error", headers = emptyMap())
    }
}

/**
 * Extension function to make DELETE with HttpResponse wrapper
 */
suspend fun RawSocketHttpClient.deleteResponse(
    urlString: String,
    headers: Map<String, String> = emptyMap()
): HttpResponse {
    return try {
        val body = this.delete(urlString, headers)
        HttpResponse(statusCode = 200, body = body, headers = emptyMap())
    } catch (e: Exception) {
        HttpResponse(statusCode = 500, body = e.message ?: "Unknown error", headers = emptyMap())
    }
}
