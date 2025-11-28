plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

dependencies {
    // Depends on the processor to get the SPI interface
    implementation(project(":hkj-processor"))
    // Depends on the core library for the types the generators support
    implementation(project(":hkj-core"))
    implementation(libs.javapoet)
    implementation(libs.palantir.java.format)

    testImplementation(libs.compile.testing)
    testImplementation(libs.truth)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
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
