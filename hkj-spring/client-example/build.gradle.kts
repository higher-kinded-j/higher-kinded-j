plugins {
  `java-library`
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
}

dependencies {
  // Higher-Kinded-J Spring Boot Starter — brings the @HkjHttpClient runtime, spring-boot-restclient
  // (binds spring.http.serviceclient.*), and a Jackson mapper.
  implementation(project(":hkj-spring:starter"))

  // Generates the @HkjHttpClient siblings (UserClientApiHttpExchange / …Client / …Configuration)
  // for UserClientApi in this module.
  annotationProcessor(project(":hkj-spring:client-processor"))

  // Testing
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.web)
  testImplementation(project(":hkj-test"))
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
}

tasks.test {
  useJUnitPlatform()
}
