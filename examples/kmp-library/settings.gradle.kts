pluginManagement {
    includeBuild("../..")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("../..")

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "kmp-library"
