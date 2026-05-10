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
}

tasks.named("javadoc") {
    enabled = false
}

// Default test task: runs solution tests only (tutorials are excluded)
// Solutions must pass as they verify the tutorial exercises are correct
tasks.named<Test>("test") {
    useJUnitPlatform()
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
