pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hron-kotlin"

include(":hron")

// The Android tests need the Android SDK, so only `-Phron.android=true` includes them.
if (providers.gradleProperty("hron.android").orNull == "true") include(":android-test")

// The runner `just diff` builds, which other builds do not need.
if (providers.gradleProperty("hron.differential").orNull == "true") {
    include(":differential")
    project(":differential").projectDir = file("../tools/differential/runners/kotlin")
}
