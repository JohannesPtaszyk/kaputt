plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.4.10"
    id("dev.pott.kaputt")
}

android {
    namespace = "com.example.devices"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.devices"
        minSdk = 24
        targetSdk = 35
    }

    buildTypes {
        // A build type beyond debug and release, as most apps have.
        create("staging") { initWith(getByName("debug")) }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("free") { dimension = "tier" }
        create("paid") { dimension = "tier" }
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
    // Flavours and build types multiply into variants, so name the one that
    // carries the mutants. freeDebug or paidStaging work the same way.
    androidVariant.set("paidStaging")
}
