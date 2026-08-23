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

rootProject.name = "jvm-junit6-app"
