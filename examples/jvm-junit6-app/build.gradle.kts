plugins {
    kotlin("jvm") version "2.4.10"
    id("dev.pott.kaputt")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

mutation {
    // Nothing here selects the framework: the fork uses whichever the test
    // classpath carries.
    // The report is easier to read without literal mutations in a sample this
    // small; drop this line to see every operator.
    operators.addAll("conditional-boundary", "negate-conditional", "math", "empty-return")
}
