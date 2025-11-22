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

  // Testing
  testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation(platform("org.junit:junit-bom:5.13.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
}
