plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

dependencies {
  // The runtime module carries the @HkjHttpClient annotation and the Effect Path types
  // the processor inspects when generating clients.
  implementation(project(":hkj-spring:client"))
  implementation(project(":hkj-core"))
  implementation(project(":hkj-api"))

  // Code generation
  implementation(libs.javapoet)
  implementation(libs.autoservice.annotations)
  annotationProcessor(libs.autoservice)

  // Spring types referenced (by name) when generating clients
  compileOnly(platform(libs.spring.boot.bom))
  compileOnly(libs.spring.web)

  // Testing: compile-testing + Truth, mirroring hkj-processor. The full spring-web bundle is on
  // the test classpath so generated sources (which reference @Configuration/@Bean from
  // spring-context via spring-webmvc, plus @ImportHttpServices/ResponseEntity from spring-web)
  // compile in-process during the compile-testing run.
  testImplementation(libs.compile.testing)
  testImplementation(libs.truth)
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.bundles.spring.web)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)
}

tasks.test {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-spring-boot-client-processor",
    version = project.version.toString()
  )

  pom {
    name.set("Higher-Kinded-J Spring Boot Client Processor")
    description.set(
      "Annotation processor that generates Higher-Kinded-J Effect-Path HTTP clients from " +
        "@HkjHttpClient interfaces")
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
