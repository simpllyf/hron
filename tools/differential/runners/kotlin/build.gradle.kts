plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    // Spotless formats only files inside a project's own directory, so this one has its own.
    id("com.diffplug.spotless")
}

application { mainClass = "RunnerKt" }

// Built from kotlin/, so its output stays out of the runner's source directory.
layout.buildDirectory = rootDir.resolve("build/differential")

tasks.installDist { destinationDir = rootDir.resolve("../tools/differential/.build/kotlin") }

dependencies {
    implementation(project(":hron"))
    implementation(libs.jackson.databind)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
