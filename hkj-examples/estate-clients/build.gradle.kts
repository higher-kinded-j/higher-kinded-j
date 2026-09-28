// The estate capstone's clients module: wire beans in the shape an OpenAPI generator or Lombok
// writes them, as a partner's client jar would ship. Lombok runs here; the mapping processor does
// not, so the service module reads these beans from the compiled jar.
dependencies {
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    api(libs.jspecify)
}
