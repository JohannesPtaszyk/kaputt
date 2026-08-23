plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

val kotlinVersion = libs.versions.kotlin.get()

// Jars handed to the in-process compiler invoked by the tests: the fixture
// snippets compile against the stdlib and the mutation runtime.
val fixtureCompileClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    testImplementation(kotlin("test"))
    testImplementation(project(":kaputt-runtime"))
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    fixtureCompileClasspath(project(":kaputt-runtime"))
    fixtureCompileClasspath("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
}

tasks.test {
    dependsOn(tasks.jar)
    inputs.files(fixtureCompileClasspath)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dmutation.test.pluginJar=${tasks.jar.get().archiveFile.get().asFile.absolutePath}",
                "-Dmutation.test.fixtureClasspath=${fixtureCompileClasspath.asPath}",
            )
        },
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
