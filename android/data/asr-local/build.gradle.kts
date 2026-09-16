plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.data.asr.local"

    defaultConfig {
        // sherpa-onnx ships native .so only for these ABIs; arm64-v8a is the
        // real target (ADR-001 gating requires it). Limiting keeps the APK sane.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // sherpa-onnx (k2-fsa) on-device ASR runtime — vendored prebuilt AAR
    // (official v1.13.8 release; classes.jar + jni/<abi>/*.so for all ABIs).
    // Resolved via the flatDir repo in settings.gradle.kts so the library
    // module can repackage its classes + native libs. Wired in SherpaAsr.kt.
    implementation(":sherpa-onnx-1.13.8@aar")

    // MediaPipe LLM Inference — on-device GGUF/.task runtime for enrichment.
    // Prebuilt AAR from Google Maven (bundles the native inference .so); wired
    // in MediaPipeLlm.kt behind LocalLlmEnricher.
    implementation(libs.mediapipe.tasks.genai)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
