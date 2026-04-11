plugins {
  `java-library`
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

dependencies {
  // Higher-Kinded-J Spring Boot Starter
  implementation(project(":hkj-spring:starter"))

  // Core for EffectBoundary, FreePath
  implementation(project(":hkj-core"))

  // Annotation processing for @EffectAlgebra code generation
  annotationProcessor(project(":hkj-processor"))
  annotationProcessor(project(":hkj-processor-plugins"))

  // Spring Boot Web
  implementation(libs.spring.boot.starter.web)

  // Spring Boot Actuator for metrics and health endpoints
  implementation(libs.spring.boot.starter.actuator)

  // Testing
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.webmvc.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}

tasks.test {
  useJUnitPlatform()
}
