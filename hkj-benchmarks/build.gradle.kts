plugins {
  `java-library`
  alias(libs.plugins.jmh)
}

dependencies {
  // Depend on core module for benchmarking
  implementation(project(":hkj-core"))
  implementation(project(":hkj-api"))

  // JMH dependencies are provided by the plugin
  jmhImplementation(libs.jmh.core)
  jmhAnnotationProcessor(libs.jmh.generator.annprocess)

  // Optional: Java Object Layout for allocation analysis
  jmhImplementation(libs.jol.core)

  // Test dependencies for benchmark assertions
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testImplementation("com.google.code.gson:gson:2.11.0")
  testRuntimeOnly(libs.junit.platform.launcher)
}

// Enable preview features for JMH compilation (required for VTask benchmarks)
tasks.named<JavaCompile>("compileJmhJava") {
  options.compilerArgs.addAll(listOf("--enable-preview"))
}

// Enable preview features for JMH bytecode generator (runs as separate JVM process)
tasks.named("jmhRunBytecodeGenerator") {
  (this as me.champeau.jmh.JmhBytecodeGeneratorTask).jvmArgs.add("--enable-preview")
}

// Enable preview features for JMH annotation processing
tasks.named<JavaCompile>("jmhCompileGeneratedClasses") {
  options.compilerArgs.addAll(listOf("--enable-preview"))
}

// Enable JUnit 6 for test discovery
tasks.test {
  useJUnitPlatform()
}

jmh {
  // Optimized for fast feedback (~1 minute total)
  // For rigorous benchmarking, use: ./gradlew jmh -Pjmh.iterations=5 -Pjmh.warmupIterations=3
  iterations = 1
  warmupIterations = 1
  fork = 1
  timeOnIteration = "200ms"
  warmup = "200ms"

  // JVM arguments for benchmarks (including preview features for VTask)
  jvmArgs = listOf("-Xms512M", "-Xmx512M", "--enable-preview")

  // Output format
  resultFormat = "JSON"

  // Results file
  resultsFile = project.file("${project.layout.buildDirectory.get()}/reports/jmh/results.json")

  // Human-readable output
  humanOutputFile = project.file("${project.layout.buildDirectory.get()}/reports/jmh/human.txt")

  // Fail on error
  failOnError = true

  // Configure benchmark parameters based on project property
  // Usage: ./gradlew :hkj-benchmarks:jmh -PbenchmarkMode=long
  val benchmarkMode = findProperty("benchmarkMode")?.toString() ?: "short"
  if (benchmarkMode == "long") {
    benchmarkParameters.put("chainDepth", project.objects.listProperty<String>().value(listOf("10000")))
    benchmarkParameters.put("recursionDepth", project.objects.listProperty<String>().value(listOf("10000")))
  }
  // Short mode uses default @Param values (chainDepth=50, recursionDepth=100)
}

tasks {
  // Short benchmarks for quick local feedback (default depths)
  register("shortBenchmark") {
    group = "benchmark"
    description = "Run benchmarks with small chain/recursion depths for quick feedback"
    dependsOn("jmh")
    doFirst {
      println("Running SHORT benchmarks (chainDepth=50, recursionDepth=100)")
      println("Results will be in: build/reports/jmh/")
    }
  }

  // Long benchmarks for thorough stack-safety validation
  // Usage: ./gradlew :hkj-benchmarks:longBenchmark
  register<Exec>("longBenchmark") {
    group = "benchmark"
    description = "Run benchmarks with large chain/recursion depths for stress testing"
    workingDir = rootProject.projectDir
    commandLine(
      "./gradlew",
      ":hkj-benchmarks:jmh",
      "-PbenchmarkMode=long"
    )
    doFirst {
      println("Running LONG benchmarks (chainDepth=10000, recursionDepth=10000)")
      println("This will take longer but provides thorough stack-safety validation")
      println("Results will be in: build/reports/jmh/")
    }
  }

  // Create a task to run benchmarks and show formatted results
  register("benchmarkReport") {
    dependsOn("jmh")
    doLast {
      val resultsFile = file("${layout.buildDirectory.get()}/reports/jmh/results.json")

      if (resultsFile.exists()) {
        @Suppress("UNCHECKED_CAST")
        val results = groovy.json.JsonSlurper().parse(resultsFile) as List<Map<String, Any>>

        println("\n${"=".repeat(90)}")
        println("  JMH BENCHMARK RESULTS")
        println("${"=".repeat(90)}\n")

        // Group by benchmark class
        val grouped = results.groupBy { result ->
          val benchmark = result["benchmark"] as String
          benchmark.substringBeforeLast(".")
        }

        grouped.forEach { (className, benchmarks) ->
          val shortName = className.substringAfterLast(".")
          println("┌${"─".repeat(88)}┐")
          println("│ %-86s │".format(shortName))
          println("├${"─".repeat(44)}┬${"─".repeat(21)}┬${"─".repeat(20)}┤")
          println("│ %-42s │ %19s │ %18s │".format("Benchmark", "Score", "Error (±)"))
          println("├${"─".repeat(44)}┼${"─".repeat(21)}┼${"─".repeat(20)}┤")

          benchmarks.sortedBy { it["benchmark"] as String }.forEach { result ->
            val benchmark = (result["benchmark"] as String).substringAfterLast(".")
            @Suppress("UNCHECKED_CAST")
            val primaryMetric = result["primaryMetric"] as Map<String, Any>
            val score = (primaryMetric["score"] as Number).toDouble()
            val scoreError = (primaryMetric["scoreError"] as Number).toDouble()
            val scoreUnit = primaryMetric["scoreUnit"] as String

            val formattedScore = when {
              score >= 1_000_000 -> "%.2f M".format(score / 1_000_000)
              score >= 1_000 -> "%.2f K".format(score / 1_000)
              else -> "%.2f".format(score)
            }

            val formattedError = when {
              scoreError.isNaN() -> "NaN"
              scoreError >= 1_000_000 -> "%.2f M".format(scoreError / 1_000_000)
              scoreError >= 1_000 -> "%.2f K".format(scoreError / 1_000)
              else -> "%.2f".format(scoreError)
            }

            val unit = scoreUnit.replace("ops/", "/")
            println("│ %-42s │ %15s %3s │ %14s %3s │".format(
              benchmark.take(42),
              formattedScore,
              unit,
              formattedError,
              unit
            ))
          }
          println("└${"─".repeat(44)}┴${"─".repeat(21)}┴${"─".repeat(20)}┘")
          println()
        }

        println("Mode: Throughput (higher is better)")
        println("JSON results: ${resultsFile.absolutePath}")
      } else {
        println("No results found. Run './gradlew :hkj-benchmarks:jmh' first.")
      }
    }
  }

  // Quick summary task (doesn't re-run benchmarks)
  register("benchmarkSummary") {
    doLast {
      val resultsFile = file("${layout.buildDirectory.get()}/reports/jmh/results.json")

      if (resultsFile.exists()) {
        @Suppress("UNCHECKED_CAST")
        val results = groovy.json.JsonSlurper().parse(resultsFile) as List<Map<String, Any>>

        println("\n=== Benchmark Summary ===\n")
        println("%-50s %15s".format("Benchmark", "ops/μs"))
        println("-".repeat(67))

        results.sortedByDescending {
          @Suppress("UNCHECKED_CAST")
          ((it["primaryMetric"] as Map<String, Any>)["score"] as Number).toDouble()
        }.forEach { result ->
          val benchmark = (result["benchmark"] as String)
            .removePrefix("org.higherkindedj.benchmarks.")
          @Suppress("UNCHECKED_CAST")
          val score = ((result["primaryMetric"] as Map<String, Any>)["score"] as Number).toDouble()

          val formattedScore = when {
            score >= 1_000_000 -> "%,.0f M".format(score / 1_000_000)
            score >= 1_000 -> "%,.0f K".format(score / 1_000)
            else -> "%,.2f".format(score)
          }
          println("%-50s %15s".format(benchmark.take(50), formattedScore))
        }
      } else {
        println("No results found. Run './gradlew :hkj-benchmarks:jmh' first.")
      }
    }
  }
}
