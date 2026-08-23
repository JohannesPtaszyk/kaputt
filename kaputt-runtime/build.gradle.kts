plugins {
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

kotlin {
    jvmToolchain(21)

    // The runtime is loaded into the consumer's test JVM, so it targets the
    // oldest bytecode a Kotlin project is likely to run on rather than the
    // toolchain used to build it.
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            // kotlin-test on the jvm target maps @kotlin.test.Test onto JUnit;
            // the runner only needs the annotation/launcher types at compile
            // time; at runtime they come from the module under test.
            compileOnly("junit:junit:4.13.2")
            compileOnly("org.junit.platform:junit-platform-launcher:1.14.4")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
