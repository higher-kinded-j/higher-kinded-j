plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
    id("info.solidsoft.pitest") version "1.19.0-rc.3"
}

dependencies {
    // Depends on the annotations module to find the @GenerateLenses annotation
    implementation(project(":hkj-annotations"))
    implementation(project(":hkj-api"))

    // The processor's own dependencies, which will not leak into the core module
    implementation(libs.javapoet)
    implementation(libs.autoservice.annotations)
    annotationProcessor(libs.autoservice)

    testImplementation(project(":hkj-processor-plugins"))

    testImplementation(libs.compile.testing)
    testImplementation(libs.truth)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)

    // Property-based testing for verifying generated optics with random inputs
    testImplementation(libs.bundles.jqwik)
}

tasks.test {
    useJUnitPlatform()
}


// Central configuration for publishing. This is inherited by all submodules
// that apply the 'com.vanniktech.maven.publish' plugin.
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-processor",
        version = project.version.toString()
    )

    // POM details are defined once and inherited by all published submodules.
    pom {
        name.set("Higher-Kinded-J")
        description.set("Bringing Higher-Kinded Types to Java Functional Patterns - Annotation Processor")
        url.set("https://github.com/higher-kinded-j/higher-kinded-j")

        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("higher-kinded-j")
                name.set("Magnus Smith")
                email.set("simulation-hkt@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/higher-kinded-j/higher-kinded-j.git")
            developerConnection.set("scm:git:ssh://github.com/higher-kinded-j/higher-kinded-j.git")
            url.set("https://github.com/higher-kinded-j/higher-kinded-j")
        }
    }

}

// =============================================================================
// Mutation Testing Configuration (Local Development Only)
// =============================================================================
//
// PIT mutation testing is configured for local development use only,
// not as a CI gate. Use it to measure and improve test quality.
//
// Run with: ./gradlew :hkj-processor:pitest
// Reports: hkj-processor/build/reports/pitest/
//
pitest {
    // Use PIT version 1.22.0 as specified in project requirements
    pitestVersion.set("1.22.0")

    // Target classes for mutation
    targetClasses.set(setOf(
        "org.higherkindedj.optics.processing.*"
    ))

    // Target tests to run against mutants
    targetTests.set(setOf(
        "org.higherkindedj.optics.processing.*Test",
        "org.higherkindedj.optics.processing.*Tests"
    ))

    // Use STRONGER mutators for thorough testing
    mutators.set(setOf("STRONGER"))

    // Output formats
    outputFormats.set(setOf("HTML", "XML"))

    // Disable timestamped reports for cleaner output
    timestampedReports.set(false)

    // Mutation threshold: 64% (improved from initial 60%)
    // 114 mutations have no test coverage (error handling paths)
    mutationThreshold.set(64)

    // JUnit 6 support
    junit5PluginVersion.set("2.0.0")

    // Threads for faster execution
    threads.set(Runtime.getRuntime().availableProcessors())

    // Exclude test infrastructure from mutation
    excludedClasses.set(setOf(
        "org.higherkindedj.optics.processing.RuntimeCompilationHelper*"
    ))
}