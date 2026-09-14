plugins {
    id("vaani.android.library")
    id("vaani.android.compose")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.feature.library"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.datetime)
}
