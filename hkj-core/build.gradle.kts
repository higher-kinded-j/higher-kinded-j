import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
  `java-library`
  id("com.vanniktech.maven.publish")
    jacoco
}

dependencies {


//  // API dependencies
  api("org.jspecify:jspecify:1.0.0")
  api(project(":hkj-api"))
  api(project(":hkj-annotations"))

  annotationProcessor(project(":hkj-processor"))
  //annotationProcessor(project(":hkj-processor-plugins"))

  // Testing dependencies
  testImplementation(platform("org.junit:junit-bom:5.13.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.assertj:assertj-core:3.27.3")
}


tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
  // Jacoco configuration remains specific to this module's source code
}

tasks.javadoc {
  source = sourceSets.main.get().allJava
  exclude("org/higherkindedj/example/**")
  (options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}
tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
 // jvmArgs = listOf("--add-opens", "org.higherkindedj.hkj/org.higherkindedj.internal=ALL-UNNAMED")
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)

  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }

  val mainSourceSet = sourceSets.main.get()
  val compiledClasses = mainSourceSet.output.classesDirs
  classDirectories.setFrom(
      files(
          compiledClasses.map { dir ->
            fileTree(dir).apply {
              exclude(
                  "**/StateTupleLenses.class", // Exclude the example package
                  "**/*Kind.class",
                  "**/FunctionKind.Witness.class",
                  "**/*Holder*.class"
                  )
            }
          }))
  sourceDirectories.setFrom(files(mainSourceSet.allSource.srcDirs))
}



// Central configuration for publishing. This is inherited by all submodules
// that apply the 'com.vanniktech.maven.publish' plugin.
mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  configure(JavaLibrary(
    // - `JavadocJar.Javadoc()` to publish standard javadocs
    javadocJar = JavadocJar.Javadoc(),
    // whether to publish a sources jar
    sourcesJar = true,
  ))

  coordinates(
    groupId = project.group.toString(),
    artifactId = "hkj-core",
    version = project.version.toString()
  )

  // POM details are defined once and inherited by all published submodules.
  pom {
    name.set("Higher-Kinded-J")
    description.set("Bringing Higher-Kinded Types to Java Functional Patterns - Core")
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




