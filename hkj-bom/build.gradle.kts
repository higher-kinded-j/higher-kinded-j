plugins {
    `java-platform`
    id("com.vanniktech.maven.publish")
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":hkj-core"))
        api(project(":hkj-api"))
        api(project(":hkj-annotations"))
        api(project(":hkj-processor-plugins"))
        api(project(":hkj-checker"))
        api(project(":hkj-spring:starter"))
        api(project(":hkj-spring:autoconfigure"))
        api(project(":hkj-openrewrite"))
    }
}

// Central configuration for publishing
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-bom",
        version = project.version.toString()
    )

    pom {
        name.set("Higher-Kinded-J BOM")
        description.set("Bill of Materials for Higher-Kinded-J, managing versions of all HKJ modules")
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
