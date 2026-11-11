// The estate capstone's API module: the domain's value types and the shared mapping vocabulary.
// It has no mapping processor, as a published API module would not: a vocabulary is a plain
// interface, so nothing here is generated.
// ANCHOR: api_build
plugins {
    `java-library` // for api(...)
}

dependencies {
    api(project(":hkj-core")) // the library, and no annotation processor
}
// ANCHOR_END: api_build

// An example, never published: its API documentation is not generated, as in hkj-examples.
tasks.named("javadoc") {
    enabled = false
}
