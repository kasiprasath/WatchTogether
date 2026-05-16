# WatchTogether ProGuard Rules

# Keep NanoHTTPD
-keep class fi.iki.elonen.** { *; }

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep WebSocket classes
-keep class fi.iki.elonen.NanoWSD** { *; }

# Keep data models
-keep class com.watchtogether.data.model.** { *; }

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
