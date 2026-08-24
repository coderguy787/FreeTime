package com.freetime.app.data.network

import android.os.Build
import com.freetime.app.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object ApiClient {
    private var baseUrl: String = try {
        BuildConfig.API_BASE_URL
    } catch (e: Exception) {
        "https://example.com/"
    }

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    // trust all certs, server uses self-signed
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    fun getInstance(): ApiService {
        if (apiService == null) {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val httpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }

            httpClientBuilder.addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val requestWithAgent = original.newBuilder()
                    .header("User-Agent", "FreeTimeApp/1.0 Android/${Build.VERSION.RELEASE}")
                    .build()
                chain.proceed(requestWithAgent)
            })

            httpClientBuilder.addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val androidVersion = Build.VERSION.RELEASE
                val sdkInt = Build.VERSION.SDK_INT
                val versionName = when {
                    sdkInt >= 34 -> "Android 14+ (UPSIDE_DOWN_CAKE)"
                    sdkInt >= 33 -> "Android 13 (TIRAMISU)"
                    sdkInt >= 31 -> "Android 12 (S)"
                    sdkInt >= 30 -> "Android 11 (R)"
                    sdkInt >= 29 -> "Android 10 (Q)"
                    sdkInt >= 28 -> "Android 9 (Pie)"
                    sdkInt >= 26 -> "Android 8 (Oreo)"
                    else -> "Android ${Build.VERSION.RELEASE}"
                }

                android.util.Log.d("ApiClient", "╔════════════════════════════════════════════════════════════")
                android.util.Log.d("ApiClient", "║ REQUEST")
                android.util.Log.d("ApiClient", "║ Android: $versionName (API $sdkInt, Release $androidVersion)")
                android.util.Log.d("ApiClient", "║ Method: ${request.method}")
                android.util.Log.d("ApiClient", "║ URL: $url")
                android.util.Log.d("ApiClient", "║ Protocol: HTTPS/TLS (Encrypted)")
                android.util.Log.d("ApiClient", "╚════════════════════════════════════════════════════════════")

                try {
                    val response = chain.proceed(request)
                    android.util.Log.d("ApiClient", "╔════════════════════════════════════════════════════════════")
                    android.util.Log.d("ApiClient", "║ RESPONSE SUCCESS")
                    android.util.Log.d("ApiClient", "║ Code: ${response.code}")
                    android.util.Log.d("ApiClient", "║ Message: ${response.message}")
                    android.util.Log.d("ApiClient", "║ Android: $versionName (API $sdkInt)")
                    android.util.Log.d("ApiClient", "╚════════════════════════════════════════════════════════════")
                    response
                } catch (e: Exception) {
                    android.util.Log.e("ApiClient", "╔════════════════════════════════════════════════════════════")
                    android.util.Log.e("ApiClient", "║ NETWORK ERROR")
                    android.util.Log.e("ApiClient", "║ Type: ${e.javaClass.simpleName}")
                    android.util.Log.e("ApiClient", "║ Message: ${e.message}")
                    android.util.Log.e("ApiClient", "║ Android: $versionName (API $sdkInt)")
                    android.util.Log.e("ApiClient", "║ URL: $url")
                    android.util.Log.e("ApiClient", "╚════════════════════════════════════════════════════════════")
                    throw e
                }
            }

            val httpClient = httpClientBuilder.build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
        }
        return apiService!!
    }

    fun setBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        // reset retrofit so the new url is used
        apiService = null
        retrofit = null
    }

    fun getBaseUrl(): String = baseUrl

    fun getTrustAllOkHttpClient(
        connectTimeoutSeconds: Long = 30,
        readTimeoutSeconds: Long = 30,
        writeTimeoutSeconds: Long = 30
    ): OkHttpClient {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun configureWithIP(ipAddress: String, port: Int = 80) {
        val protocol = if (port == 443) "https" else "http"
        setBaseUrl("$protocol://$ipAddress:$port/")
    }
}
