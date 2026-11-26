# Keep JavaScript bridge methods: their names are called from the local WebView UI.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.realworldgta.MainActivity$NativeBridge { *; }

# LiteRT-LM exposes its Java API to native inference code. Keep JNI-facing wrappers stable.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
