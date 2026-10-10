plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
    id("info.solidsoft.pitest") version "1.19.0-rc.3"
    jacoco
    alias(libs.plugins.protobuf)
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
    // Tests exercising generated error-envelope companions cast into hkj-core's carrier types
    // (ErrorEnvelope, TimeSource); already on the runtime classpath via hkj-processor-plugins.
    testImplementation(project(":hkj-core"))
    // Law-checks every @GenerateMapping emission tier against the published harness
    // (GeneratedMappingLawsTest). hkj-test depends only on hkj-core, so no cycle.
    testImplementation(project(":hkj-test"))

    testImplementation(libs.compile.testing)
    testImplementation(libs.truth)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Lombok interop coverage: bean-shaped wires read Lombok-generated accessors in the same
    // javac run, so the pairing is pinned by LombokInteropTest rather than assumed.
    testImplementation(libs.lombok)
    // protobuf-java interop coverage: its well-known types are messages protoc generated, so a
    // mapping over each field kind is pinned against the real accessors rather than a stand-in.
    testImplementation(libs.protobuf.java)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)

    // Property-based testing for verifying generated optics with random inputs
    testImplementation(libs.bundles.jqwik)
}

// protoc for the test-only .proto files, at the release that matches the protobuf-java runtime: a
// message whose field names only its descriptor keeps is real protoc output, not a stand-in.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.asProvider().get()}"
    }
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    setForkEvery(100)
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    // Exclude test infrastructure from coverage measurement
    classDirectories.setFrom(
        sourceSets.main.get().output.classesDirs.map { dir ->
            fileTree(dir).apply {
                exclude(
                    "**/RuntimeCompilationHelper*.class"
                )
            }
        }
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    classDirectories.setFrom(
        sourceSets.main.get().output.classesDirs.map { dir ->
            fileTree(dir).apply {
                exclude(
                    "**/RuntimeCompilationHelper*.class"
                )
            }
        }
    )

    violationRules {
        // 99/97 (not 100): the residual is verified-unreachable - compiler-synthesised
        // exhaustive-switch defaults, @Target-unproducible arms, and descriptor-table
        // combos that cannot occur (see the coverage-hardening PR for the per-class list).
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.99".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.97".toBigDecimal()
            }
        }
        // Classes held at 100%: every branch each of these has is one a test can reach, so a new
        // unreachable one is a design signal rather than a residual to be waived.
        rule {
            element = "CLASS"
            includes = listOf(
                "org.higherkindedj.optics.processing.ErrorEnvelopeProcessor*",
                "org.higherkindedj.optics.processing.MappingProcessor*",
                "org.higherkindedj.optics.processing.MappingIndexes*",
                "org.higherkindedj.optics.processing.MergeProcessor*",
                "org.higherkindedj.optics.processing.NullScan*",
                "org.higherkindedj.optics.processing.ImplConstants*",
                "org.higherkindedj.optics.processing.ContainerCopy*",
                "org.higherkindedj.optics.processing.BeanPropertyAnalyser*",
                "org.higherkindedj.optics.processing.WireShape*",
                "org.higherkindedj.optics.processing.WaitingSpecs*",
                "org.higherkindedj.optics.processing.WaitingImporters*",
                "org.higherkindedj.optics.processing.NavigatorClassGenerator*",
                "org.higherkindedj.optics.processing.WideningAnalysis*",
                "org.higherkindedj.optics.processing.GeneratorRegistry*",
                "org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor*",
                "org.higherkindedj.optics.processing.effect.PathProcessor*",
                "org.higherkindedj.optics.processing.external.InstanceOfNarrowing*",
                "org.higherkindedj.optics.processing.external.CopyStrategyChecks*",
                "org.higherkindedj.optics.processing.external.SpecAnalysis*",
                "org.higherkindedj.optics.processing.external.SpecInterfaceAnalyser*",
                "org.higherkindedj.optics.processing.external.TypeKindAnalyser*",
                "org.higherkindedj.optics.processing.external.CallBinding*",
                "org.higherkindedj.optics.processing.external.ConstructorOrderChecks*",
                "org.higherkindedj.optics.processing.external.CopyStrategyCodeGenerator*",
                "org.higherkindedj.optics.processing.util.ProcessorUtils*",
                "org.higherkindedj.optics.processing.util.Reachability*",
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// =============================================================================
// Nullness checking, from a consumer's side
// =============================================================================
//
// Compiles fixtures with NullAway in JSpecify mode, as a consumer who runs a nullness checker
// does, over hand-written optics and the code the processors generate. No nullness checker runs
// over the library itself, so without this suite a declaration that rejects a nullable type
// argument is invisible to the build.
//
// Its own suite for two reasons: Error Prone needs jdk.compiler opened to the test JVM, and its
// dependencies (Guava, protobuf-java and the rest) resolve on a classpath of their own rather than
// against the versions the main tests pin.
testing {
    suites {
        register<JvmTestSuite>("nullnessTest") {
            dependencies {
                implementation(project())
                implementation(project(":hkj-core"))
                implementation(project(":hkj-processor-plugins"))
                implementation(platform(libs.junit.bom))
                implementation(libs.junit.jupiter)
                implementation(libs.compile.testing)
                implementation(libs.truth)
                implementation(libs.errorprone.core)
                implementation(libs.nullaway)
                runtimeOnly(libs.junit.platform.launcher)
                // The root build adds -Xplugin:HKJChecker to every compile, this suite's included.
                annotationProcessor(project(":hkj-checker"))
            }
            targets.all {
                testTask.configure {
                    useJUnitPlatform()
                    val javacPackages = listOf("api", "file", "main", "model", "parser", "processing", "tree", "util")
                    jvmArgs(javacPackages.map { "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
                    jvmArgs(listOf("code", "comp").map { "--add-opens=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED" })
                    // The suite checks types, not coverage: JaCoCo's report reads the main suite only.
                    extensions.configure<JacocoTaskExtension> { isEnabled = false }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("nullnessTest"))
}

// =============================================================================
// Golden File Management
// =============================================================================
//
// Regenerates every golden file from the generator's current output, for the two tasks below.
// Gradle does not track the golden files they write, so neither is ever skipped as up to date.
val regeneratesGoldenFiles: Test.() -> Unit = {
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("updateGolden", "true")
    filter {
        includeTestsMatching("*GoldenFileTest.generatedCodeMatchesGolden")
        includeTestsMatching("*ForComprehensionGoldenFileTest.generatedCodeMatchesGolden")
    }
    doNotTrackState("It writes the golden files, which Gradle does not track")
}

// Regenerates all golden files from current code generator output.
// Run when the code generator changes intentionally:
//
//   ./gradlew :hkj-processor:updateGoldenFiles
//
tasks.register<Test>("updateGoldenFiles") {
    description = "Regenerates golden files from current code generator output"
    regeneratesGoldenFiles()
}

// Regenerates the golden files and fails if any then differs from the committed copy, or is not
// committed at all. The golden tests in `test` read the working-tree copies and ignore line
// endings and trailing whitespace, so within a build only this checks the committed copies byte
// for byte. It refuses to start while a golden file has uncommitted changes, which regenerating
// would overwrite. A failure after regenerating leaves the regenerated files in place to review.
//
//   ./gradlew :hkj-processor:verifyGoldenFiles
//
tasks.register<Test>("verifyGoldenFiles") {
    description = "Regenerates golden files and fails if any differs from the committed copy"
    regeneratesGoldenFiles()
    val goldenDir = file("src/test/resources/golden").relativeTo(rootDir).invariantSeparatorsPath
    val processFactory = providers
    val repositoryDir = rootDir
    val changedGoldenFiles = {
        processFactory.exec {
            workingDir = repositoryDir
            commandLine("git", "--no-optional-locks", "status", "--porcelain", "--untracked-files=all", "--", goldenDir)
        }.standardOutput.asText.get()
    }
    doFirst {
        val uncommitted = changedGoldenFiles()
        if (uncommitted.isNotEmpty()) {
            throw GradleException(
                "Golden files have uncommitted changes, which regenerating would overwrite:\n" +
                    uncommitted + "Commit, stash or restore them first."
            )
        }
    }
    doLast {
        val changed = changedGoldenFiles()
        if (changed.isNotEmpty()) {
            throw GradleException(
                "Golden files differ from the committed copies once regenerated:\n" + changed +
                    "Review them with git diff HEAD -- $goldenDir (a ?? line is a new file to git add), " +
                    "then commit them or fix the generator."
            )
        }
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
        name.set("Higher-Kinded-J Annotation Processor")
        description.set("Annotation processor for Higher-Kinded-J that generates optics and data-mapping boilerplate for Java records and sealed interfaces.")
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
        inceptionYear.set("2025")
        organization {
            name.set("The Higher-Kinded-J Team")
            url.set("https://github.com/higher-kinded-j")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/higher-kinded-j/higher-kinded-j/issues")
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
//   -Ppitest.mutators=GROUP    Override mutator group (DEFAULTS, STRONGER, ALL)
//   -Ppitest.heap=SIZE         Override per-fork heap (e.g. 768m, 1g)
//
// Reports: hkj-processor/build/reports/pitest/
//

val pitestProfile = (project.findProperty("pitest.profile") as String?) ?: "conservative"
val isFull = pitestProfile == "full"

val cpuCount = Runtime.getRuntime().availableProcessors()
val profileThreads = if (isFull) cpuCount else maxOf(1, cpuCount / 2)
val profileMutators = if (isFull) "STRONGER" else "DEFAULTS"
val profileHeap = if (isFull) "1g" else "512m"

// Allow per-setting overrides via project properties
val effectiveThreads = (project.findProperty("pitest.threads") as String?)?.toInt() ?: profileThreads
val effectiveMutators = (project.findProperty("pitest.mutators") as String?) ?: profileMutators
val effectiveHeap = (project.findProperty("pitest.heap") as String?) ?: profileHeap

pitest {
    // Use PIT version 1.22.1 as specified in project requirements
    pitestVersion.set("1.22.1")

    // Target classes for mutation
    targetClasses.set(setOf(
        "org.higherkindedj.optics.processing.*"
    ))

    // Target tests to run against mutants
    targetTests.set(setOf(
        "org.higherkindedj.optics.processing.*Test",
        "org.higherkindedj.optics.processing.*Tests"
    ))

    // Mutator group: DEFAULTS (conservative) or STRONGER (full)
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

    // Timeout: allow more time for mutated code (PIT default can be too tight)
    timeoutConstInMillis.set(8000)
    timeoutFactor.set("1.5".toBigDecimal())

    // Exclude slow golden file tests from mutation runs to avoid minion timeouts
    excludedTestClasses.set(setOf(
        "org.higherkindedj.optics.processing.ForComprehensionGoldenFileTest",
        // Its javac runs in a process of its own, which no mutant reaches
        "org.higherkindedj.optics.processing.ProcessorRegistrationTest"
    ))

    // Exclude test infrastructure from mutation
    excludedClasses.set(setOf(
        "org.higherkindedj.optics.processing.RuntimeCompilationHelper*"
    ))
}