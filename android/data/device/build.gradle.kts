plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.data.device"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:audio"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    implementation(libs.okhttp.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
