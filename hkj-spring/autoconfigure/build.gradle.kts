plugins {
  `java-library`
}

dependencies {
  // Higher-Kinded-J core dependencies
  api(project(":hkj-core"))
  api(project(":hkj-api"))

  // Spring Boot auto-configuration
  implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  // Spring Web MVC (optional - for web integration)
  compileOnly("org.springframework:spring-web")
  compileOnly("org.springframework:spring-webmvc")
  compileOnly("jakarta.servlet:jakarta.servlet-api")

  // Jackson (optional - for JSON serialization)
  compileOnly("com.fasterxml.jackson.core:jackson-databind")

  // Spring Boot Actuator (optional - for metrics and health indicators)
  compileOnly("org.springframework.boot:spring-boot-actuator")
  compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")
  compileOnly("io.micrometer:micrometer-core")

  // Spring Security (optional - for security integration)
  compileOnly("org.springframework.security:spring-security-core")
  compileOnly("org.springframework.security:spring-security-web")
  compileOnly("org.springframework.security:spring-security-config")
  compileOnly("org.springframework.security:spring-security-oauth2-jose")

  // Testing
  testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(platform("org.junit:junit-bom:5.13.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  // Test dependencies for optional features
  testImplementation("org.springframework:spring-web")
  testImplementation("org.springframework:spring-webmvc")
  testImplementation("jakarta.servlet:jakarta.servlet-api")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("org.springframework.boot:spring-boot-actuator")
  testImplementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
  testImplementation("io.micrometer:micrometer-core")
  testImplementation("org.springframework.security:spring-security-core")
  testImplementation("org.springframework.security:spring-security-web")
  testImplementation("org.springframework.security:spring-security-config")
  testImplementation("org.springframework.security:spring-security-oauth2-jose")
}

tasks.test {
  useJUnitPlatform()
}
