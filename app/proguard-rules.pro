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

# ── Sherpa-onnx (JNI native bridge) ──
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ── ONNX Runtime (JNI native bridge) ──
-keep class ai.onnxruntime.** { *; }

# ── ObjectBox ──
-keep class io.objectbox.** { *; }
-keep class com.alfredassistant.alfred_ai.db.** { *; }

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Keep Kotlin metadata for serialization ──
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations