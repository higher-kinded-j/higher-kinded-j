plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

// Central configuration for publishing. This is inherited by all submodules
// that apply the 'com.vanniktech.maven.publish' plugin.
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-annotations",
        version = project.version.toString()
    )

    // POM details are defined once and inherited by all published submodules.
    pom {
        name.set("Higher-Kinded-J Annotations")
        description.set("Annotations for Higher-Kinded-J code generation, including optics (@GenerateLenses, @GeneratePrisms, @GenerateTraversals, @GenerateIsos) and data mapping.")
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
        inceptionYear.set("2025")
        organization {
            name.set("The Higher-Kinded-J Team")
            url.set("https://github.com/higher-kinded-j")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/higher-kinded-j/higher-kinded-j/issues")
        }
    }

}