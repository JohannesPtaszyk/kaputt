plugins {
    kotlin("multiplatform") version "2.4.10"
    id("dev.pott.kaputt")
}

kotlin {
    jvmToolchain(21)

    jvm()
    macosArm64()
    linuxX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

mutation {
    operators.addAll("conditional-boundary", "negate-conditional", "math", "empty-return")
    // Native targets are opt-in because every mutant reruns their test binary.
    // Add the one your host can run, for example:
    // targets.addAll("jvm", "macosArm64")
}
