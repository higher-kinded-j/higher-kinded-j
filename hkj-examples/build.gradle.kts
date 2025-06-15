plugins {
    `maven-publish`
    signing
    id("net.thebugmc.gradle.sonatype-central-portal-publisher") version "1.2.4"
}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor"))
}

val isSnapshotVersion: Boolean by rootProject.extra
val isReleaseBuild: Boolean by rootProject.extra

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("HKJ Examples")
                description.set("Examples for the Higher-Kinded-J")
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
    }
    repositories {
        if (isSnapshotVersion) {
            maven {
                name = "CentralPortalSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                credentials {
                    // These would be your Central Portal User Code and Passcode
                    username = System.getenv("CENTRAL_PORTAL_USERNAME")
                    password = System.getenv("CENTRAL_PORTAL_PASSWORD")
                }
            }
        }
    }
}


signing {
    isRequired = isReleaseBuild
    if (isReleaseBuild) {
        sign(publishing.publications["mavenJava"])
    }
}

if (!isSnapshotVersion) {
    centralPortal {
        username.set(System.getenv("CENTRAL_PORTAL_USERNAME"))
        password.set(System.getenv("CENTRAL_PORTAL_PASSWORD"))

        publishingType = net.thebugmc.gradle.sonatypepublisher.PublishingType.AUTOMATIC

        pom {
            name.set("Higher-Kinded-J API")
            description.set("Bringing Higher-Kinded Types to Java Functional Patterns - API")
            url.set("https://github.com/higher-kinded-j/higher-kinded-j")

            licenses {
                license {
                    name.set("The MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
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
}
