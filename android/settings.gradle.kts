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
    }
}

rootProject.name = "Vaani"

include(":app")
include(":core:designsystem")
include(":core:common")
include(":core:ui")
include(":domain")
include(":data:notes")
include(":data:database")
include(":data:audio")
include(":data:ai")
include(":feature:library")
include(":feature:note")
include(":feature:onboarding")
include(":feature:device")
include(":feature:chat")
include(":feature:search")
include(":feature:tasks")
include(":feature:settings")
