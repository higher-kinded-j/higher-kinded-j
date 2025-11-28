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
  compileOnly(libs.bundles.spring.web)

  // Jackson (optional - for JSON serialization)
  compileOnly(libs.jackson.databind)

  // Spring Boot Actuator (optional - for metrics and health indicators)
  compileOnly(libs.bundles.spring.actuator)

  // Spring Security (optional - for security integration)
  compileOnly(libs.bundles.spring.security)

  // Testing
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)

  // Test dependencies for optional features
  testImplementation(libs.bundles.spring.web)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.bundles.spring.actuator)
  testImplementation(libs.bundles.spring.security)
  testImplementation(libs.archunit.junit5)
}

tasks.test {
  useJUnitPlatform()
}
