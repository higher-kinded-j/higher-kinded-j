plugins {
  `java-library`
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

  // Spring Web MVC (optional - for web integration)
  compileOnly(libs.spring.web)
  compileOnly(libs.spring.webmvc)
  compileOnly(libs.jakarta.servlet.api)

  // Jackson (optional - for JSON serialization)
  compileOnly(libs.jackson.databind)

  // Spring Boot Actuator (optional - for metrics and health indicators)
  compileOnly(libs.spring.boot.actuator)
  compileOnly(libs.spring.boot.actuator.autoconfigure)
  compileOnly(libs.micrometer.core)

  // Spring Security (optional - for security integration)
  compileOnly(libs.spring.security.core)
  compileOnly(libs.spring.security.web)
  compileOnly(libs.spring.security.config)
  compileOnly(libs.spring.security.oauth2.jose)

  // Testing
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)

  // Test dependencies for optional features
  testImplementation(libs.spring.web)
  testImplementation(libs.spring.webmvc)
  testImplementation(libs.jakarta.servlet.api)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.spring.boot.actuator)
  testImplementation(libs.spring.boot.actuator.autoconfigure)
  testImplementation(libs.micrometer.core)
  testImplementation(libs.spring.security.core)
  testImplementation(libs.spring.security.web)
  testImplementation(libs.spring.security.config)
  testImplementation(libs.spring.security.oauth2.jose)
  testImplementation(libs.archunit.junit5)
}

tasks.test {
  useJUnitPlatform()
}
