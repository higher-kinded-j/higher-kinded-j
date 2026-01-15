import org.gradle.external.javadoc.StandardJavadocDocletOptions

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
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000", "--enable-preview"))
    }

    tasks.withType<Test>().configureEach {
        jvmArgs("--enable-preview")
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--enable-preview")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addBooleanOption("-enable-preview", true)
        (options as StandardJavadocDocletOptions).addStringOption("source", "25")
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

// =============================================================================
// Custom Tasks
// =============================================================================

/**
 * Full benchmark validation task.
 *
 * Runs the complete benchmark pipeline:
 * 1. VTask and Par unit tests (hkj-core)
 * 2. JaCoCo coverage report
 * 3. JMH benchmarks
 * 4. Benchmark assertion tests
 *
 * Usage: ./gradlew benchmarkValidation
 *
 * Note: This is separate from the regular test task as it takes several minutes.
 */
tasks.register("benchmarkValidation") {
    group = "verification"
    description = "Runs full benchmark validation: tests, coverage, JMH benchmarks, and assertions"

    dependsOn(":hkj-core:test")
    dependsOn(":hkj-core:jacocoTestReport")
    dependsOn(":hkj-benchmarks:jmh")
    dependsOn(":hkj-benchmarks:test")

    // Ensure proper ordering
    tasks.findByPath(":hkj-core:jacocoTestReport")?.mustRunAfter(":hkj-core:test")
    tasks.findByPath(":hkj-benchmarks:jmh")?.mustRunAfter(":hkj-core:jacocoTestReport")
    tasks.findByPath(":hkj-benchmarks:test")?.mustRunAfter(":hkj-benchmarks:jmh")

    doLast {
        println("\n" + "=".repeat(70))
        println("  BENCHMARK VALIDATION COMPLETE")
        println("=".repeat(70))
        println("\nReports generated:")
        println("  - JaCoCo:     hkj-core/build/reports/jacoco/test/html/index.html")
        println("  - JMH JSON:   hkj-benchmarks/build/reports/jmh/results.json")
        println("  - JMH Human:  hkj-benchmarks/build/reports/jmh/human.txt")
        println("\nRun './gradlew :hkj-benchmarks:benchmarkSummary' for a quick results overview.")
    }
}