plugins {

}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor"))
    annotationProcessor(project(":hkj-processor-plugins"))

    // Testing dependencies for tutorials
    testImplementation(platform("org.junit:junit-bom:5.13.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.named("javadoc") {
    enabled = false
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
