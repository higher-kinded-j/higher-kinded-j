// The estate capstone's clients module: wire beans in the shape an OpenAPI generator or Lombok
// writes them, as a partner's client jar would ship. Lombok runs here; the mapping processor does
// not, so the service module reads these beans from the compiled jar.
// ANCHOR: clients_build
dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok) // Lombok, and no mapping processor
    api(libs.jspecify)
}
// ANCHOR_END: clients_build
