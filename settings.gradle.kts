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

rootProject.name = "Kinecare"
include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")

include(":feature:auth")
include(":feature:search")
include(":feature:professional-profile")
include(":feature:booking")
include(":feature:payment")
include(":feature:verification")
include(":feature:professional-panel")
include(":feature:reviews")
include(":feature:client-panel")
