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
-dontwarn com.android.volley.toolbox.**
-dontwarn com.facebook.infer.**

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
-dontwarn okhttp3.**

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

# andrroid v4 v7
-dontwarn android.support.v4.**
-dontwarn android.support.v7.**

# rx3
-dontwarn sun.misc.**
-keep class io.reactivex.rxjava3.** { *; }
-keep interface io.reactivex.rxjava3.** { *; }
-dontwarn io.reactivex.rxjava3.**

#mongodb
-dontwarn javax.**
-dontwarn java.lang.management.**
-dontwarn io.netty.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn org.xerial.snappy.**

-keep class javax.** { *; }
-keep class java.lang.management.** { *; }
-keep class io.netty.** { *; }
-keep class org.ietf.jgss.** { *; }
-keep class org.slf4j.** { *; }
-keep class org.xerial.snappy.** { *; }

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

-keep class xyropencc.** { *; }
-keep interface xyropencc.** { *; }
-dontwarn xyropencc.**
-keep class com.xyrlsz.xcimocob.utils.dmzj.protos.** { *; }
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.facebook.common.internal.VisibleForTesting
-dontwarn java.awt.Color
-dontwarn java.awt.Font
-dontwarn java.awt.Point
-dontwarn java.awt.Rectangle
-dontwarn org.javamoney.moneta.Money
-dontwarn org.joda.time.DateTime
-dontwarn org.joda.time.DateTimeZone
-dontwarn org.joda.time.Duration
-dontwarn org.joda.time.Instant
-dontwarn org.joda.time.LocalDate
-dontwarn org.joda.time.LocalDateTime
-dontwarn org.joda.time.LocalTime
-dontwarn org.joda.time.Period
-dontwarn org.joda.time.ReadablePartial
-dontwarn org.joda.time.format.DateTimeFormat
-dontwarn org.joda.time.format.DateTimeFormatter
-dontwarn springfox.documentation.spring.web.json.Json
-dontwarn org.glassfish.jersey.**

-dontwarn com.alibaba.fastjson.*