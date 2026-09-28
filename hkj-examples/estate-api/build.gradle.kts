// The estate capstone's api module: the domain's value types and the shared mapping vocabulary.
// It has no mapping processor, as a published api module would not: a vocabulary is a plain
// interface, so nothing here is generated.
// ANCHOR: api_build
dependencies {
    api(project(":hkj-core")) // the library, and no annotation processor
}
// ANCHOR_END: api_build
