plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
    jacoco
}

dependencies {
    // Depends on the processor to get the SPI interface
    implementation(project(":hkj-processor"))
    // Depends on the core library for the types the generators support
    implementation(project(":hkj-core"))
    implementation(libs.javapoet)
    implementation(libs.palantir.java.format)
    /*
     * The Avaje SPI (https://avaje.io/spi/) annotation processor help manage
     *   SPI implementations. It does this by:
     * 1. Automatically generating the META-INF/services files
     * 2. Throwing a compile error if the `module-info` is missing an
     *      implementation, with the error description containing
     *      the full expected `provides` declaration that can be
     *      copy and pasted into the `module-info.java` file
     */
    compileOnly(libs.avaje.spi)
    annotationProcessor(libs.avaje.spi)

    testImplementation(libs.compile.testing)
    testImplementation(libs.truth)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)

    // Optionally supported types
    testImplementation(libs.eclipse.collections)
    testImplementation(libs.google.guava)
    testImplementation(libs.vavr)
    testImplementation(libs.apache.commons.collections4)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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
