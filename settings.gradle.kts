pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://maven.hygradle.dev") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.hygradle.settings") version "0.0.12"
}

rootProject.name = "EndgameAndQoL"

hygradle {
    hytale {
        version = "2026.03.26-89796e57b"
    }
}
