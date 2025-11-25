plugins {
  `java-library`
  id("me.champeau.jmh") version "0.7.3"
}

dependencies {
  // Depend on core module for benchmarking
  implementation(project(":hkj-core"))
  implementation(project(":hkj-api"))

  // JMH dependencies are provided by the plugin
  jmhImplementation("org.openjdk.jmh:jmh-core:1.37")
  jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

  // Optional: Java Object Layout for allocation analysis
  jmhImplementation("org.openjdk.jol:jol-core:0.17")
}

jmh {
  // Number of measurement iterations
  iterations = 10

  // Number of warmup iterations (JIT compilation)
  warmupIterations = 5

  // Number of forks (separate JVM processes)
  fork = 2

  // JVM arguments for benchmarks
  jvmArgs = listOf("-Xms2G", "-Xmx2G")

  // Output format
  resultFormat = "JSON"

  // Results file
  resultsFile = project.file("${project.layout.buildDirectory.get()}/reports/jmh/results.json")

  // Human-readable output
  humanOutputFile = project.file("${project.layout.buildDirectory.get()}/reports/jmh/human.txt")

  // Fail on error
  failOnError = true

  // Enable GC profiling (can be overridden from command line)
  // profilers = listOf("gc")
}

// Ensure Java version matches the rest of the project
java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

tasks {
  // Create a task to run benchmarks and show results
  register("benchmarkReport") {
    dependsOn("jmh")
    doLast {
      val resultsFile = file("${layout.buildDirectory.get()}/reports/jmh/results.json")
      val humanFile = file("${layout.buildDirectory.get()}/reports/jmh/human.txt")

      if (humanFile.exists()) {
        println("\n=== Benchmark Results ===")
        println(humanFile.readText())
      }

      if (resultsFile.exists()) {
        println("\nDetailed JSON results: ${resultsFile.absolutePath}")
      }
    }
  }
}
