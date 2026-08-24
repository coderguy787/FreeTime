# FreeTime App Hardened ProGuard Rules

# 1. Aggressive Obfuscation
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-repackageclasses ''

# 2. Obfuscate class and member names
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# 3. Only keep necessary entry points (Application, Activities, Services, etc.)
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 4. Keep WebRTC (Essential for calls)
-keep class org.webrtc.** { *; }

# 5. Keep Firebase (Essential for notifications)
-keep class com.google.firebase.** { *; }
-keep class com.firebase.** { *; }

# 6. Keep Compose (UI functionality)
-keep class androidx.compose.** { *; }
-keep @androidx.compose.** interface * { *; }

# 7. Don't warn about unused classes (common in large libs)
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn com.google.android.gms.**
-dontwarn kotlin.**

# 8. Obfuscate own code heavily (replace classes with generic names)
-keepnames class com.freetime.app.** { *; }
