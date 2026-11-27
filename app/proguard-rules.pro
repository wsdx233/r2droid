# Preserve stack trace metadata for crash reports
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# WebView JS bridge methods & plugin runtime bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class top.wsdx233.r2droid.feature.plugin.PluginAndroidBridge { *; }
-keep class top.wsdx233.r2droid.feature.plugin.PluginWebBridge { *; }

# Termux JNI methods invoked from native libtermux.so
-keepclasseswithmembernames class com.termux.terminal.JNI {
    native <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class top.wsdx233.r2droid.**$$serializer { *; }
-keepclassmembers class top.wsdx233.r2droid.** {
    *** Companion;
}
-keepclasseswithmembers class top.wsdx233.r2droid.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# QuickJS JNI bindings
-keep class com.dokar.quickjs.** { *; }
