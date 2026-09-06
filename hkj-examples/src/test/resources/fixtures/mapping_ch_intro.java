// Fixture for hkj-book/src/mapping/ch_intro.md
//
// The chapter opens with the hand-written mapper it is about to replace. The wire record and the
// domain record it translates between are declared here; every other fence on the page is an
// {{#include}} of compiled source.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

record UserDto(String id, String email, String joined) {}

record User(UUID id, String email, LocalDate joined) {}

class Fixture {}
