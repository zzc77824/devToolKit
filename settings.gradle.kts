import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "DevToolkit"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.0"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.20.0"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        // Local Ivy repository for IntelliJ Platform artifacts generated on the fly
        // (bundledModule:*, local plugins, etc.). It must come first so Gradle never
        // probes remote repositories for artifacts that only exist locally.
        intellijPlatform {
            localPlatformArtifacts()
        }

        mavenCentral()

        // Remote IntelliJ Platform repositories - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        // They are listed after Maven Central on purpose: the JetBrains CDN occasionally drops TLS
        // handshakes on some networks, and probing it first would make every dependency fragile.
        intellijPlatform {
            jetbrainsRuntime()
            jetbrainsIdeInstallers()
            androidStudioInstallers()
            releases()
            snapshots()
            intellijDependencies()
            marketplace()
        }
    }
}
