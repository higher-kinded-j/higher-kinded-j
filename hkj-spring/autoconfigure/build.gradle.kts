plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

dependencies {
  // Higher-Kinded-J core dependencies
  api(project(":hkj-core"))
  api(project(":hkj-api"))

  // Spring Boot auto-configuration
  implementation(platform(libs.spring.boot.bom))
  implementation(libs.spring.boot.autoconfigure)
  annotationProcessor(platform(libs.spring.boot.bom))
  annotationProcessor(libs.spring.boot.autoconfigure.processor)
  annotationProcessor(libs.spring.boot.configuration.processor)

  // Apply BOM to compileOnly configuration for version management
  compileOnly(platform(libs.spring.boot.bom))

  // Spring Web MVC (optional - for web integration)
  compileOnly(libs.bundles.spring.web)

  // Jackson (optional - for JSON serialization)
  compileOnly(libs.jackson.databind)
  compileOnly(libs.spring.boot.jackson)

  // Spring Boot Actuator (optional - for metrics and health indicators)
  compileOnly(libs.bundles.spring.actuator)

  // Spring Security (optional - for security integration)
  compileOnly(libs.bundles.spring.security)

  // SLF4J for logging (optional - used by return value handlers)
  compileOnly("org.slf4j:slf4j-api")

  // Testing
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)

  // Test dependencies for optional features
  testImplementation(libs.bundles.spring.web)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.spring.boot.jackson)
  testImplementation(libs.bundles.spring.actuator)
  testImplementation(libs.bundles.spring.security)
  testImplementation(libs.archunit.junit5)
}

tasks.test {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-spring-boot-autoconfigure",
    version = project.version.toString()
  )

  pom {
    name.set("Higher-Kinded-J Spring Boot Auto-Configuration")
    description.set("Spring Boot auto-configuration for Higher-Kinded-J - Automatic setup for functional programming patterns")
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
