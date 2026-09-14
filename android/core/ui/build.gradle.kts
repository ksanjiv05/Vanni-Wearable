plugins {
    id("vaani.android.library")
    id("vaani.android.compose")
}

android {
    namespace = "com.vaani.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.datetime)
}
