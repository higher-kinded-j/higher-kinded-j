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
    // Spring Boot integration modules
    "hkj-spring:autoconfigure",
    "hkj-spring:starter",
    "hkj-spring:example"
)