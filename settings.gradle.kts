pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Auto-provisions the Java 25 toolchain (build.gradle.kts) when no matching
// local JDK is present, e.g. in fresh CI / Claude Code on the web containers.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}


rootProject.name = "higher-kinded-j"
include(
    "hkj-core",
    "hkj-test",
    "hkj-processor",
    "hkj-examples",
    "hkj-annotations", "hkj-api",
    "hkj-processor-plugins",
    "hkj-benchmarks",
    "hkj-openrewrite",
    "hkj-checker",
    "hkj-bom",
    // Plugin modules (under plugins/ directory)
    "hkj-gradle-plugin",
    "hkj-maven-plugin",
    // Spring Boot integration modules
    "hkj-spring:autoconfigure",
    "hkj-spring:starter",
    "hkj-spring:example",
    "hkj-spring:effect-example"
)

// Remap plugin modules to plugins/ subdirectory
project(":hkj-gradle-plugin").projectDir = file("plugins/hkj-gradle-plugin")
project(":hkj-maven-plugin").projectDir = file("plugins/hkj-maven-plugin")
