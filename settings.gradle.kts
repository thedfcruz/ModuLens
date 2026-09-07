pluginManagement {
    includeBuild("modulens")

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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ModuLens"

include(
    ":sample:app",
    ":sample:analytics",
    ":sample:feature:dashboard",
    ":sample:feature:settings",
    ":sample:core:data",
    ":sample:core:domain",
    ":sample:core:network",
    ":sample:core:ui",
    ":sample:shared",
)
