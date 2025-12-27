
plugins {
    java
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.openrewrite)
}

// Global properties for all modules
group = "io.github.higher-kinded-j"
version = project.findProperty("projectVersion")?.toString() ?: "0.2.2-SNAPSHOT"


// Repositories for root project (required for OpenRewrite dependencies)
repositories {
    mavenCentral()
}
// OpenRewrite configuration for the root project
rewrite {
    activeRecipe("org.openrewrite.java.ShortenFullyQualifiedTypeReferences")
    failOnDryRunResults = true  // CI enforcement

    // Only parse Java source files, exclude build scripts and other non-Java files
    exclusion(
        "**/*.kts",
        "**/*.kt",
        "**/*.gradle",
        "**/build/**",
        "**/.gradle/**"
    )
}


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

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
    }

    // Apply Spotless configuration to all java sources in subprojects
    spotless {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            target("src/**/*.java")
            googleJavaFormat(libs.versions.google.java.format.get()).formatJavadoc(true)
            removeUnusedImports()
            trimTrailingWhitespace()
            licenseHeaderFile(rootProject.file("config/spotless/copyright.txt"), "(package|import|public|@)")
        }
    }
}