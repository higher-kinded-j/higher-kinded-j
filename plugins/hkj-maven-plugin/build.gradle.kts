import java.util.zip.ZipFile

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

// Generate hkj-version.properties so the plugin knows its own version at runtime: without a
// <version> in its configuration, that is the library version it adds
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

// The annotation processors hkj-processor and the @HkjHttpClient processor register, read from
// their jars, so that a build naming its processors in <annotationProcessors> can be given HKJ's
fun processorJars(name: String) = configurations.create(name) {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
val coreProcessorJars = processorJars("coreProcessorJars")
val springProcessorJars = processorJars("springProcessorJars")
dependencies {
    coreProcessorJars(project(":hkj-processor"))
    springProcessorJars(project(":hkj-spring:client-processor"))
}

val generateProcessorList = tasks.register("generateProcessorList") {
    val outputDir = layout.buildDirectory.dir("generated/resources/hkj-processors")
    val core: FileCollection = coreProcessorJars
    val spring: FileCollection = springProcessorJars
    inputs.files(core, spring)
    outputs.dir(outputDir)
    doLast {
        fun registered(jars: FileCollection): String =
            jars.files
                .flatMap { jar ->
                    ZipFile(jar).use { zip ->
                        val entry = zip.getEntry("META-INF/services/javax.annotation.processing.Processor")
                        if (entry == null) emptyList()
                        else zip.getInputStream(entry).bufferedReader().readLines()
                    }
                }
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
        val coreNames = registered(core)
        val springNames = registered(spring)
        check(coreNames.isNotEmpty()) { "hkj-processor registers no annotation processor" }
        check(springNames.isNotEmpty()) { "the @HkjHttpClient processor jar registers none" }
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("hkj-processors.properties").writeText("core=$coreNames\nspring=$springNames\n")
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
    resources.srcDir(generateVersionProperties)
    resources.srcDir(generateProcessorList)
    resources.srcDir(bundleSkills)
}

// Maven refuses a plugin descriptor without its version, so the build writes it in. A literal
// token, because the descriptor carries Maven ${...} expressions of its own.
tasks.processResources {
    val versionValue = project.version.toString()
    inputs.property("version", versionValue)
    filesMatching("META-INF/maven/plugin.xml") {
        filter { line -> line.replace("@hkj.version@", versionValue) }
    }
}

// Maven plugin classes must not use --enable-preview so they can load in any Java 25+ JVM
tasks.withType<JavaCompile>().configureEach {
    doFirst {
        options.compilerArgs.removeIf { it == "--enable-preview" }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // The version the bundled hkj-version.properties must carry
    systemProperty("hkj.expectedVersion", project.version.toString())
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
