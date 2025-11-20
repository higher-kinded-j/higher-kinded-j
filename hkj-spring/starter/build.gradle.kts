plugins {
  `java-library`
}

dependencies {
  // Bring in the autoconfigure module
  api(project(":hkj-spring:autoconfigure"))

  // Spring Boot starter web (provides Spring MVC, Tomcat, Jackson)
  api("org.springframework.boot:spring-boot-starter-web:3.5.7")

  // Annotation processor for generating optics
  annotationProcessor(project(":hkj-processor"))
  annotationProcessor(project(":hkj-processor-plugins"))
}
