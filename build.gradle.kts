import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    compilerOptions {
        // Do not generate delegating stubs for deprecated default methods inherited from
        // platform interfaces (e.g. ToolWindowFactory.isApplicable), which the plugin
        // verifier reports as deprecated API usages.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation(libs.zxing.core)
    implementation(libs.jackson.databind)
    // SLF4J is provided by the IntelliJ Platform, avoid packaging a second copy.
    implementation(libs.json.path) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.2.4")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}
