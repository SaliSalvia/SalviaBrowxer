# Android default rules
-keep class androidx.** { *; }
-keep class com.google.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.Database { *; }
-keep class * extends androidx.room.Entity { *; }
-keep class * extends androidx.room.Dao { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# WebView
-keep class android.webkit.** { *; }

# Keep all Activities, Services, and BroadcastReceivers
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep all Parcelable classes
-keep public class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep R classes
-keep class **.R$* { *; }

# Keep all enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all Compose functions
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep all DataStore preferences
-keep class androidx.datastore.preferences.** { *; }

# Keep all WorkManager classes
-keep class androidx.work.** { *; }

# Hilt
-keep class com.google.dagger.hilt.** { *; }
-dontwarn com.google.dagger.hilt.**