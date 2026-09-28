// The estate capstone's clients module: wire beans in the shape an OpenAPI generator or Lombok
// writes them, as a partner's client jar would ship. Lombok runs here; the mapping processor does
// not, so the service module reads these beans' compiled accessors.
// ANCHOR: clients_build
plugins {
    `java-library` // for api(...)
}

dependencies {
    compileOnly(libs.lombok) // org.projectlombok:lombok
    annotationProcessor(libs.lombok) // Lombok, and no mapping processor
    api(libs.jspecify) // org.jspecify:jspecify, for @Nullable
}
// ANCHOR_END: clients_build
