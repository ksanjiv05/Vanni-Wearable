plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.data.asr.local"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // sherpa-onnx (k2-fsa) provides the on-device ASR runtime. It is not on a
    // stable Maven Central coordinate; integrate via one of (ADR-001 §7):
    //   1. jitpack:  implementation("com.github.k2-fsa:sherpa-onnx-android:<ver>")
    //   2. local AAR: build with android/build-android.sh arm64-v8a and
    //      flatDir/publishToMavenLocal, then depend on it here.
    // The native call is isolated in SherpaAsr.kt behind runCatching so this
    // module compiles and the engine degrades to ModelUnavailable until the
    // AAR + model are present. Uncomment when wiring the runtime:
    // implementation("com.github.k2-fsa:sherpa-onnx-android:1.10.34")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
