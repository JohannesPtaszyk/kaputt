plugins {
    id("com.diffplug.spotless") version "8.1.0" apply false
}

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            publications.withType(MavenPublication::class.java).configureEach {
                pom {
                    name.set("kaputt: ${project.name}")
                    description.set(
                        "Kotlin-Multiplatform-first mutation testing: an IR compiler plugin " +
                            "generating mutant schemata plus a Gradle plugin running the " +
                            "kotlin-test suites once per mutant.",
                    )
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("JohannesPtaszyk")
                            name.set("Johannes Ptaszyk")
                        }
                    }
                    url.set("https://github.com/JohannesPtaszyk/kaputt")
                    scm {
                        url.set("https://github.com/JohannesPtaszyk/kaputt")
                    }
                }
            }
        }
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
        kotlin {
            target("src/**/*.kt")
            ktlint()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }
}
