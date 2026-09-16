pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Vendored prebuilt AARs (sherpa-onnx on-device ASR runtime), resolved as
        // a normal dependency so library modules can consume + repackage them.
        flatDir { dirs("${rootDir}/data/asr-local/libs") }
    }
}

rootProject.name = "Vaani"

include(":app")
include(":core:designsystem")
include(":core:common")
include(":core:ui")
include(":domain")
include(":data:notes")
include(":data:vector")
include(":data:database")
include(":data:audio")
include(":data:pipeline")
include(":data:asr-local")
include(":data:sarvam")
include(":data:work")
include(":data:ai")
include(":feature:library")
include(":feature:note")
include(":feature:onboarding")
include(":feature:device")
include(":data:device")
include(":feature:chat")
include(":feature:search")
include(":feature:tasks")
include(":feature:settings")
