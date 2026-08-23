plugins {
    kotlin("jvm") version "2.4.10"
    id("dev.pott.kaputt")
}

dependencies {
    testImplementation(kotlin("test-junit"))
}

kotlin {
    jvmToolchain(21)
}

mutation {
    // The report is easier to read without literal mutations in a sample this
    // small; drop this line to see every operator.
    operators.addAll("conditional-boundary", "negate-conditional", "math", "empty-return")
}
