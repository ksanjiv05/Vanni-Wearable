plugins {
    id("vaani.android.application")
    id("vaani.android.compose")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.app"

    defaultConfig {
        applicationId = "com.vaani.app"
        versionCode = 1
        versionName = "0.1.0-milestoneA"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data:notes"))
    implementation(project(":data:database"))
    implementation(project(":data:audio"))
    implementation(project(":data:pipeline"))
    implementation(project(":data:asr-local"))
    implementation(project(":data:ai"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))
    implementation(project(":feature:library"))
    implementation(project(":feature:note"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:device"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:search"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}
