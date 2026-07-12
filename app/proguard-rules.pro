# FreeTime App ProGuard Rules

# Keep all Java classes and methods
-keepclassmembers class * {
    public *;
    protected *;
}

# Keep all Kotlin metadata
-keepclassmembers class kotlin.** {
    *;
}

# Keep all Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.firebase.** { *; }
-keep class org.apache.** { *; }
-keep class org.bouncycastle.** { *; }

# Keep all Room database classes
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep all ViewModel classes
-keep class androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep all serialization classes
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Retrofit and OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.gson.** { *; }

# Keep Socket.IO
-keep class io.socket.** { *; }
-keep class engine.io.** { *; }

# Keep Dagger Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltComponent { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep @androidx.compose.** interface * { *; }

# Keep WebRTC
-keep class org.webrtc.** { *; }

# Performance optimizations
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Don't show ProGuard messages
-dontnote
