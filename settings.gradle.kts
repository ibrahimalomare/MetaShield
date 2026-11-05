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
        // Official Content Authenticity Initiative (CAI) C2PA Android SDK via JitPack
        maven { url = uri("https://jitpack.io") }
        // Optional GitHub Packages repository for org.contentauth:c2pa if GITHUB_TOKEN is configured
        maven {
            name = "GitHubPackagesContentAuth"
            url = uri("https://maven.pkg.github.com/contentauth/c2pa-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_USER") ?: ""
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "MetaShield"
include(":app")
