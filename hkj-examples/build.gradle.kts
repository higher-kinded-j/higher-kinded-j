plugins {
    application
}

// Allow running any example via: ./gradlew :hkj-examples:run -PmainClass=<fully.qualified.ClassName>
application {
    mainClass.set(project.findProperty("mainClass")?.toString() ?: "org.higherkindedj.example.tutorials.TutorialGettingStarted")
}

dependencies {
    api(libs.jspecify)
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor-plugins"))

    // External libraries for spec interface examples
    implementation(libs.jackson.databind)
    implementation(libs.jooq)

    // Eclipse Collections for cross-ecosystem portfolio risk example
    implementation(libs.eclipse.collections)

    // PCollections for the persistent-collections HKT compatibility example (Phase 1)
    implementation(libs.pcollections)

    // Testing dependencies for tutorials
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.awaitility)
    testImplementation(project(":hkj-test"))

    // The TraversableGenerator SPI (BaseTraversableGenerator, Cardinality) that hkj-optics'
    // container-types page documents. It is wired above as `annotationProcessor`, which puts it on
    // the PROCESSOR path, not the compile classpath, so the gate could not compile a snippet that
    // implements the SPI. javac is given an explicit -processorpath, so classpath discovery stays off.
    testImplementation(project(":hkj-processor-plugins"))

    testImplementation(project(":hkj-spring:client"))

    // Test-only, so the examples' own artifact stays Spring-free: the hkj-spring skill's snippets
    // are Spring code, and without these the gate could not compile them at all.
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(project(":hkj-spring:starter"))
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.webmvc.test)

    // JUnit declares apiguardian as compileOnly, so it is absent from the test runtime classpath.
    // The book gate hands that classpath to javac, and a snippet using @Test then trips
    // "unknown enum constant API.Status.STABLE" -- a warning, which the gate treats as fatal.
    // It is a gap in the harness's classpath, not a defect in the page, so close the gap.
    testRuntimeOnly("org.apiguardian:apiguardian-api:1.1.2")
}

tasks.named("javadoc") {
    enabled = false
}

// The processors the documentation gate hands to javac: this module's own, plus the @HkjHttpClient
// one, so the hkj-spring skill's client snippets are checked against the generated
// <Name>HttpExchange rather than a stub of it. Kept out of the main compile, which has no
// @HkjHttpClient to process.
val bookProcessor: Configuration by configurations.creating {
    extendsFrom(configurations.annotationProcessor.get())
}

dependencies {
    bookProcessor(project(":hkj-spring:client-processor"))
}

// The book-snippet fixtures (src/test/resources/fixtures) are .java for IDE support, but they are
// resources, not sources: their imports exist for the *snippet* they are spliced into, so an
// "unused import" cleanup would silently break them.
spotless {
    java {
        targetExclude("src/test/resources/fixtures/**")
    }
}

// Default test task: runs solution tests only (tutorials are excluded)
// Solutions must pass as they verify the tutorial exercises are correct
tasks.named<Test>("test") {
    useJUnitPlatform()

    // The book gate has its own task (`bookVerify`, below): hanging it off `test` meant every edit
    // to an .md file invalidated `test` and re-ran the whole tutorial/solution suite.
    exclude("org/higherkindedj/book/**")

    // Exclude tutorial exercises (they are incomplete by design)
    // Include solutions which must always pass
    exclude("**/tutorial/optics/**")
    exclude("**/tutorial/coretypes/**")
    exclude("**/tutorial/concurrency/**")
    exclude("**/tutorial/effect/**")
    exclude("**/tutorial/expression/**")
    exclude("**/tutorial/effecthandlers/**")
    exclude("**/tutorial/context/**")
    exclude("**/tutorial/mtl/**")
    exclude("**/tutorial/transformers/**")
    exclude("**/tutorial/resilience/**")
    // Solutions are in tutorial/solutions/ and will run
}

// The book gate: compiles hkj-book's `<!-- verify -->` snippets against this module's classpath and
// the REAL annotation processor, and checks that every `{{#include}}` resolves to a real anchor. A
// page therefore cannot drift away from the API without failing the build. It lives in this module
// rather than one of its own because this module already wires the processor, hkj-test, JUnit and
// AssertJ. See BOOK-SNIPPETS.md.
val bookVerify = tasks.register<Test>("bookVerify") {
    description = "Compiles the code in hkj-book against the library, so the docs cannot drift."
    group = "verification"
    useJUnitPlatform()

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("org/higherkindedj/book/**")

    val bookDir = rootProject.layout.projectDirectory.dir("hkj-book/src")
    inputs.dir(bookDir).withPropertyName("bookSources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("hkj.book.dir", bookDir.asFile.absolutePath)

    // The shipped Claude Code skills are documentation too, and they are installed into consumer
    // projects by the build plugins. Until they were gated, nothing compiled their code.
    val skillsDir = rootProject.layout.projectDirectory.dir(".claude/skills")
    inputs.dir(skillsDir).withPropertyName("skillSources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("hkj.skills.dir", skillsDir.asFile.absolutePath)

    // The anchored example sources are inputs too. Without this, renaming an `// ANCHOR:` would not
    // re-run the gate: the rename is a comment, so the compiled classes are byte-identical and the
    // task stays UP-TO-DATE while the book silently loses that code block.
    inputs
        .files(layout.projectDirectory.dir("src/main/java/org/higherkindedj/example/book").asFileTree)
        .withPropertyName("bookExampleSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .files(layout.projectDirectory.dir("src/test/java/org/higherkindedj/example/book").asFileTree)
        .withPropertyName("bookExampleTestSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // Declared as a proper input (so a processor change re-runs the gate) and passed via an argument
    // provider (so the configuration cache can serialise it). It must be captured as a FileCollection,
    // not a NamedDomainObjectProvider: the cache cannot serialise the latter.
    val processorPath: FileCollection = bookProcessor
    inputs.files(processorPath)
        .withPropertyName("bookProcessorPath")
        .withNormalizer(ClasspathNormalizer::class)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dhkj.book.processorPath=${processorPath.asPath}")
        }
    )
}

tasks.named("check") { dependsOn(bookVerify) }

// Separate task for users to run tutorial exercises
// Usage: ./gradlew :hkj-examples:tutorialTest
tasks.register<Test>("tutorialTest") {
    description = "Run tutorial exercises (expected to fail until completed by user)"
    group = "verification"
    useJUnitPlatform()

    // Configure test classpath (required for custom Test tasks)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    // Only include tutorial exercises, exclude solutions
    include("**/tutorial/optics/**")
    include("**/tutorial/coretypes/**")
    include("**/tutorial/concurrency/**")
    include("**/tutorial/effect/**")
    include("**/tutorial/expression/**")
    include("**/tutorial/effecthandlers/**")
    include("**/tutorial/mtl/**")
    include("**/tutorial/resilience/**")
    exclude("**/tutorial/solutions/**")
    // Don't fail the build - tutorials are expected to fail
    ignoreFailures = true
}

// Per-journey progress report: count remaining answerRequired() calls.
// Usage: ./gradlew :hkj-examples:tutorialProgress
tasks.register("tutorialProgress") {
    description =
        "Show how many answerRequired() placeholders remain in each tutorial journey."
    group = "help"

    val tutorialRoot = layout.projectDirectory
        .dir("src/test/java/org/higherkindedj/tutorial")
        .asFile

    // Capture inputs at configuration time so the task can be cached.
    val journeyDirs: List<java.io.File> = if (tutorialRoot.isDirectory) {
        tutorialRoot.listFiles()
            ?.filter { it.isDirectory && it.name != "solutions" }
            ?.sortedBy { it.name }
            ?: emptyList()
    } else {
        emptyList()
    }

    inputs.dir(tutorialRoot).withPropertyName("tutorialSources").optional(true)

    doLast {
        if (journeyDirs.isEmpty()) {
            println("No tutorial sources found under $tutorialRoot")
            return@doLast
        }

        // A finished exercise has its answerRequired() call replaced. We grep for the
        // placeholder pattern on the right-hand side of an assignment or as the sole
        // expression of a TODO line, ignoring the helper definition itself.
        //
        // We deliberately keep this a regex over text rather than a full Java parser:
        // the pattern is unambiguous and the cost of any false positive is one line off
        // a progress bar, not broken behaviour.
        val placeholderRegex = Regex("""\banswerRequired\s*\(""")
        val helperDefinitionRegex =
            Regex("""private\s+static\s+<T>\s+T\s+answerRequired\s*\(""")

        data class Stats(val journey: String, val files: Int, val remaining: Int, val total: Int)

        // We treat the *initial* remaining count per file as the "total" so the bar shows
        // progress relative to where we started. Since we cannot store a baseline in this
        // simple task, we approximate: total = remaining + (already-completed exercises),
        // estimated by counting the @Test methods in the file. Off by a small amount when
        // an exercise has multiple placeholders, but accurate enough as a signal.
        val testRegex = Regex("""@Test\b""")

        val stats = journeyDirs.map { dir ->
            val files = dir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .toList()

            var remaining = 0
            var total = 0
            for (file in files) {
                val text = file.readText()
                val helperHits = helperDefinitionRegex.findAll(text).count()
                val allHits = placeholderRegex.findAll(text).count()
                val callHits = (allHits - helperHits).coerceAtLeast(0)
                remaining += callHits

                val testCount = testRegex.findAll(text).count()
                // Conservative total: at least one slot per @Test, plus any extra
                // placeholders we still see. Stays sensible whether the file is fresh,
                // partly done, or completed.
                total += maxOf(testCount, callHits)
            }

            Stats(
                journey = dir.name,
                files = files.size,
                remaining = remaining,
                total = total,
            )
        }

        val barWidth = 24
        val nameWidth = (stats.maxOfOrNull { it.journey.length } ?: 10).coerceAtLeast(10)

        println()
        println("Tutorial Progress")
        println("=".repeat(nameWidth + barWidth + 30))
        var grandRemaining = 0
        var grandTotal = 0
        for (s in stats) {
            grandRemaining += s.remaining
            grandTotal += s.total
            val done = s.total - s.remaining
            val ratio = if (s.total == 0) 1.0 else done.toDouble() / s.total
            val filled = (ratio * barWidth).toInt().coerceIn(0, barWidth)
            val bar = "[" + "#".repeat(filled) + "-".repeat(barWidth - filled) + "]"
            val pct = (ratio * 100).toInt()
            val line = String.format(
                "  %-${nameWidth}s  %s  %3d%%   %d/%d done   (%d files)",
                s.journey, bar, pct, done, s.total, s.files,
            )
            println(line)
        }
        println("-".repeat(nameWidth + barWidth + 30))
        val grandDone = grandTotal - grandRemaining
        val grandPct = if (grandTotal == 0) 100 else (grandDone * 100 / grandTotal)
        println(
            "  %-${nameWidth}s  %d/%d done overall (%d%%)".format(
                "TOTAL", grandDone, grandTotal, grandPct,
            ),
        )
        println()
        println("Tip: run ./gradlew :hkj-examples:tutorialTest --tests \"*<JourneyName>*\"")
        println("     to focus on a single journey.")
    }
}
