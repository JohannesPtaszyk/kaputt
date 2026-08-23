plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.4.10"
    id("dev.pott.kaputt")
}

android {
    namespace = "com.example.sync"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sync"
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

mutation {
    operators.addAll("conditional-boundary", "negate-conditional", "math", "empty-return")
    // Verifies every mutant with the instrumented tests, on a connected device.
    instrumentedTests.set(true)
}
