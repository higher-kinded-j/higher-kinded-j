pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}


rootProject.name = "higher-kinded-j"
include(
    "hkj-core",
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
