plugins {
    id("vaani.android.library")
    id("vaani.android.compose")
}

android {
    namespace = "com.vaani.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
