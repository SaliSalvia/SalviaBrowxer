# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# SalviaBrowxer
# ---------------------------------------------------------------------------
# WebView JavaScript bridges (NavaMediaBridge / NavaConsoleBridge) are anonymous
# objects whose @JavascriptInterface methods are only called reflectively by
# Chromium. They must keep their names and signatures.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# Readable crash stack traces for release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities/DAOs are generated and reflected on by name.
-keep class com.example.data.local.** { *; }

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.engine.DownloadUidtJobService { *; }
-keep class com.example.engine.DownloadActionReceiver { *; }

# OkHttp optional TLS providers.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
