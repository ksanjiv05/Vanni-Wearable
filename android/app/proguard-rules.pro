# Keep rules for release (R8 minify + resource shrink).

# --- Native JNI boundaries: classes/methods called from C++ must not be renamed ---
# sherpa-onnx (ASR) — JNI looks up class + method names reflectively.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# MediaPipe LLM / tasks-genai (on-device enrichment) — JNI + protobuf-lite.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# --- Kotlinx serialization (if used by any DTO) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# --- Room / Hilt / WorkManager are handled by their own bundled consumer rules ---
# (androidx ships -keep rules via AAR consumerProguardFiles); nothing extra needed.

# Keep model/DTO enum values used by name (AiBackend, Priority, etc. via valueOf).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
