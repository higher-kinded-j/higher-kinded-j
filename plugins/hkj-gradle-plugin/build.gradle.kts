plugins {
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish")
}

gradlePlugin {
    plugins {
        create("hkj") {
            id = "io.github.higher-kinded-j.hkj"
            implementationClass = "org.higherkindedj.gradle.HKJPlugin"
            displayName = "Higher-Kinded-J Plugin"
            description = "Configures HKJ dependencies, preview features, and compile-time checks"
        }
    }
}

dependencies {
    // Test dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Central configuration for publishing
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-gradle-plugin",
        version = project.version.toString()
    )

    pom {
        name.set("Higher-Kinded-J Gradle Plugin")
        description.set("Gradle plugin that configures HKJ dependencies, preview features, and compile-time checks")
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
