// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

plugins {
    `java-library`
}

dependencies {
    // OpenRewrite core dependencies
    compileOnly("org.openrewrite:rewrite-java:8.75.5")
    compileOnly("org.openrewrite:rewrite-core:8.75.5")

    // Required for recipe testing
    testImplementation("org.openrewrite:rewrite-java:8.75.5")
    testImplementation("org.openrewrite:rewrite-test:8.75.5")
    testImplementation("org.openrewrite:rewrite-java-25:8.75.5")

    // Testing dependencies
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
