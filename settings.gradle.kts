pluginManagement {
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

rootProject.name = "payment-recorder"

// Pure Kotlin: parser, Jalali calendar, money, export. Shared with the future backend.
include(":core")

// Android app (lives under android/ to leave room for backend/ and web/).
include(":app")
project(":app").projectDir = file("android/app")
