plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

dependencies {
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    compileOnly(libs.javax.inject)

    // Test dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.maven.core)
    testImplementation(libs.maven.plugin.api)
}

// Bundle Claude Code skill files so the plugin can install them into consumer projects
val bundleSkills = tasks.register("bundleSkills") {
    val skillsSourceDir = rootProject.layout.projectDirectory.dir(".claude/skills")
    val outputDir = layout.buildDirectory.dir("generated/resources/hkj-skills")
    inputs.dir(skillsSourceDir).optional()
    outputs.dir(outputDir)
    doLast {
        val srcDir = skillsSourceDir.asFile
        val outDir = outputDir.get().asFile.resolve("META-INF/hkj-skills")
        outDir.mkdirs()
        val manifest = mutableListOf<String>()
        if (srcDir.exists()) {
            srcDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".md") }
                .forEach { file ->
                    val relativePath = file.relativeTo(srcDir).invariantSeparatorsPath
                    if (relativePath.startsWith("hkj-")) {
                        val targetFile = outDir.resolve(relativePath)
                        targetFile.parentFile.mkdirs()
                        file.copyTo(targetFile, overwrite = true)
                        manifest.add(relativePath)
                    }
                }
        }
        outDir.resolve("manifest.txt").writeText(manifest.sorted().joinToString("\n") + "\n")
    }
}

sourceSets.main {
    resources.srcDir(bundleSkills)
}

// Maven plugin classes must not use --enable-preview so they can load in any Java 25+ JVM
tasks.withType<JavaCompile>().configureEach {
    doFirst {
        options.compilerArgs.removeIf { it == "--enable-preview" }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests don't need preview features either
    doFirst {
        jvmArgs?.removeIf { it == "--enable-preview" }
    }
}

tasks.withType<JavaExec>().configureEach {
    doFirst {
        jvmArgs?.removeIf { it == "--enable-preview" }
    }
}

// Central configuration for publishing
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "hkj-maven-plugin",
        version = project.version.toString()
    )

    pom {
        name.set("Higher-Kinded-J Maven Plugin")
        description.set("Maven plugin that configures HKJ dependencies, preview features, and compile-time checks")
        packaging = "maven-plugin"
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
