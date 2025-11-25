
plugins {
    java
    id("com.vanniktech.maven.publish") version "0.33.0"
    id("com.diffplug.spotless") version "8.1.0"
}

// Global properties for all modules
group = "io.github.higher-kinded-j"
version = project.findProperty("projectVersion")?.toString() ?: "0.2.2-SNAPSHOT"



// Configure all submodules
subprojects {
    // Apply necessary plugins to each submodule
    plugins.apply("java-library")
    plugins.apply("com.diffplug.spotless")

    group = rootProject.group
    version = rootProject.version

    // Set Java version for all submodules
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    // Apply Spotless configuration to all java sources in subprojects
    spotless {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            target("src/**/*.java")
            googleJavaFormat("1.32.0").formatJavadoc(true)
            removeUnusedImports()
            trimTrailingWhitespace()
            licenseHeaderFile(rootProject.file("config/spotless/copyright.txt"), "(package|import|public|@)")
        }
    }
}