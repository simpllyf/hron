plugins { alias(libs.plugins.android.application) }

// An app that runs the spec's shared cases on a device, with Android's own java.time and tz data.
android {
    namespace = "io.hron.kotlin.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.hron.kotlin.android"
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("androidTest") {
        kotlin.directories += "../hron/src/test/kotlin/io/hron/kotlin/spec"
        assets.directories += "../../spec"
    }
}

dependencies {
    implementation(project(":hron"))
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.jackson.databind)
}
