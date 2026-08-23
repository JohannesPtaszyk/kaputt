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

rootProject.name = "jvm-junit4-app"
