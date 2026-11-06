plugins {
    alias(libs.plugins.android.application)
}

val classifierDigest = providers.gradleProperty("AQ_CLASSIFIER_SHA256").orElse("").get()
require(classifierDigest.isEmpty() || classifierDigest.matches(Regex("[a-fA-F0-9]{64}"))) { "AQ_CLASSIFIER_SHA256 must be a SHA-256 digest." }

android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.aquaquencherspos"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.aquaquencherspos"
        // Requirement B-05: Android 10 and above.
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CLASSIFIER_SHA256", "\"$classifierDigest\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":classifier-java"))
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests, so tests use the real one.
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
