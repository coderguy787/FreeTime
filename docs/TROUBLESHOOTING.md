# 🔧 SecureChat Troubleshooting Guide

**Last Updated:** February 2026 | **Status:** ✅ Comprehensive Solutions

---

## 🆘 Quick Diagnosis

| Symptom | Solution |
|---------|----------|
| **App won't build** | → Check Gradle sync, JDK version |
| **Can't connect to server** | → Verify IP, check firewall, test API |
| **Messages not sending** | → Check network, verify token, check logs |
| **App crashes on startup** | → Check for null pointer, memory issue |
| **Database errors** | → Clear app data, check Room migrations |
| **2FA not working** | → Check time sync, token validity |
| **Dark theme not showing** | → Clear cache, rebuild, check Theme.kt |

---

## 🔨 Build Issues

### Gradle Sync Failed

**Problem:** "Gradle sync failed"

**Solutions:**
```bash
# 1. Clean gradle cache
./gradlew clean

# 2. Rebuild
./gradlew build

# 3. Check Java version
java -version  # Should be 17+

# 4. Clear Android Studio cache
# File > Invalidate Caches > Invalidate and Restart

# 5. Update dependencies
./gradlew --refresh-dependencies

# 6. Check gradle.properties
# Verify all required properties are set
```

### Compilation Error: "Cannot find symbol"

**Problem:** Cannot find MainActivity, R.id.*, etc.

**Solutions:**
```bash
# Missing imports
# Add: import com.freetime.app.R

# Rebuild resources
./gradlew clean buildDebug

# Check layout files exist
# app/src/main/res/layout/activity_main.xml

# Invalidate cache
# File > Invalidate Caches > Invalidate and Restart
```

### Dependency Conflict

**Problem:** "Duplicate class java.util.*, javax.*"

**Solutions:**
```gradle
// In build.gradle, remove duplicate dependencies
android {
    configurations {
        all*.exclude group: 'com.android.support', module: 'support-annotations'
    }
}
```

### APK Build Failed

**Problem:** "Build failed with exception"

**Solutions:**
```bash
# 1. Check NDK requirement
# Project Structure > SDK Location > NDK version

# 2. Fix ProGuard issues
# buildTypes > release > minifyEnabled false  # Temporarily

# 3. Increase heap size
# gradle.properties:
# org.gradle.jvmargs=-Xmx4096m

# 4. Check for 64-bit libraries
./gradlew build --info | grep "64-bit"
```

---

## 📱 App Runtime Issues

### App Crashes on Startup

**Problem:** App force closes immediately

**Solutions:**

1. **Check crash logs:**
```bash
adb logcat | grep "AndroidRuntime"
# Look for "Exception", "Error", "Crash"
```

2. **Common causes and fixes:**
```kotlin
// Null pointer exception
if (data != null) {  // Check before using
    useData(data)
}

// Missing permissions
// AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />

// Incorrect Activity name
// Check AndroidManifest.xml entry
<activity android:name=".MainActivity" />

// Theme not found
// Check res/values/colors.xml exists
```

3. **Debug using breakpoints:**
- Set breakpoint in MainActivity.onCreate()
- Run app in debug mode
- Step through code to find issue

### App Slow or Laggy

**Problem:** UI stuttering, janky animations

**Solutions:**

```kotlin
// 1. Check for main thread blocking
// ✅ Good - Use coroutines
viewModelScope.launch {
    val data = repository.fetchData()  // Async
    updateUI(data)
}

// ❌ Bad - Blocks UI thread
val data = repository.fetchData()  // Blocking
updateUI(data)

// 2. Use LazyColumn instead of Column
// ✅ Good - Only renders visible items
LazyColumn {
    items(1000) { index ->
        ItemRow(data[index])
    }
}

// ❌ Bad - Renders all items
Column {
    data.forEach { item ->
        ItemRow(item)
    }
}

// 3. Profile with Android Profiler
// Android Studio > View > Tool Windows > Profiler
```

### App Won't Respond to Input

**Problem:** Buttons don't work, app feels frozen

**Solutions:**

```bash
# 1. Check if main thread blocked
adb shell dumpsys meminfo | grep "freetime"

# 2. Look for infinite loops
# Search code for "while(true)" or "for(;;)"

# 3. Check for ANR (Application Not Responding) logs
adb logcat | grep "ANR"

# 4. Add timeouts to network calls
// Retrofit client
OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

---

## 🌐 Network & Connectivity Issues

### Can't Connect to Server

**Problem:** App can't reach server

**Solutions:**

**1. Verify Server is Running:**
```bash
# SSH to server
ssh user@debian-ip

# Check if ports are listening
netstat -tlnp | grep -E '8081|8080|9080'

# Expected output:
# LISTEN 8081 (API)
# LISTEN 8080 (WebSocket)
# LISTEN 9080 (Peer)
```

**2. Test API Connectivity:**
```bash
# From your computer
curl -X GET http://YOUR_SERVER_IP:8081/api/verify

# Should return JSON (even if error)
# If no response: firewall blocking
```

**3. Check gradle.properties:**
```gradle
# Verify IP is correct
SERVER_HOST=192.168.1.100  # Replace with actual IP
SERVER_PORT=8081
```

**4. Fix on Emulator:**
```bash
# Emulator can't reach 192.168.x.x
# Use 10.0.2.2 instead (emulator's localhost)

# gradle.properties
SERVER_HOST=10.0.2.2
SERVER_PORT=8081
```

**5. Firewall Issues:**
```bash
# On Debian server
sudo ufw status
# If inactive, enable:
sudo ufw enable

# Allow ports
sudo ufw allow 8081/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 9080/tcp
```

### Slow Network Requests

**Problem:** API calls take too long

**Solutions:**

```kotlin
// 1. Add timeouts
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

// 2. Enable compression
okHttpClient.interceptors().add { chain ->
    chain.request().newBuilder()
        .addHeader("Accept-Encoding", "gzip")
        .build()
}

// 3. Check network
// Settings > About > Network info
// Verify: WiFi connected, signal strong

// 4. Monitor via Android Profiler
// Profile > Network > Watch for spikes
```

### WebSocket Connection Issues

**Problem:** Messages not arriving, WebSocket won't connect

**Solutions:**

```javascript
// Server-side check
// Check console for connection errors
io.on('connection', (socket) => {
    console.log('Client connected:', socket.id);
    
    socket.on('disconnect', (reason) => {
        console.log('Client disconnected:', reason);
    });
});
```

```bash
# Test WebSocket
wscat -c ws://YOUR_SERVER_IP:8080
# Should connect without errors

# If fails: server not running or firewall blocking
```

---

## 💾 Database Issues

### Room Database Errors

**Problem:** "DatabaseException", "Migration failed"

**Solutions:**

```kotlin
// 1. Clear app data (dev only)
// Settings > Apps > SecureChat > Storage > Clear Storage
// Or:
adb shell pm clear com.freetime.app

// 2. Check database version
// Increment version in @Database
@Database(
    entities = [...],
    version = 2  // Increment from 1
)

// 3. Add migration if structure changed
val migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN newColumn TEXT")
    }
}

// 4. Debug database file
adb pull /data/data/com.freetime.app/databases/securechat_db
# Open with DB Browser for SQLite
```

### Encryption/Decryption Failed

**Problem:** "IOException", "Failed to decrypt"

**Solutions:**

```kotlin
// 1. Verify key exists
try {
    val key = getKey("chat_key_$chatId")
    if (key == null) {
        // Generate new key
        val newKey = generateKey("chat_key_$chatId")
    }
} catch (e: KeyStoreException) {
    Log.e("Encryption", "Key error", e)
}

// 2. Check data integrity
// If file corrupted, try downloading again from server

// 3. Log encryption details (dev only)
Log.d("Encryption", "Key size: ${key.encoded.size}")
Log.d("Encryption", "Algorithm: ${key.algorithm}")
```

---

## 🔐 Authentication Issues

### Login Failed

**Problem:** "Invalid credentials", can't login

**Solutions:**

```bash
# 1. Verify credentials are correct
# Username: correct?
# Password: no caps lock?

# 2. Check if user exists
# Server: Check MongoDB
mongo
> use freetime
> db.users.find({ username: "yourname" })

# 3. Password reset
# Delete user from database and re-register
> db.users.deleteOne({ username: "yourname" })
```

### 2FA Code Not Accepted

**Problem:** "Invalid OTP code", 2FA failing

**Solutions:**

```bash
# 1. Check time sync on device
# Settings > System > Date & time
# Enable automatic date & time

# 2. Check time on server
date -u  # Should match device

# 3. TOTP codes expire fast
# Code valid ~90 seconds total
# If slow: use new code immediately

# 4. Verify 2FA is setup
# Check database:
mongo
> db.users.findOne({ username: "yourname" })
> printjson(db.users.findOne({ username: "yourname" }))
# Should have "totp_secret" field
```

### JWT Token Issues

**Problem:** "Invalid token", "Token expired"

**Solutions:**

```bash
# 1. Check token expiry
# Tokens valid 24 hours
# Re-login after expiry

# 2. Verify token on server
# Decode JWT:
echo $TOKEN | cut -d '.' -f 2 | base64 -d

# 3. Check token format
# Should be: "Authorization: Bearer <token>"
# NOT: "Authorization: <token>"

# 4. Token tampered with
# Doesn't match signature = rejected
```

---

## 🎨 UI Issues

### Dark Theme Not Applied

**Problem:** App shows light theme instead of dark

**Solutions:**

```bash
# 1. Verify Theme.kt applied
# MainActivity or Application class should have:
@Composable
override fun onCreate() {
    super.onCreate()
    setContent {
        SecureChatTheme {  // Must wrap content
            Navigation()
        }
    }
}

# 2. Clear cache
# File > Invalidate Caches > Invalidate and Restart

# 3. Rebuild APK
./gradlew clean assembleDebug

# 4. Check system theme
# Device Settings > Display > Dark theme
# Should be enabled for dark mode
```

### Animations Janky or Flashing

**Problem:** UI animations stutter, flashing effects

**Solutions:**

```kotlin
// 1. Check animation duration
val animationSpec = tween<Float>(
    durationMillis = 300,  // 300ms (not too fast)
    easing = EaseInOutCubic
)

// 2. Avoid repeated animations
// ❌ Bad - Recomposes and re-animates constantly
var counter by mutableStateOf(0)
Counter(counter)  // Parent recomposition triggers

// ✅ Good - Only animate specific component
@Composable
fun AnimatedCounter(counter: Int) {
    val animValue by animateIntAsState(counter)
    Text(animValue.toString())
}

// 3. Profile performance
// Android Studio > Profiler > Frame rate
// Should be 60fps consistently
```

---

## 📊 Performance Issues

### High Memory Usage

**Problem:** App uses lots of RAM, crashes with OOM

**Solutions:**

```kotlin
// 1. Profile memory
// Android Studio > Profiler > Memory > Record

// 2. Fix memory leaks
// Don't keep references to Context
class MyClass(private val context: Context)  // ❌ Leak
class MyClass(private val weakContext: WeakReference<Context>)  // ✅ Safe

// 3. Clear collections
// Don't keep unlimited message lists
var messages = mutableListOf<Message>()  // ❌ Grows forever
var messages = mutableListOf<Message>().take(100)  // ✅ Limited

// 4. Increase heap size
// gradle.properties
org.gradle.jvmargs=-Xmx4096m
```

### Battery Drain

**Problem:** App drains battery quickly

**Solutions:**

```kotlin
// 1. Stop continuous network calls
// Bad: Poll server every second
viewModelScope.launch {
    while (true) {
        fetchMessages()
        delay(1000)
    }
}

// Good: Use WebSocket (event-driven)
socket.on("message:received") { message ->
    handleMessage(message)
}

// 2. Use Location/Sensors wisely
// Only enable when needed
// Disable on pause

// 3. Monitor with Battery Historian
// Android Studio > Profiler > Battery
```

---

## 📞 Support & Debugging

### Enable Verbose Logging

```kotlin
// In Application class
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
    // All Timber.d(), Timber.e() calls logged
}

// Use everywhere
Timber.d("Debug: %s", message)
Timber.e(exception, "Error occurred")
```

### Collect Debug Information

```bash
# Gather these if reporting issue:

# 1. App version
adb shell dumpsys package com.freetime.app | grep versionName

# 2. Android version
adb shell getprop ro.build.version.release

# 3. Device name
adb shell getprop ro.product.model

# 4. Logs
adb logcat -d > logs.txt

# 5. Database (encrypted, for your analysis only)
adb pull /data/data/com.freetime.app/databases/

# 6. Crash report
# Android Studio > Logcat > Select "Crash"
```

### Report a Bug

Include:
- [ ] Android version
- [ ] Device/emulator model
- [ ] Steps to reproduce
- [ ] Expected behavior
- [ ] Actual behavior
- [ ] Error logs/crashes
- [ ] Screenshot (if UI issue)

---

## ✅ Verification Checklist

After troubleshooting:

- [ ] App builds without errors
- [ ] App starts without crashes
- [ ] Can login successfully
- [ ] 2FA works (if enabled)
- [ ] Messages send and receive
- [ ] Dark theme displays correctly
- [ ] Animations smooth (60fps)
- [ ] No memory leaks
- [ ] No battery drain
- [ ] Server connection stable

---

## 🆘 Still Not Working?

1. Check [DEPLOYMENT.md](DEPLOYMENT.md) - Setup issue?
2. Check [SECURITY.md](SECURITY.md) - Encryption issue?
3. Check [DEVELOPMENT.md](DEVELOPMENT.md) - Code issue?
4. Review server logs: `pm2 logs`
5. Check database: Open with MongoDB Compass
6. Review [ARCHITECTURE.md](ARCHITECTURE.md) - System issue?

---

**Status:** ✅ Comprehensive Solutions | **Date:** February 2026
