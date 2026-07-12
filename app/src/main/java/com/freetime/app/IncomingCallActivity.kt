@file:Suppress("DEPRECATION")

package com.freetime.app

import android.content.*
import android.os.*
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.ui.theme.FreeTimeTheme
import com.freetime.app.notifications.NotificationHelper
import com.freetime.app.services.CallManagementService
import com.freetime.app.services.CallForegroundService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * Incoming Call Activity - Full-screen call display with WhatsApp-style features
 */
class IncomingCallActivity : ComponentActivity() {
    private val AUDIO_PERMISSION_REQUEST = 4123
    private var callService: CallManagementService? = null
    private var isBound = false
    private var isCallHandled = false  // ✅ CRITICAL: Prevent double-taps on accept/decline

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CallManagementService.CallBinder
            callService = binder.getService()
            isBound = true
            android.util.Log.d("IncomingCall", "✅ Bound to CallManagementService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            callService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ ENHANCED: Full-screen and lock screen display (WhatsApp-style)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // ✅ CRITICAL: Do NOT play ringtone here - it's handled by FCM notification
        // This prevents duplicate ringtones when notification already plays one
        // The CallForegroundService handles ringtone for app-active calls only via WebSocket
        val foregroundServiceIntent = Intent(this, CallForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(foregroundServiceIntent)
        } else {
            startService(foregroundServiceIntent)
        }

        // Bind to service
        val intentService = Intent(this, CallManagementService::class.java)
        bindService(intentService, serviceConnection, Context.BIND_AUTO_CREATE)

        // Support both UPPERCASE (local) and camelCase (FCM/System) keys
        val callerName = intent.getStringExtra("CALLER_NAME") ?: intent.getStringExtra("callerName") ?: "Someone"
        val callerId = intent.getStringExtra("CALLER_ID") ?: intent.getStringExtra("callerId") ?: ""
        val callId = intent.getStringExtra("CALL_ID") ?: intent.getStringExtra("callId") ?: ""
        val callType = intent.getStringExtra("CALL_TYPE") ?: intent.getStringExtra("callType") ?: "audio"
        val callerAvatarUrl = intent.getStringExtra("CALLER_AVATAR") ?: intent.getStringExtra("callerAvatar") ?: ""
        val offerSdp = intent.getStringExtra("OFFER_SDP") ?: intent.getStringExtra("offerSdp") ?: ""  // ✅ CRITICAL: Extract offer from intent
        
        android.util.Log.d("IncomingCall", "📞 Activity launched - callId: $callId, offer available: ${offerSdp.isNotEmpty()}, offer length: ${offerSdp.length}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            }
        }

        // ✅ UNIFIED: Ringtone managed by CallStateManager (no duplicate ringtones)
        // Vibration only for user feedback
        vibrateIncomingCall()

        setContent {
            FreeTimeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0E27)) {
                    val callerBitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    val elapsedSeconds = remember { mutableStateOf(0) }

                    // ✅ NEW: Timer for ringing duration
                    LaunchedEffect(Unit) {
                        var seconds = 0
                        while (true) {
                            kotlinx.coroutines.delay(1000)
                            seconds++
                            elapsedSeconds.value = seconds
                            // Auto-decline after 60 seconds
                            if (seconds >= 60) {
                                android.util.Log.d("IncomingCall", "⏱️ Call timeout after 60 seconds")
                            }
                        }
                    }

                    LaunchedEffect(callerAvatarUrl) {
                        android.util.Log.d("IncomingCall", "🎬 Avatar LaunchedEffect triggered - URL: $callerAvatarUrl, empty: ${callerAvatarUrl.isNullOrEmpty()}")
                        
                        if (!callerAvatarUrl.isNullOrEmpty()) {
                            try {
                                val bmp = withContext(Dispatchers.IO) {
                                    try {
                                        val baseUrl = com.freetime.app.BuildConfig.MAIN_SERVER_URL.trimEnd('/')
                                        android.util.Log.d("IncomingCall", "📍 Base URL: $baseUrl")
                                        android.util.Log.d("IncomingCall", "📍 Avatar URL from intent: $callerAvatarUrl")
                                        
                                        val finalUrl = if (callerAvatarUrl.startsWith("http")) {
                                            android.util.Log.d("IncomingCall", "✅ Avatar URL is absolute (starts with http)")
                                            callerAvatarUrl
                                        } else {
                                            val constructedUrl = if (callerAvatarUrl.startsWith("/")) {
                                                "$baseUrl$callerAvatarUrl"
                                            } else {
                                                "$baseUrl/$callerAvatarUrl"
                                            }
                                            android.util.Log.d("IncomingCall", "🔗 Constructed avatar URL: $constructedUrl")
                                            constructedUrl
                                        }
                                        
                                        android.util.Log.d("IncomingCall", "🌐 Loading avatar from: $finalUrl")
                                        val url = java.net.URL(finalUrl)
                                        
                                        // ✅ FIX: Use SSL bypass for self-signed certificates (same as API service)
                                        val conn = if (finalUrl.startsWith("https")) {
                                            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                                                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                                                override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                                                override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
                                            })
                                            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                                            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                                            val httpsConn = url.openConnection() as javax.net.ssl.HttpsURLConnection
                                            httpsConn.sslSocketFactory = sslContext.socketFactory
                                            httpsConn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                                            httpsConn
                                        } else {
                                            url.openConnection()
                                        }
                                        conn.connectTimeout = 5000
                                        conn.readTimeout = 5000
                                        android.util.Log.d("IncomingCall", "✅ Connection established, reading input stream...")
                                        
                                        val input = conn.getInputStream()
                                        android.util.Log.d("IncomingCall", "✅ Input stream opened, decoding bitmap...")
                                        
                                        val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                                        input.close()
                                        
                                        if (bitmap != null) {
                                            android.util.Log.d("IncomingCall", "✅ Avatar bitmap loaded successfully - size: ${bitmap.width}x${bitmap.height}")
                                        } else {
                                            android.util.Log.e("IncomingCall", "❌ BitmapFactory.decodeStream returned null")
                                        }
                                        bitmap
                                    } catch (e: Exception) {
                                        android.util.Log.e("IncomingCall", "❌ Failed to load avatar: ${e.message} - Class: ${e.javaClass.simpleName}")
                                        e.printStackTrace()
                                        null
                                    }
                                }
                                callerBitmapState.value = bmp
                                if (bmp != null) {
                                    android.util.Log.d("IncomingCall", "✅ Avatar state updated with bitmap")
                                } else {
                                    android.util.Log.d("IncomingCall", "⚠️ Avatar state updated with null bitmap - will show initials")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("IncomingCall", "❌ Avatar loading exception: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            android.util.Log.d("IncomingCall", "⚠️ Avatar URL is null or empty - will show initials")
                        }
                    }

                    IncomingCallScreen(
                        callerName = callerName,
                        callType = callType,
                        callerAvatar = callerBitmapState.value,
                        elapsedSeconds = elapsedSeconds.value,
                        onAccept = {
                            // ✅ CRITICAL: Prevent double-taps on accept button
                            if (isCallHandled) {
                                Toast.makeText(this@IncomingCallActivity, "Call already being processed...", Toast.LENGTH_SHORT).show()
                                return@IncomingCallScreen
                            }

                            // ✅ NEW: Check for audio permission before accepting
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(this@IncomingCallActivity, "Please grant microphone permission to answer calls", Toast.LENGTH_LONG).show()
                                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
                                    return@IncomingCallScreen
                                }
                            }

                            isCallHandled = true
                            
                            vibrateSuccess()
                            // ✅ UNIFIED: Ringtone stopped by CallStateManager
                            NotificationHelper.cancelCallNotification(this@IncomingCallActivity, callerId)
                            
                            val service = callService
                            if (service == null) {
                                Toast.makeText(this@IncomingCallActivity, "Call service not ready", Toast.LENGTH_SHORT).show()
                                isCallHandled = false  // Reset on error
                                return@IncomingCallScreen
                            }

                            service.initializeIncomingCallSession(
                                callId = callId,
                                remoteUserId = callerId,
                                remoteUsername = callerName,
                                callType = if (callType.equals("video", ignoreCase = true)) 
                                    CallManagementService.CallType.VIDEO 
                                else 
                                    CallManagementService.CallType.AUDIO,
                                offerSdp = offerSdp  // ✅ CRITICAL: Pass offer to service
                            )
                            
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    android.util.Log.d("IncomingCall", "📞 CRITICAL: Starting answer creation for callId=$callId")
                                    
                                    val answerSdp = withContext(Dispatchers.IO) {
                                        service.createAndSendAnswer(callId)
                                    }
                                    android.util.Log.d("IncomingCall", "✅ Answer SDP created: ${answerSdp?.take(50)}...")                                    
                                    if (answerSdp != null) {
                                        val apiService = com.freetime.app.api.FreeTimeApiService(this@IncomingCallActivity)
                                        
                                        android.util.Log.d("IncomingCall", "📤 Sending answer to API server...")
                                        val answerResult = apiService.answerCall(callId, answerSdp)
                                        
                                        answerResult.fold(
                                            onSuccess = { result ->
                                                android.util.Log.d("IncomingCall", "✅ API accepted answer: $result")
                                                withContext(Dispatchers.Main) {
                                                    val navIntent = Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                                                        putExtra("CHAT_ID", callerId)
                                                        putExtra("CALL_ID", callId)
                                                        putExtra("ACCEPT_CALL", true)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    }
                                                    android.util.Log.d("IncomingCall", "🚀 Launching MainActivity with call state")
                                                    startActivity(navIntent)
                                                    finish()
                                                }
                                            },
                                            onFailure = { error ->
                                                android.util.Log.e("IncomingCall", "❌ API answer failed: ${error.message}", error)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@IncomingCallActivity, "Failed to send answer: ${error.message}", Toast.LENGTH_LONG).show()
                                                    isCallHandled = false
                                                    finish()
                                                }
                                            }
                                        )
                                    } else {
                                        android.util.Log.e("IncomingCall", "❌ createAndSendAnswer returned null")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@IncomingCallActivity, "Failed to create answer - null response", Toast.LENGTH_SHORT).show()
                                            isCallHandled = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("IncomingCall", "❌ CRITICAL ERROR in accept flow: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@IncomingCallActivity, "Error accepting call: ${e.message}", Toast.LENGTH_LONG).show()
                                        isCallHandled = false
                                        finish()
                                    }
                                }
                            }
                        },
                        onDecline = {
                            // ✅ CRITICAL: Prevent double-taps on decline button
                            if (isCallHandled) {
                                finish()
                                return@IncomingCallScreen
                            }
                            isCallHandled = true
                            
                            vibrateCancel()
                            // ✅ UNIFIED: Ringtone stopped by CallStateManager
                            NotificationHelper.cancelCallNotification(this@IncomingCallActivity, callerId)
                            callService?.rejectCall(callId)
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * ✅ CRITICAL FIX: Handle new intents when activity is already running
     * This ensures notification extras are properly received even with singleTask launchMode
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            android.util.Log.d("IncomingCall", "🔔 onNewIntent called - updated intent extras")
            
            // Log the new intent data
            val newCallId = intent.getStringExtra("CALL_ID") ?: ""
            val newOfferSdp = intent.getStringExtra("OFFER_SDP") ?: ""
            android.util.Log.d("IncomingCall", "📞 New intent received - callId: $newCallId, offer available: ${newOfferSdp.isNotEmpty()}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ UNIFIED: Ringtone cleanup handled by CallStateManager
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        vibrator?.cancel()
        
        // ✅ Stop foreground service when call ends
        val foregroundServiceIntent = Intent(this, CallForegroundService::class.java)
        stopService(foregroundServiceIntent)
    }

    private fun vibrateIncomingCall() {
        vibratePattern(longArrayOf(0, 500, 200, 500))
    }

    private fun vibrateSuccess() {
        vibratePattern(longArrayOf(0, 50, 30, 50))
    }

    private fun vibrateCancel() {
        vibratePattern(longArrayOf(0, 100, 50, 100))
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {}
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String, 
    callType: String, 
    callerAvatar: android.graphics.Bitmap? = null,
    elapsedSeconds: Int = 0,
    onAccept: () -> Unit, 
    onDecline: () -> Unit
) {
    // ✅ ENHANCED: Format time display
    val timeDisplay = if (elapsedSeconds > 0) {
        String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    } else {
        "00:00"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0E27), Color(0xFF1a1f3a), Color(0xFF0A0E27)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            verticalArrangement = Arrangement.spacedBy(24.dp), 
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // ✅ Caller Avatar - Large and prominent (WhatsApp-style)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF9D4EDD).copy(alpha = 0.4f), Color(0xFF5A189A).copy(alpha = 0.2f)))),
                contentAlignment = Alignment.Center
            ) {
                if (callerAvatar != null) {
                    androidx.compose.foundation.Image(
                        bitmap = callerAvatar.asImageBitmap(), 
                        contentDescription = "Caller Avatar", 
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        callerName.take(1).uppercase(), 
                        color = Color(0xFF00FFFF), 
                        fontSize = 56.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Text("Incoming ${callType.uppercase()} Call", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Text(callerName, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            
            // ✅ NEW: Timer display
            Text(
                timeDisplay, 
                color = Color(0xFF00DDAA), 
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text("● Ringing...", color = Color(0xFF00DDAA), style = MaterialTheme.typography.labelSmall)
            
            Spacer(modifier = Modifier.height(80.dp))
            
            // ✅ ENHANCED: Prominent Accept/Decline buttons (WhatsApp-style)
            Row(
                horizontalArrangement = Arrangement.spacedBy(72.dp), 
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth()
            ) {
                // Decline button - Red
                IconButton(
                    onClick = onDecline, 
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFFF5252), CircleShape)
                ) { 
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
                }
                
                // Accept button - Green
                IconButton(
                    onClick = onAccept, 
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                ) { 
                    Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
                }
            }
        }
    }
}
