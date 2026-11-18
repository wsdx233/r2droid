pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "2.3.21"
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "r2pipe-kotlin"
