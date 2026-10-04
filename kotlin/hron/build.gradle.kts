import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    @OptIn(ExperimentalAbiValidation::class) abiValidation()
    // Apps on Kotlin 2.2 can use the library, and it does not raise their standard library.
    coreLibrariesVersion = "2.2.0"
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_2
        apiVersion = KotlinVersion.KOTLIN_2_2
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
        // Like javac's --release: without it, calls to JDK methods newer than 17 compile.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    val spec = rootDir.resolve("../spec")
    systemProperty("hron.spec", spec.canonicalPath)
    // Read through a system property, so Gradle would not otherwise see a spec change.
    inputs.dir(spec).withPathSensitivity(PathSensitivity.RELATIVE)
    failOnNoDiscoveredTests = true
    // -Phron.testJdk=17 runs the tests on the oldest JDK the bytecode targets.
    providers.gradleProperty("hron.testJdk").orNull?.let { version ->
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(version)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Release builds sign; others build the same artifacts unsigned, which Maven Central rejects.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates("io.hron", "hron-kotlin", version.toString())
    pom {
        name = "hron-kotlin"
        description =
            "Human-readable cron - scheduling expressions that read like English and convert to and" +
                " from cron"
        url = "https://github.com/simpllyf/hron"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                name = "Prasanna Ram Venkatachalam"
                url = "https://prasrvenkat.dev"
            }
        }
        scm {
            connection = "scm:git:git://github.com/simpllyf/hron.git"
            developerConnection = "scm:git:ssh://github.com:simpllyf/hron.git"
            url = "https://github.com/simpllyf/hron/tree/main"
        }
    }
}
