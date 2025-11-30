plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
}

dependencies {
  // Bring in the autoconfigure module
  api(project(":hkj-spring:autoconfigure"))

  // Spring Boot starter web (provides Spring MVC, Tomcat, Jackson)
  api(libs.spring.boot.starter.web)

  // Annotation processor for generating optics
  annotationProcessor(project(":hkj-processor"))
  annotationProcessor(project(":hkj-processor-plugins"))
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-spring-boot-starter",
    version = project.version.toString()
  )

  pom {
    name.set("Higher-Kinded-J Spring Boot Starter")
    description.set("Spring Boot starter for Higher-Kinded-J - Functional programming patterns for Spring applications")
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
