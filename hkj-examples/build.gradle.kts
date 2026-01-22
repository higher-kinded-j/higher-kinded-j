plugins {
    application
}

// Allow running any example via: ./gradlew :hkj-examples:run -PmainClass=<fully.qualified.ClassName>
application {
    mainClass.set(project.findProperty("mainClass")?.toString() ?: "org.higherkindedj.example.tutorials.TutorialGettingStarted")
}

dependencies {
    api(libs.jspecify)
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor-plugins"))

    // Testing dependencies for tutorials
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.named("javadoc") {
    enabled = false
}

// Default test task: runs solution tests only (tutorials are excluded)
// Solutions must pass as they verify the tutorial exercises are correct
tasks.named<Test>("test") {
    useJUnitPlatform()
    // Exclude tutorial exercises (they are incomplete by design)
    // Include solutions which must always pass
    exclude("**/tutorial/optics/**")
    exclude("**/tutorial/coretypes/**")
    exclude("**/tutorial/concurrency/**")
    exclude("**/tutorial/effect/**")
    exclude("**/tutorial/context/**")
    // Solutions are in tutorial/solutions/ and will run
}

// Separate task for users to run tutorial exercises
// Usage: ./gradlew :hkj-examples:tutorialTest
tasks.register<Test>("tutorialTest") {
    description = "Run tutorial exercises (expected to fail until completed by user)"
    group = "verification"
    useJUnitPlatform()

    // Configure test classpath (required for custom Test tasks)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    // Only include tutorial exercises, exclude solutions
    include("**/tutorial/optics/**")
    include("**/tutorial/coretypes/**")
    include("**/tutorial/concurrency/**")
    include("**/tutorial/effect/**")
    exclude("**/tutorial/solutions/**")
    // Don't fail the build - tutorials are expected to fail
    ignoreFailures = true
}
