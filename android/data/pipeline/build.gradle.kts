plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.data.pipeline"
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
