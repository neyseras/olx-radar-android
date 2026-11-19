# Proguard rules for OlxRadar

# WebView JS Interface (critical for ChromiumBridge)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Room Database and Data Models
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class ua.magerko.olxradar.data.model.** { *; }
-keep class ua.magerko.olxradar.data.local.** { *; }

# Glide Image Loading
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-dontwarn com.bumptech.glide.**

# Preserve annotations and line numbers for crash reporting
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
