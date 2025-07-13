plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

dependencies {
    // Depends on the processor to get the SPI interface
    implementation(project(":hkj-processor"))
    // Depends on the core library for the types the generators support
    implementation(project(":hkj-core"))
    implementation("com.palantir.javapoet:javapoet:0.7.0")
    implementation("com.palantir.javaformat:palantir-java-format:2.69.0")

    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    testImplementation("com.google.truth:truth:1.4.4")


    testImplementation(platform("org.junit:junit-bom:5.13.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.test {
    useJUnitPlatform()
}

// Central configuration for publishing. This is inherited by all submodules
// that apply the 'com.vanniktech.maven.publish' plugin.
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-processor-plugins",
        version = project.version.toString()
    )

    // POM details are defined once and inherited by all published submodules.
    pom {
        name.set("Higher-Kinded-J")
        description.set("Bringing Higher-Kinded Types to Java Functional Patterns - Annotation Processor Plugins")
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
