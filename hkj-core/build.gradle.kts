plugins {
    id("java-library")
    id("maven-publish")
    id("jacoco")
}

// group and version might be inherited from root or set here
version = project.findProperty("projectVersion")?.toString() ?: "v0.1.3-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")

    // Add the annotation processor
    annotationProcessor(project(":hkj-processor"))

    // If your core code (e.g., WorkflowAliases) directly uses annotations like @GenerateHKTAlias
    // from the processor module, you also need an implementation or api dependency.
    implementation(project(":hkj-processor"))

    testImplementation(platform("org.junit:junit-bom:5.10.2")) //
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
    withSourcesJar()
    withJavadocJar()
}

// Spotless configuration might be inherited or defined here if not in root.
// Ensure it's applied correctly.

tasks.test { //
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs = listOf("--add-opens", "org.higherkindedj.core/org.higherkindedj.internal=ALL-UNNAMED")
}

tasks.jacocoTestReport { //
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
                        "**/example/**",
                        "**/*Kind.class",
                        "**/*Holder*.class"
                    )
                }
            }
        )
    )
    sourceDirectories.setFrom(files(mainSourceSet.allSource.srcDirs))
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Higher-Kinded-J Core")
                description.set("Bringing Higher-Kinded Types to Java Functional Patterns - Core Library")
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
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/higher-kinded-j/higher-kinded-j")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}