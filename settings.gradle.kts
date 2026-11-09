pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
// Keep the Gradle identity consistent with the folder and Android Studio module IDs.
rootProject.name = "anotherlauncher"
include(":app")
