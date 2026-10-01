// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

dependencies {
    // OpenRewrite core dependencies
    compileOnly("org.openrewrite:rewrite-java:8.75.5")
    compileOnly("org.openrewrite:rewrite-core:8.75.5")

    // Required for recipe testing
    testImplementation("org.openrewrite:rewrite-java:8.75.5")
    testImplementation("org.openrewrite:rewrite-test:8.75.5")
    testImplementation("org.openrewrite:rewrite-java-25:8.75.5")

    // Testing dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}

// Central configuration for publishing. OpenRewrite itself stays compileOnly: the rewrite Gradle or
// Maven plugin that runs these recipes supplies it.
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-openrewrite",
        version = project.version.toString()
    )

    pom {
        name.set("Higher-Kinded-J OpenRewrite Recipes")
        description.set("OpenRewrite recipes for migrating between Higher-Kinded-J releases")
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
