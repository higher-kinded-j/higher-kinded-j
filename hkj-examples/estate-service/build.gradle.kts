// The estate capstone's service module: the domain records and the mapping specs, with the
// mapping processor, over the API module's vocabulary and the clients module's beans.
// ANCHOR: service_build
dependencies {
    implementation(project(":hkj-examples:estate-api"))
    implementation(project(":hkj-examples:estate-clients"))
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor")) // the mapping processor runs here, and only here
}
// ANCHOR_END: service_build

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(project(":hkj-test"))
    // The PATCH test binds real JSON, as a controller would.
    testImplementation(libs.jackson.databind)
}

tasks.test {
    useJUnitPlatform()
}
