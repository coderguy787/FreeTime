package com.freetime.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.InetAddress

/**
 * Network Diagnostics Utility
 * Helps identify connectivity issues with the master server
 */
object NetworkDiagnostics {
    private const val TAG = "NetworkDiagnostics"
    
    /**
     * Check if device has internet connection
     */
    fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Get network connection type
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"
        
        return when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Test DNS resolution for a hostname
     */
    fun testDnsResolution(hostname: String): Boolean {
        return try {
            val address = InetAddress.getByName(hostname)
            Log.d(TAG, "DNS resolved $hostname -> ${address.hostAddress}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "DNS resolution failed for $hostname: ${e.message}")
            false
        }
    }
    
    /**
     * Test TCP connection to a server
     */
    fun testTcpConnection(hostname: String, port: Int, timeoutMs: Int = 5000): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(hostname, port), timeoutMs)
            socket.close()
            Log.d(TAG, "TCP connection successful to $hostname:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP connection failed to $hostname:$port: ${e.message}")
            false
        }
    }
    
    /**
     * Perform comprehensive network diagnostics
     */
    fun runFullDiagnostics(context: Context, serverUrl: String): String {
        val diagnostics = StringBuilder()
        diagnostics.append("Network Diagnostics Report\n")
        diagnostics.append("==================================================\n\n")
        
        // 1. Check internet connection
        val hasInternet = hasInternetConnection(context)
        diagnostics.append("Internet Connection: ${if (hasInternet) "Connected" else "Not connected"}\n")
        
        if (!hasInternet) {
            diagnostics.append("   Device has no internet connection\n")
            return diagnostics.toString()
        }
        
        // 2. Check network type
        val networkType = getNetworkType(context)
        diagnostics.append("Network Type: $networkType\n\n")
        
        // 3. Parse server URL
        val hostname = try {
            val url = java.net.URL(serverUrl)
            url.host
        } catch (e: Exception) {
            diagnostics.append("Invalid server URL: $serverUrl\n")
            return diagnostics.toString()
        }
        
        diagnostics.append("Server: $hostname\n")
        
        // 4. Test DNS resolution
        diagnostics.append("\n[1] DNS Resolution:\n")
        val dnsOk = testDnsResolution(hostname)
        diagnostics.append("   ${if (dnsOk) "OK" else "FAIL"} $hostname\n")
        
        if (!dnsOk) {
            diagnostics.append("   Cannot resolve domain name\n")
            diagnostics.append("   Possible causes:\n")
            diagnostics.append("   - Domain name is incorrect\n")
            diagnostics.append("   - DNS server is not responding\n")
            diagnostics.append("   - Internet connection is unstable\n")
            return diagnostics.toString()
        }
        
        // 5. Test TCP connections
        diagnostics.append("\n[2] TCP Connectivity:\n")
        val tcpPort80Ok = testTcpConnection(hostname, 80)
        diagnostics.append("   ${if (tcpPort80Ok) "OK" else "FAIL"} Port 80 (HTTP)\n")
        
        val tcpPort443Ok = testTcpConnection(hostname, 443)
        diagnostics.append("   ${if (tcpPort443Ok) "OK" else "FAIL"} Port 443 (HTTPS)\n")
        
        if (!tcpPort443Ok && !tcpPort80Ok) {
            diagnostics.append("   Cannot connect to server\n")
            diagnostics.append("   Possible causes:\n")
            diagnostics.append("   - Server is offline\n")
            diagnostics.append("   - Firewall is blocking connections\n")
            diagnostics.append("   - Wrong IP address or port\n")
            return diagnostics.toString()
        }
        
        // 6. Summary
        diagnostics.append("\nDiagnostics complete\n")
        diagnostics.append("Server appears to be reachable\n")
        
        return diagnostics.toString()
    }
}
