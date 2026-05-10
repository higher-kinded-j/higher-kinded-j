import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
  jacoco
}

dependencies {
  api(project(":hkj-core"))
  api(libs.assertj.core)

  // Test scope hosts the assertion-usage examples that double as regression
  // tests for the assertion API.
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

// Coverage of hkj-test is measured against its own tests only.
// The contract tests in src/test/java exercise every assertion path explicitly.
tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }
}

// Enforce 100% line + instruction coverage on hkj-test's published code.
// Every assertion class has a dedicated *AssertContractTest that exercises
// each public method on at least one passing input and one failing input
// via the AssertContract<S, A> base class. New assertion methods that
// aren't covered by a contract row will fail this gate.
tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.jacocoTestReport)
  executionData.setFrom(tasks.jacocoTestReport.get().executionData)
  violationRules {
    rule {
      element = "BUNDLE"
      limit {
        counter = "INSTRUCTION"
        minimum = "1.0".toBigDecimal()
      }
      limit {
        counter = "LINE"
        minimum = "1.0".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.javadoc {
  (options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  configure(JavaLibrary(
    javadocJar = JavadocJar.Javadoc(),
    sourcesJar = SourcesJar.Sources(),
  ))

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-test",
    version = project.version.toString()
  )

  pom {
    name.set("Higher-Kinded-J Test Assertions")
    description.set("Custom AssertJ assertions for Higher-Kinded-J types (Either, Maybe, Try, IO, etc.)")
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
