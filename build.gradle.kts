plugins {
  id("java")
  id("jacoco")
  id("com.diffplug.spotless") version "6.25.0"
}

group = "org.simulation.hkt"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
  implementation("org.jspecify:jspecify:1.0.0")
  testImplementation(platform("org.junit:junit-bom:5.10.2"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.assertj:assertj-core:3.25.3")
}

java {
  // Ensure consistent Java version
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

// Spotless configuration block
spotless {
  // Optional: Configure line endings
  lineEndings = com.diffplug.spotless.LineEnding.UNIX

  java {
    // Apply to all Java files in src/main and src/test
    target("src/**/*.java")

    // Use Google Java Format
    googleJavaFormat("1.26.0")
        // .aosp() // Use AOSP style instead of Google Style (optional)
        // .style("AOSP") // Alternative way to specify AOSP style
        .reflowLongStrings() // Optional: Reflow long string literals
        .formatJavadoc(true) // Optional: default is true, set false to disable javadoc formatting

    removeUnusedImports()
    trimTrailingWhitespace()
    // endWithNewline() // Usually handled by googleJavaFormat

    // Optional: Add license header
    // licenseHeaderFile(rootProject.file("config/spotless/copyright.txt"),
    // "(package|import|public|@)")
  }

  // Optional: Configure formatting for Gradle files themselves
  kotlinGradle {
    target("*.gradle.kts", "settings.gradle.kts")
    ktfmt("0.47") // Use ktfmt for Kotlin files
  }
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)

  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }

  val mainSourceSet = sourceSets.main.get()
  val compiledClasses = mainSourceSet.output.classesDirs
  classDirectories.setFrom(
      files(
          compiledClasses.map { dir ->
            fileTree(dir).apply {
              exclude(
                  "**/example/**", // Exclude the example package
                  "**/*Kind.class", // Exclude Kind interfaces
                  "**/*Holder*.class" // Exclude internal holders
                  )
            }
          }))
  sourceDirectories.setFrom(files(mainSourceSet.allSource.srcDirs))
}
