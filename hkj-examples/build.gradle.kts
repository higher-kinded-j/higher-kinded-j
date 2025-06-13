plugins {

}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
    implementation(project(":hkj-core"))
    annotationProcessor(project(":hkj-processor"))
    annotationProcessor(project(":hkj-processor-plugins"))
}

tasks.named("javadoc") {
    enabled = false
}
