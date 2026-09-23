import java.util.Properties

plugins {
    id("vaani.android.application")
    id("vaani.android.compose")
    id("vaani.android.hilt")
}

android {
    namespace = "com.vaani.app"

    signingConfigs {
        create("release") {
            // Loaded from keystore/keystore.properties (gitignored — never committed).
            val props = Properties().apply {
                val propsFile = rootProject.file("../keystore/keystore.properties")
                if (propsFile.exists()) propsFile.inputStream().use { load(it) }
            }
            val propsFile = rootProject.file("../keystore/keystore.properties")
            if (propsFile.exists()) {
                storeFile = rootProject.file("../keystore/vaani-release.jks")
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.vaani.app"
        versionCode = 1
        versionName = "0.1.0-milestoneA"
        // arm64-v8a only: every on-device model requires arm64 (device gating rejects
        // 32-bit), and MediaPipe LiteRT is arm64 in practice — shipping armeabi-v7a
        // just bloats the APK and gives 32-bit users a dead-end install.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Shrink + obfuscate: the app pulls in sherpa-onnx, MediaPipe, Room, Hilt,
            // WorkManager, Media3, commons-compress — R8 meaningfully cuts APK size.
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation(project(":data:vector"))
    implementation(project(":data:audio"))
    implementation(project(":data:pipeline"))
    implementation(project(":data:asr-local"))
    implementation(project(":data:sarvam"))
    implementation(project(":data:work"))
    implementation(project(":data:ai"))
    implementation(project(":data:device"))
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
}
