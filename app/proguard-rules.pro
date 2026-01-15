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

-optimizationpasses 5
-dontwarn javax.lang.model.**
-dontwarn sun.misc.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn android.content.res.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class indi.dmzz_yyhyy.lightnovelreader.data.json.** { *; }
-keep,allowobfuscation,allowshrinking class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.** { *; }
-keepclassmembers class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.** { *; }
-keepnames class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.** { *; }
-keep,allowobfuscation,allowshrinking class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.json.** { *; }
-keepclassmembers class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.json.** { *; }
-keepnames class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.json.** { *; }
-keep,allowobfuscation,allowshrinking class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.exploration.** { *; }
-keepclassmembers class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.exploration.** { *; }
-keepnames class indi.dmzz_yyhyy.lightnovelreader.data.web.zaicomic.exploration.** { *; }
-keepclassmembernames class indi.dmzz_yyhyy.lightnovelreader.data.web.exploration.** { *; }
-keep,allowobfuscation,allowshrinking class indi.dmzz_yyhyy.lightnovelreader.data.update.** { *; }
-keepnames class indi.dmzz_yyhyy.lightnovelreader.data.update.** { *; }
-keepclassmembernames class indi.dmzz_yyhyy.lightnovelreader.data.update.** { *; }

-keepattributes Signature, *Annotation*, InnerClasses
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keepclassmembers,allowobfuscation,allowoptimization class <1> {
  <init>();
}
-keep class org.xmlpull.** { *; }
-keepclassmembers class org.xmlpull.** { *; }
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class indi.dmzz_yyhyy.lightnovelreader.**$$serializer { *; }
-keepclassmembers class indi.dmzz_yyhyy.lightnovelreader.** {
    *** Companion;
}
-keepclasseswithmembers class indi.dmzz_yyhyy.lightnovelreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.dom4j.**
-keep class org.dom4j.**{*;}
-keep interface org.dom4j.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn okhttp3.logging.**