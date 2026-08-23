pluginManagement {
    includeBuild("../..")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

includeBuild("../..")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-flavors"
