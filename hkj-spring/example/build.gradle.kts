plugins {
  `java-library`
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

dependencies {
  // Higher-Kinded-J Spring Boot Starter
  implementation(project(":hkj-spring:starter"))

  // Annotation processing for optics generation and @HkjHttpClient client generation
  annotationProcessor(project(":hkj-processor"))
  annotationProcessor(project(":hkj-processor-plugins"))
  annotationProcessor(project(":hkj-spring:client-processor"))

  // Spring Boot Web (already included via starter, but explicit for clarity)
  implementation(libs.spring.boot.starter.web)

  // Spring Boot Actuator for metrics and health endpoints
  implementation(libs.spring.boot.starter.actuator)

  // Testing
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.webmvc.test)
  testImplementation(project(":hkj-test"))
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}

tasks.test {
  useJUnitPlatform()
}
