plugins {
    id("vaani.android.library")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.data.ai"
}

dependencies {
    implementation(libs.androidx.security.crypto)
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp.core)
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
