plugins {
    id("java")
    id("jacoco")
}

group = "org.simulation.hkt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

java {
    // Ensure consistent Java version
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
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

    val mainSourceSet = sourceSets.main.get()
    val compiledClasses = mainSourceSet.output.classesDirs
    classDirectories.setFrom(files(compiledClasses.map { dir ->
        fileTree(dir).apply {
            exclude(
                "**/example/**", // Exclude the example package if desired
                "**/MonadSimulation.class",
                "**/*Kind.class", // Exclude Kind interfaces
                "**/*KindHelper*.class", // Exclude helpers
                "**/*Holder*.class" // Exclude internal holders

            )
        }
    }))
    sourceDirectories.setFrom(files(mainSourceSet.allSource.srcDirs))
}