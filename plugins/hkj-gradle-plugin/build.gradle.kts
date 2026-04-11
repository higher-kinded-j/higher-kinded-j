plugins {
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.plugin.publish)
}

gradlePlugin {
    website.set("https://github.com/higher-kinded-j/higher-kinded-j")
    vcsUrl.set("https://github.com/higher-kinded-j/higher-kinded-j")
    plugins {
        create("hkj") {
            id = "io.github.higher-kinded-j.hkj"
            implementationClass = "org.higherkindedj.gradle.HKJPlugin"
            displayName = "Higher-Kinded-J Plugin"
            description = "Configures HKJ dependencies, preview features, and compile-time checks"
            tags.set(listOf("higher-kinded-types", "functional-programming", "java"))
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

// Only require signing when credentials are available (not in CI builds without secrets)
tasks.withType<Sign>().configureEach {
    isRequired = providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey")
        .map { it.isNotBlank() }
        .getOrElse(false)
}

// Generate version.properties so the plugin knows its own version at runtime
val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources/hkj")
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("hkj-version.properties").writeText("version=$versionValue\n")
    }
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
        // Generate manifest listing all skill files
        val manifest = mutableListOf<String>()
        if (srcDir.exists()) {
            srcDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".md") }
                .forEach { file ->
                    val relativePath = file.relativeTo(srcDir).path
                    // Only include hkj-* skill directories
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
    resources.srcDir(generateVersionProperties)
    resources.srcDir(bundleSkills)
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
