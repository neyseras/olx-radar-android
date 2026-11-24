# Proguard / R8 rules for OlxRadar

# BuildConfig & Keep annotations
-keep class ua.magerko.olxradar.BuildConfig { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# WebView JS Interface (critical for ChromiumBridge)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Services, Receivers, Workers (AndroidManifest / WorkManager reflection)
-keep class ua.magerko.olxradar.service.MonitorService
-keep class ua.magerko.olxradar.service.BootReceiver
-keep class ua.magerko.olxradar.service.NotificationActionReceiver
-keep class ua.magerko.olxradar.service.MonitorWatchdogWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room Database and Data Models
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class ua.magerko.olxradar.data.model.** { *; }
-keep class ua.magerko.olxradar.data.local.** { *; }
-keep class ua.magerko.olxradar.ai.AiCacheEntry { *; }

# Compose / Material3 / AndroidLiquidGlass shaders
-keep class io.github.kyant0.** { *; }
-dontwarn io.github.kyant0.**
-dontwarn android.graphics.RuntimeShader

# Glide & Coil Image Loading
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**
-dontwarn coil.**

# Coroutines & WorkManager
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Preserve annotations and line numbers for crash reporting
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
