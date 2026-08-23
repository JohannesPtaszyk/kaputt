pluginManagement {
    val kotlinVersion = file("gradle/libs.versions.toml")
        .readLines()
        .first { it.startsWith("kotlin = ") }
        .substringAfter('"')
        .substringBefore('"')

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kaputt"

include(":kaputt-runtime")
include(":kaputt-compiler-plugin")
include(":kaputt-gradle-plugin")
