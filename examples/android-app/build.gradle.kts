plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.4.10"
    id("dev.pott.kaputt")
}

android {
    namespace = "com.example.battery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.battery"
        minSdk = 24
        targetSdk = 35
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
    testImplementation(kotlin("test-junit"))
}

mutation {
    operators.addAll("conditional-boundary", "negate-conditional", "math", "empty-return")
}
