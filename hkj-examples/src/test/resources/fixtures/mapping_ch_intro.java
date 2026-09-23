// Fixture for hkj-book/src/mapping/ch_intro.md
//
// The chapter opens with the hand-written mapper it is about to replace, the one the capstone
// works at full scale. The records it translates between are declared here; every other fence on the page is an
// {{#include}} of compiled source.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

record CustomerDto(String fullName, String email) {}

record OrderDto(String id, CustomerDto customer, String placedAt) {}

record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

record Order(UUID id, Customer customer, Instant placedAt) {}

class Fixture {}
