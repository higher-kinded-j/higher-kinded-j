plugins {
  `java-library`
  id("org.springframework.boot") version "3.5.7"
  id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
  // Higher-Kinded-J Spring Boot Starter
  implementation(project(":hkj-spring:starter"))

  // Annotation processing for optics generation
  annotationProcessor(project(":hkj-processor"))
  annotationProcessor(project(":hkj-processor-plugins"))

  // Spring Boot Web (already included via starter, but explicit for clarity)
  implementation("org.springframework.boot:spring-boot-starter-web")

  // Spring Boot Actuator for metrics and health endpoints
  implementation("org.springframework.boot:spring-boot-starter-actuator")

  // Testing
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(platform("org.junit:junit-bom:5.13.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
  useJUnitPlatform()
}
