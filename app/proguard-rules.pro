# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Developer\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-optimizationpasses 5

-keepattributes SourceFile, LineNumberTable

# fresco
# Keep our interfaces so they can be used by other ProGuard rules.
# See http://sourceforge.net/p/proguard/bugs/466/
-keep,allowobfuscation @interface com.facebook.common.internal.DoNotStrip
#-keep,allowobfuscation @interface com.facebook.soloader.DoNotOptimize

# Do not strip any method/class that is annotated with @DoNotStrip
-keep @com.facebook.common.internal.DoNotStrip class *
-keepclassmembers class * {
    @com.facebook.common.internal.DoNotStrip *;
}

# Do not strip any method/class that is annotated with @DoNotOptimize
#-keep @com.facebook.soloader.DoNotOptimize class *
#-keepclassmembers class * {
#    @com.facebook.soloader.DoNotOptimize *;
#}

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}


# Do not strip SoLoader class and init method
#-keep public class com.facebook.soloader.SoLoader {
#    public static void init(android.content.Context, int);
#}

-dontwarn okio.**
-dontwarn com.squareup.okhttp.**
-dontwarn okhttp3.**
-dontwarn javax.annotation.**

-keep class **$Properties
-keep class **$Properties{*;}

# ObjectBox
-keep class io.objectbox.** {
    *;
}
-keep class **_ {
    *;
}
-keep class * implements io.objectbox.converter.PropertyConverter {
    *;
}
-keepclassmembers class * {
    @io.objectbox.annotation.* *;
}

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# DataSync models (used by Gson via reflection)
-keep class com.xyrlsz.xcimocob.network.sync.DataSyncModels { *; }
-keep class com.xyrlsz.xcimocob.network.sync.DataSyncModels$* { *; }

# rhino
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-keep class org.mozilla.javascript.** { *; }

# jsoup
-keeppackagenames org.jsoup.nodes

# rx3
-keep class io.reactivex.rxjava3.** { *; }
-keep interface io.reactivex.rxjava3.** { *; }
-dontwarn io.reactivex.rxjava3.**

# guava
-dontwarn com.google.common.base.**
-keep class com.google.common.base.** {*;}
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** {*;}
-dontwarn com.google.j2objc.annotations.**
-keep class com.google.j2objc.annotations.** { *; }
-dontwarn java.lang.ClassValue
#-keep class java.lang.ClassValue { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
#-keep class org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement { *; }

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.facebook.common.internal.VisibleForTesting