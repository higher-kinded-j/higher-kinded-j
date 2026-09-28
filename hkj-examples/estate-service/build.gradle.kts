// The estate capstone's service module: the domain records and the mapping specs, with the
// mapping processor, over the api module's vocabulary and the clients module's beans.
dependencies {
    implementation(project(":hkj-examples:estate-api"))
    implementation(project(":hkj-examples:estate-clients"))
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(project(":hkj-test"))
}

tasks.test {
    useJUnitPlatform()
}
