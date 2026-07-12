package com.freetime.app.ui.composables

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.freetime.app.BuildConfig
import com.freetime.app.ui.components.CyberpunkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class GifResult(
    val id: String,
    val url: String,
    val previewUrl: String,
    val title: String
)

data class GifCategory(
    val name: String,
    val query: String,
    val icon: ImageVector
)

private val categories = listOf(
    GifCategory("Trending", "", Icons.Default.TrendingUp),
    GifCategory("Reactions", "reactions", Icons.Default.EmojiEmotions),
    GifCategory("Love", "love", Icons.Default.Favorite),
    GifCategory("Celebration", "celebration", Icons.Default.Star),
    GifCategory("Funny", "funny", Icons.Default.Whatshot),
)

@Composable
fun GifPickerDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGifSelected: (gifUrl: String, previewUrl: String) -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf(listOf<GifResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                gifs = fetchTrendingGifs(context)
                errorMessage = null
            } catch (e: Exception) {
                Log.e("GIF_PICKER", "Failed to fetch trending GIFs", e)
                errorMessage = "Failed to load GIFs"
            }
        }
        isLoading = false
        hasSearched = true
    }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory.query.isEmpty()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        searchQuery = ""
        withContext(Dispatchers.IO) {
            try {
                gifs = searchGifs(context, selectedCategory.query)
            } catch (e: Exception) {
                Log.e("GIF_PICKER", "Failed to search category", e)
                errorMessage = "Search failed"
            }
        }
        isLoading = false
        hasSearched = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop — fills entire area, dismisses on tap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        // Content panel — positioned at bottom, no clickable so children (TextField, GIF grid) work normally
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.BottomCenter),
            color = Color(0xFF0F0F1F),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "GIF Picker",
                            color = CyberpunkTheme.CyberCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tap a GIF to send",
                            color = CyberpunkTheme.GhostGray,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = CyberpunkTheme.GhostGray)
                    }
                }

                // Search bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f))
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { newValue ->
                            searchQuery = newValue
                            searchJob?.cancel()
                            if (newValue.isNotBlank()) {
                                searchJob = scope.launch {
                                    delay(400)
                                    isLoading = true
                                    errorMessage = null
                                    selectedCategory = categories.first()
                                    withContext(Dispatchers.IO) {
                                        try {
                                            gifs = searchGifs(context, newValue)
                                            errorMessage = null
                                        } catch (e: Exception) {
                                            Log.e("GIF_PICKER", "Search failed", e)
                                            errorMessage = "Search failed"
                                        }
                                    }
                                    isLoading = false
                                    hasSearched = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        singleLine = true,
                        placeholder = { Text("Search GIFs...", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = CyberpunkTheme.PrimaryPurple)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    selectedCategory = categories.first()
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        withContext(Dispatchers.IO) {
                                            try {
                                                gifs = fetchTrendingGifs(context)
                                                errorMessage = null
                                            } catch (e: Exception) {
                                                Log.e("GIF_PICKER", "Failed to load trending", e)
                                                errorMessage = "Failed to load GIFs"
                                            }
                                        }
                                        isLoading = false
                                        hasSearched = true
                                    }
                                }) {
                                    Icon(Icons.Default.Close, "Clear", tint = CyberpunkTheme.GhostGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        selectedCategory = categories.first()
                                        withContext(Dispatchers.IO) {
                                            try {
                                                gifs = searchGifs(context, searchQuery)
                                                errorMessage = null
                                            } catch (e: Exception) {
                                                Log.e("GIF_PICKER", "Search failed", e)
                                                errorMessage = "Search failed"
                                            }
                                        }
                                        isLoading = false
                                        hasSearched = true
                                    }
                                }
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = CyberpunkTheme.CyberCyan,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Categories row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        val isSelected = category == selectedCategory
                        Surface(
                            onClick = {
                                selectedCategory = category
                                searchQuery = ""
                            },
                            color = if (isSelected) CyberpunkTheme.PrimaryPurple.copy(alpha = 0.3f) else Color(0xFF1A1A2E),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CyberpunkTheme.PrimaryPurple else CyberpunkTheme.GhostGray.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    category.icon,
                                    null,
                                    tint = if (isSelected) CyberpunkTheme.CyberCyan else CyberpunkTheme.GhostGray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    category.name,
                                    color = if (isSelected) Color.White else CyberpunkTheme.GhostGray,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content area
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = CyberpunkTheme.CyberCyan,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Loading GIFs...",
                                    color = CyberpunkTheme.GhostGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "⚠️",
                                    fontSize = 36.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    errorMessage ?: "Something went wrong",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    onClick = {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            withContext(Dispatchers.IO) {
                                                gifs = fetchTrendingGifs(context)
                                            }
                                            isLoading = false
                                            hasSearched = true
                                        }
                                    },
                                    color = CyberpunkTheme.PrimaryPurple,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        "Retry",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                    gifs.isEmpty() && hasSearched -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No GIFs found",
                                    color = CyberpunkTheme.GhostGray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Try a different search term",
                                    color = CyberpunkTheme.GhostGray.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(gifs, key = { it.id }) { gif ->
                                Surface(
                                    modifier = Modifier
                                        .height(150.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onGifSelected(gif.url, gif.previewUrl)
                                            onDismiss()
                                        }
                                        .border(
                                            1.dp,
                                            CyberpunkTheme.PrimaryPurple.copy(alpha = 0.15f),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    color = Color(0xFF1A1A2E)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(gif.previewUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = gif.title,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (gif.title.isNotBlank() && gif.title != "GIF") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        Color.Black.copy(alpha = 0.6f),
                                                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    gif.title.take(40),
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val giphyClient: OkHttpClient by lazy {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
    })
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, SecureRandom())
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}

private fun fetchTrendingGifs(context: Context): List<GifResult> {
    val baseUrl = BuildConfig.MAIN_SERVER_URL.trimEnd('/')
    val request = Request.Builder()
        .url("$baseUrl/api/gifs/trending?limit=30&rating=g")
        .get()
        .build()
    return try {
        val response = giphyClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        parseGiphyResponse(body)
    } catch (e: Exception) {
        Log.e("GIF_PICKER", "Failed to fetch trending GIFs", e)
        throw e
    }
}

private fun searchGifs(context: Context, query: String): List<GifResult> {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val baseUrl = BuildConfig.MAIN_SERVER_URL.trimEnd('/')
    val request = Request.Builder()
        .url("$baseUrl/api/gifs/search?q=$encoded&limit=30&rating=g")
        .get()
        .build()
    return try {
        val response = giphyClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        parseGiphyResponse(body)
    } catch (e: Exception) {
        Log.e("GIF_PICKER", "Failed to search GIFs", e)
        throw e
    }
}

private fun parseGiphyResponse(json: String): List<GifResult> {
    val obj = JSONObject(json)
    val data = obj.optJSONArray("data") ?: JSONArray()
    val results = mutableListOf<GifResult>()

    for (i in 0 until data.length()) {
        val gif = data.getJSONObject(i)
        val id = gif.optString("id", "")
        val title = gif.optString("title", "GIF")
        val images = gif.optJSONObject("images") ?: continue
        val original = images.optJSONObject("original") ?: continue
        val url = original.optString("url", "")
        val preview = images.optJSONObject("fixed_height_small")?.optString("url", "")
            ?: images.optJSONObject("fixed_height")?.optString("url", "")
            ?: url

        if (id.isNotEmpty() && url.isNotEmpty()) {
            results.add(GifResult(id, url, preview, title))
        }
    }
    return results
}
