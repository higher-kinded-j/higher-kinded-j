import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
    jacoco
}

dependencies {
  // API dependencies
  api(libs.jspecify)
  api(project(":hkj-api"))
  api(project(":hkj-annotations"))

  annotationProcessor(project(":hkj-processor"))

  // Testing dependencies
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)
  testImplementation(libs.awaitility)
  testImplementation(libs.bundles.jqwik)
  testImplementation(libs.archunit.junit5)
}


tasks.javadoc {
  // Ensure annotation processing runs first so generated classes (Tuple2-12,
  // MonadicSteps2-12, FilterableSteps2-12, *PathSteps2, etc.) are available.
  dependsOn(tasks.compileJava)

  // Include both hand-written and annotation-processor-generated sources
  source = sourceSets.main.get().allJava
  val generatedSources = layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main")
  source(generatedSources)

  // Add compiled classes to classpath so javadoc can resolve all symbols
  classpath = files(sourceSets.main.get().compileClasspath, sourceSets.main.get().output.classesDirs)

  exclude("org/higherkindedj/example/**")
  (options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}
tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
 // jvmArgs = listOf("--add-opens", "org.higherkindedj.hkj/org.higherkindedj.internal=ALL-UNNAMED")

  // Note: Parallel execution disabled due to:
  // - Performance test timing instability
  // - Thread-safety issues in some test fixtures (Lazy memoization)
  // - Performance testing should be handled by hkj-benchmarks module
  // Future: May re-enable with @Execution(CONCURRENT) opt-in per test class
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)

  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }

  // Include both hkj-core and hkj-api classes for coverage measurement
  val coreSourceSet = sourceSets.main.get()
  val apiProject = project(":hkj-api")

  // Combine class directories from both modules
  classDirectories.setFrom(
      files(
          // hkj-core classes (with existing exclusions)
          coreSourceSet.output.classesDirs.map { dir ->
            fileTree(dir).apply {
              exclude(
                  "**/StateTupleLenses.class", // Exclude the example package
                  "**/*Kind.class",
                  "**/FunctionKind.Witness.class",
                  "**/*Holder*.class"
              )
            }
          },
          // hkj-api classes (interface default methods we want to cover)
          apiProject.sourceSets.main.get().output.classesDirs.map { dir ->
            fileTree(dir).apply {
              exclude(
                  // Exclude default methods that are overridden by ALL implementations
                  // (and thus unreachable through hkj-core tests)
                  // Add entries here as identified by DefaultMethodCoverageRules
              )
            }
          }
      )
  )

  // Combine source directories from both modules for accurate source linking
  sourceDirectories.setFrom(
      files(
          coreSourceSet.allSource.srcDirs,
          apiProject.sourceSets.main.get().allSource.srcDirs
      )
  )
}



// Central configuration for publishing. This is inherited by all submodules
// that apply the 'com.vanniktech.maven.publish' plugin.
mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  configure(JavaLibrary(
    // - `JavadocJar.Javadoc()` to publish standard javadocs
    javadocJar = JavadocJar.Javadoc(),
    // whether to publish a sources jar
    sourcesJar = true,
  ))

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-core",
    version = project.version.toString()
  )

  // POM details are defined once and inherited by all published submodules.
  pom {
    name.set("Higher-Kinded-J")
    description.set("Bringing Higher-Kinded Types to Java Functional Patterns - Core")
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




