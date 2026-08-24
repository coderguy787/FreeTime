package com.freetime.app.security

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.app.R

class BlockedActivity : ComponentActivity() {
    private val killHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // block screenshots of this screen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        HostileEnvironment.deploy()

        setContent {
            BlockedScreen()
        }

        // close the app after showing the message
        killHandler.postDelayed({
            HostileEnvironment.stop()
            finishAffinity()
            Runtime.getRuntime().exit(0)
        }, 5000)
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        HostileEnvironment.stop()
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        killHandler.removeCallbacksAndMessages(null)
    }
}

@Composable
private fun BlockedScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.roger),
                contentDescription = "ROGER",
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 32.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Nice try buddy",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = alpha),
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}
