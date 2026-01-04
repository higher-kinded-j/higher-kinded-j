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

tasks.named<Test>("test") {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
    ignoreFailures = true  // tutorials may fail if the user has not completed
    // Exclude tutorial tests from CI builds
    // These are interactive exercises meant for users, not automated testing
    if (System.getenv("CI") == "true") {
        exclude("**/tutorial/**")
    }
}
