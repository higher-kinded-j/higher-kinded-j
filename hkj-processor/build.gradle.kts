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

// =============================================================================
// Golden File Management
// =============================================================================
//
// Regenerates all golden files from current code generator output.
// Run when the code generator changes intentionally:
//
//   ./gradlew :hkj-processor:updateGoldenFiles
//
tasks.register<Test>("updateGoldenFiles") {
    description = "Regenerates golden files from current code generator output"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("updateGolden", "true")
    filter {
        includeTestsMatching("*GoldenFileTest.updateGoldenFiles")
    }
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
// Profiles (controlled via -Ppitest.profile=<value>):
//
//   conservative (default) — Suitable for laptops and lower-spec machines.
//     Uses half the available CPU cores and DEFAULT mutators.
//     Run with: ./gradlew :hkj-processor:pitest
//
//   full — Uses all CPU cores and STRONGER mutators for thorough analysis.
//     Run with: ./gradlew :hkj-processor:pitest -Ppitest.profile=full
//
// Fine-tuning individual settings (these override the profile):
//   -Ppitest.threads=N         Override thread count
//   -Ppitest.mutators=GROUP    Override mutator group (DEFAULT, STRONGER, ALL)
//   -Ppitest.heap=SIZE         Override per-fork heap (e.g. 768m, 1g)
//
// Reports: hkj-processor/build/reports/pitest/
//

val pitestProfile = (project.findProperty("pitest.profile") as String?) ?: "conservative"
val isFull = pitestProfile == "full"

val cpuCount = Runtime.getRuntime().availableProcessors()
val profileThreads = if (isFull) cpuCount else maxOf(1, cpuCount / 2)
val profileMutators = if (isFull) "STRONGER" else "DEFAULT"
val profileHeap = if (isFull) "1g" else "512m"

// Allow per-setting overrides via project properties
val effectiveThreads = (project.findProperty("pitest.threads") as String?)?.toInt() ?: profileThreads
val effectiveMutators = (project.findProperty("pitest.mutators") as String?) ?: profileMutators
val effectiveHeap = (project.findProperty("pitest.heap") as String?) ?: profileHeap

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

    // Mutator group: DEFAULT (conservative) or STRONGER (full)
    mutators.set(setOf(effectiveMutators))

    // Output formats
    outputFormats.set(setOf("HTML", "XML"))

    // Disable timestamped reports for cleaner output
    timestampedReports.set(false)

    // Mutation threshold: 70% (improved from 64% via ForComprehensionGeneratorTest
    // and expanded MutationKillingTest covering generator code paths)
    mutationThreshold.set(70)

    // JUnit 6 support
    junit5PluginVersion.set("1.2.3")

    // Thread count: half CPUs (conservative) or all CPUs (full)
    threads.set(effectiveThreads)

    // Heap per forked JVM
    jvmArgs.set(listOf("-Xmx${effectiveHeap}"))

    // Exclude test infrastructure from mutation
    excludedClasses.set(setOf(
        "org.higherkindedj.optics.processing.RuntimeCompilationHelper*"
    ))
}