plugins {
    id("vaani.android.library")
}

android {
    namespace = "com.vaani.core.common"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
