plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

dependencies {
  // Higher-Kinded-J core dependencies (Effect Paths, Either, resilience primitives)
  api(project(":hkj-core"))
  api(project(":hkj-api"))

  // Spring Web (optional - provides RestClient, @HttpExchange, ResponseEntity,
  // RestClientResponseException). Supplied by the application/starter at runtime.
  compileOnly(platform(libs.spring.boot.bom))
  compileOnly(libs.spring.web)

  // Spring Boot auto-configuration (optional - for the default decoder-factory bean)
  compileOnly(libs.spring.boot.autoconfigure)
  annotationProcessor(platform(libs.spring.boot.bom))
  annotationProcessor(libs.spring.boot.autoconfigure.processor)
  annotationProcessor(libs.spring.boot.configuration.processor)

  // Jackson (optional - for the default JSON error decoder)
  compileOnly(libs.jackson.databind)
  compileOnly(libs.spring.boot.jackson)

  // Testing
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.web)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.spring.boot.jackson)
  testImplementation(project(":hkj-test"))
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-spring-boot-client",
    version = project.version.toString()
  )

  pom {
    name.set("Higher-Kinded-J Spring Boot Client")
    description.set(
      "Client-side HTTP integration for Higher-Kinded-J - declarative HTTP clients that " +
        "return Effect Paths and preserve the typed error channel across services")
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
