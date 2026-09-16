plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vaani.data.sarvam"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
