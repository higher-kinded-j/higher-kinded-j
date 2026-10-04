import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    java
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.openrewrite)
}

// Global properties for all modules
group = "io.github.higher-kinded-j"
// A release passes projectVersion; otherwise this is gradle.properties' snapshotVersion, which
// checkSnapshotVersion holds to the latest release tag.
version = project.findProperty("projectVersion")?.toString() ?: providers.gradleProperty("snapshotVersion").get()


// Repositories for root project (required for OpenRewrite dependencies)
repositories {
    mavenCentral()
}
// OpenRewrite configuration for the root project
rewrite {
    activeRecipe("com.higherkindedj.ShortenFullyQualifiedTypeReferencesJavaOnly")
    failOnDryRunResults = true  // CI enforcement
}


// Modules that use java-platform instead of java-library
val platformModules = setOf("hkj-bom")

// Configure all submodules
subprojects {
    group = rootProject.group
    version = rootProject.version

    // Apply necessary plugins to each submodule (skip java-library for platform modules)
    if (project.name !in platformModules) {
        plugins.apply("java-library")
    }
    plugins.apply("com.diffplug.spotless")

    // Set Java version for all submodules with Java sources
    if (project.name !in platformModules) {
        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            // -Werror ratchet: a new unchecked/raw-type use must carry an explicit,
            // justified @SuppressWarnings or the build fails (see issue #560).
            listOf("-Xmaxerrs", "10000", "--enable-preview", "-Xlint:unchecked,rawtypes", "-Werror")
        )
    }

    // Run HKJ's own compile-time checks over HKJ. The checks exist for HKJ shapes, so the
    // library is their most demanding user: the two false positives fixed alongside this
    // wiring (a requireNonNull guard read as a discarded effect, and eager paths judged by
    // the lazy-effect rule) were both invisible while the checker never saw this codebase.
    //
    // severity=warn keeps a new finding advisory at the checker's own level; -Werror above
    // still turns it into a build failure, which is the intent — the repo is at zero
    // findings and a regression should stop the build. A deliberate exception is spelled
    // @SuppressWarnings("<check-id>") on the narrowest enclosing declaration.
    //
    // hkj-checker is excluded: it cannot run itself while being built, and its fixtures
    // violate the checks on purpose.
    if (project.name !in platformModules && project.name != "hkj-checker") {
        dependencies {
            add("annotationProcessor", project(":hkj-checker"))
            add("testAnnotationProcessor", project(":hkj-checker"))
        }
        // The JMH source set compiles against its own processor path, and only under
        // releaseReadiness / benchmarkValidation, so a missing entry here is invisible to
        // an ordinary build; the -Xplugin flag below is added to every JavaCompile.
        plugins.withId("me.champeau.jmh") {
            dependencies.add("jmhAnnotationProcessor", project(":hkj-checker"))
        }
        tasks.withType<JavaCompile>().configureEach {
            // JMH's generated benchmark wrappers are harness code compiled on their own
            // processor path, with nothing of ours to check.
            if (name == "jmhCompileGeneratedClasses") return@configureEach
            options.compilerArgs.add("-Xplugin:HKJChecker severity=warn")
            // The checker reads jdk.compiler internals, which the compiler JVM must export.
            options.isFork = true
            options.forkOptions.jvmArgs?.addAll(
                listOf(
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                )
            )
        }
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

// mustRunAfter, unless the task has already run. A task listing such as `gradle :x:test tasks`
// configures the tasks below after others have run, and Gradle refuses to order a started task.
fun Task.mustRunAfterIfPending(vararg paths: Any) {
    if (!state.executed) mustRunAfter(*paths)
}

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
    tasks.getByPath(":hkj-core:jacocoTestReport").mustRunAfterIfPending(":hkj-core:test")
    tasks.getByPath(":hkj-benchmarks:jmh").mustRunAfterIfPending(":hkj-core:jacocoTestReport")
    tasks.getByPath(":hkj-benchmarks:test").mustRunAfterIfPending(":hkj-benchmarks:jmh")

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

/**
 * British spelling check over the repository's prose, as docs/STYLE-GUIDE.md asks and CI runs
 * before its build. It needs Node.js. To rewrite the spellings it reports, run the script
 * itself: node .github/scripts/british-spelling-check.cjs --fix
 *
 * Usage: ./gradlew britishSpellingCheck
 */
tasks.register<Exec>("britishSpellingCheck") {
    group = "verification"
    description = "Checks that the repository's prose uses British spelling"
    workingDir = rootProject.projectDir
    commandLine("node", ".github/scripts/british-spelling-check.cjs")
}

/**
 * Release readiness quality gate.
 *
 * Runs the release's checks from fastest to slowest, failing fast
 * on the cheapest checks first:
 *
 * 1. britishSpellingCheck — British spelling in prose (seconds)
 * 2. spotlessCheck        — code formatting, every module (seconds)
 * 3. verifyGoldenFiles    — golden files regenerated and compared with git (about a minute
 *                           once the processor is compiled)
 * 4. build                — compile + unit tests, every module but hkj-benchmarks, and the
 *                           JaCoCo limits of the modules that set them (minutes)
 * 5. jmh                  — JMH benchmarks (minutes)
 * 6. benchmark tests      — benchmark assertion tests (seconds, requires jmh)
 * 7. pitest (full)        — mutation testing with STRONGER mutators (slowest)
 *
 * Steps 1 and 3 are the checks CI runs before its build, with the same script and task. Step 3
 * refuses to start while a golden file has uncommitted changes, which it would overwrite, and
 * fails on a golden file git does not track. Steps 2 and 4 name every module's task by path.
 * In dependsOn, a bare task name means this root project's own task, which checks nothing;
 * only on the command line does a name select the task in every project. hkj-benchmarks is
 * left out of step 4, as CI leaves it out of its build, because that build includes the
 * benchmark assertion tests, which read the results step 5 writes. The book's own checks are
 * separate: hkj-book/check.sh.
 *
 * Usage: ./gradlew releaseReadiness
 *
 * This is the essential quality gate before any release. All steps must pass.
 */
tasks.register("releaseReadiness") {
    group = "verification"
    description = "Release quality gate: British spelling, spotless in every module, golden files, build in every module but hkj-benchmarks, benchmarks, benchmark assertions, pitest (full profile)"

    val spelling = ":britishSpellingCheck"
    val formatChecks = subprojects.map { "${it.path}:spotlessCheck" }
    val goldenFiles = ":hkj-processor:verifyGoldenFiles"
    val builds = subprojects.filter { it.path != ":hkj-benchmarks" }.map { "${it.path}:build" }

    dependsOn(spelling)
    dependsOn(formatChecks)
    dependsOn(goldenFiles)
    dependsOn(builds)
    dependsOn(":hkj-benchmarks:jmh")
    dependsOn(":hkj-benchmarks:test")
    dependsOn("pitestFull")

    // Enforce fast-to-slow ordering. mustRunAfter orders a task but not the tasks it depends
    // on, so the rules go on every module task, not just each build: Spotless's tasks after the
    // spelling check, every other task after both, and every test after the golden files, which
    // need only compilation first. clean is exempt, since Spotless orders its own tasks after
    // it. Gradle configures this task only when it is requested or listed, so other builds keep
    // their order.
    subprojects {
        tasks.configureEach {
            when {
                name == "clean" -> Unit
                name.startsWith("spotless") -> mustRunAfterIfPending(spelling)
                else -> {
                    mustRunAfterIfPending(spelling, formatChecks)
                    if (this is Test && path != goldenFiles) mustRunAfterIfPending(goldenFiles)
                }
            }
        }
    }
    tasks.getByPath(":hkj-benchmarks:jmh").mustRunAfterIfPending(builds)
    tasks.getByPath(":hkj-benchmarks:test").mustRunAfterIfPending(":hkj-benchmarks:jmh")
    tasks.getByName("pitestFull").mustRunAfterIfPending(builds)

    doLast {
        println("\n" + "=".repeat(70))
        println("  RELEASE READINESS — ALL CHECKS PASSED")
        println("=".repeat(70))
        println("\nAll quality gates passed (fast to slow):")
        println("  1. British spelling   — prose across the repository")
        println("  2. Spotless           — code formatting, every module")
        println("  3. Golden files       — regenerated, and identical to the committed copies")
        println("  4. Build              — compile + unit tests, every module but hkj-benchmarks; JaCoCo limits where set")
        println("  5. JMH Benchmarks     — performance benchmarks")
        println("  6. Benchmark Tests    — performance assertions")
        println("  7. Pitest (full)      — mutation testing (STRONGER mutators)")
        println("\nReports:")
        println("  - JaCoCo:     hkj-core/build/reports/jacoco/test/html/index.html")
        println("  - JMH JSON:   hkj-benchmarks/build/reports/jmh/results.json")
        println("  - JMH Human:  hkj-benchmarks/build/reports/jmh/human.txt")
        println("  - Pitest:     hkj-processor/build/reports/pitest/index.html")
    }
}

tasks.register<Exec>("pitestFull") {
    group = "verification"
    description = "Run pitest with full profile (STRONGER mutators, all CPU cores)"
    workingDir = rootProject.projectDir
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("windows")) "gradlew.bat" else "./gradlew",
        ":hkj-processor:pitest",
        "-Ppitest.profile=full"
    )
    // Pitest is the slowest step — run it after everything else
    mustRunAfter(":hkj-benchmarks:test")
    doFirst {
        println("\nRunning pitest with FULL profile (STRONGER mutators, all CPU cores)")
    }
}

/**
 * Snapshot version check.
 *
 * A merge to main publishes gradle.properties' snapshotVersion to the snapshots repository. After
 * a release a.b.c the usual snapshot is a.b.(c+1)-SNAPSHOT, and that passes as it is. A minor or
 * major step, a.(b+1).0-SNAPSHOT or (a+1).0.0-SNAPSHOT, is legitimate but deliberate, so this asks
 * you to confirm it, once, and records the answer as confirmedSnapshotVersion in gradle.properties
 * for you to commit. A version that is not ahead of the latest release, or that skips one, fails.
 *
 * A build that cannot ask, such as CI's, fails on an unconfirmed step instead, naming the fix.
 * Without release tags, as in a shallow clone, there is nothing to compare with and it passes.
 *
 * Usage: ./gradlew checkSnapshotVersion (check runs it, and publish.yml before a snapshot)
 */
val checkSnapshotVersion = tasks.register("checkSnapshotVersion") {
    group = "verification"
    description = "Checks snapshotVersion follows the latest release tag, asking to confirm a minor or major step"

    val userInput = serviceOf<UserInputHandler>()
    val propertiesFile = layout.projectDirectory.file("gradle.properties").asFile
    val declared = providers.gradleProperty("snapshotVersion")
    val confirmed = providers.gradleProperty("confirmedSnapshotVersion")
    val tags = providers.exec {
        commandLine("git", "tag", "--list", "v[0-9]*", "--sort=-v:refname")
        isIgnoreExitValue = true
    }.standardOutput.asText

    doLast {
        val snapshot = declared.get()
        val snapshotParts = Regex("""(\d+)\.(\d+)\.(\d+)-SNAPSHOT""").matchEntire(snapshot)
            ?: throw GradleException("snapshotVersion in gradle.properties must read a.b.c-SNAPSHOT, found '$snapshot'.")
        val latestTag = tags.get().lineSequence().map { it.trim() }
            .firstOrNull { Regex("""v\d+\.\d+\.\d+""").matches(it) }
        if (latestTag == null) {
            logger.lifecycle("checkSnapshotVersion: no release tag to compare $snapshot with")
            return@doLast
        }
        val (major, minor, patch) = latestTag.removePrefix("v").split('.').map { it.toInt() }
        val nextPatch = "$major.$minor.${patch + 1}-SNAPSHOT"
        val nextMinor = "$major.${minor + 1}.0-SNAPSHOT"
        val nextMajor = "${major + 1}.0.0-SNAPSHOT"

        fun record(key: String, value: String) {
            val lines = propertiesFile.readLines()
            val updated =
                if (lines.any { it.startsWith("$key=") }) lines.map { if (it.startsWith("$key=")) "$key=$value" else it }
                else lines.flatMap { if (it.startsWith("snapshotVersion=")) listOf(it, "$key=$value") else listOf(it) }
            propertiesFile.writeText(updated.joinToString("\n", postfix = "\n"))
        }

        val (a, b, c) = snapshotParts.destructured
        val ahead = compareValuesBy(
            Triple(a.toInt(), b.toInt(), c.toInt()), Triple(major, minor, patch),
            { it.first }, { it.second }, { it.third }) > 0
        when {
            snapshot == nextPatch ->
                logger.lifecycle("checkSnapshotVersion: $snapshot follows $latestTag")
            (snapshot == nextMinor || snapshot == nextMajor) && confirmed.orNull == snapshot ->
                logger.lifecycle("checkSnapshotVersion: $snapshot follows $latestTag, as confirmed")
            snapshot == nextMinor || snapshot == nextMajor -> {
                val step = if (snapshot == nextMinor) "minor" else "major"
                val answer = userInput.askYesNoQuestion(
                    "The latest release is $latestTag, so the usual next snapshot is $nextPatch. " +
                        "snapshotVersion is $snapshot, a $step step. Publish $snapshot from main?")
                when (answer) {
                    true -> {
                        record("confirmedSnapshotVersion", snapshot)
                        logger.lifecycle("checkSnapshotVersion: confirmed $snapshot in gradle.properties; commit it")
                    }
                    false -> throw GradleException(
                        "snapshotVersion $snapshot is not confirmed. For the next patch release, set snapshotVersion=$nextPatch in gradle.properties.")
                    null -> throw GradleException(
                        "snapshotVersion $snapshot is a $step step from $latestTag, where the usual next snapshot is $nextPatch, " +
                            "and this build cannot ask you to confirm it. Run ./gradlew checkSnapshotVersion in a terminal and commit " +
                            "gradle.properties, or add confirmedSnapshotVersion=$snapshot to it yourself.")
                }
            }
            !ahead -> {
                val answer = userInput.askYesNoQuestion(
                    "snapshotVersion $snapshot is not ahead of the latest release $latestTag. Move it to $nextPatch?")
                if (answer == true) {
                    record("snapshotVersion", nextPatch)
                    logger.lifecycle("checkSnapshotVersion: set snapshotVersion=$nextPatch in gradle.properties; commit it")
                } else {
                    throw GradleException(
                        "snapshotVersion $snapshot is not ahead of the latest release $latestTag. Set snapshotVersion in " +
                            "gradle.properties to $nextPatch, or to $nextMinor or $nextMajor for a minor or major release.")
                }
            }
            else -> throw GradleException(
                "snapshotVersion $snapshot skips a release after $latestTag. The next snapshot is $nextPatch, " +
                    "$nextMinor or $nextMajor.")
        }
    }
}

tasks.named("check") {
    dependsOn(checkSnapshotVersion)
}
