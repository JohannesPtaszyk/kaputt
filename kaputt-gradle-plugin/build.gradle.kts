plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-gradle-plugin`
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation(project(":kaputt-compiler-plugin"))
    implementation(project(":kaputt-runtime"))

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        register("mutation") {
            id = "dev.pott.kaputt"
            implementationClass = "dev.pott.kaputt.gradle.MutationGradlePlugin"
            displayName = "kaputt — Kotlin Mutation Testing"
            description =
                "Runs the Kotlin test suites once per generated mutant and reports survivors."
        }
    }
}

// Single-sources the version: the plugin resolves its compiler-plugin and
// runtime artifacts with the version it was built as (see gradle.properties).
val testKitClasspath: Configuration by configurations.creating

dependencies {
    testKitClasspath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testKitClasspath)
}

val generateVersionResource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version-resource")
    val versionValue = version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        outputDir
            .get()
            .file("dev.pott.kaputt.version")
            .asFile
            .writeText(versionValue)
    }
}

sourceSets.main {
    resources.srcDir(generateVersionResource)
}

tasks.test {
    // The generated project resolves the runtime and compiler-plugin artifacts
    // from the local repository, so they have to be there first.
    dependsOn(":kaputt-runtime:publishToMavenLocal", ":kaputt-compiler-plugin:publishToMavenLocal")
    systemProperty("mutation.test.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("mutation.test.examplesDir", rootProject.file("examples").absolutePath)
    // Without this the example tests keep a cached verdict when an example
    // changes, which is the one thing they exist to notice.
    inputs
        .dir(rootProject.file("examples"))
        .withPropertyName("examples")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
