// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.Map;
import java.util.Set;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The law checks behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/structure.html">Nesting, Containers, and
 * Sealed Hierarchies</a> page. The page {@code {{#include}}}s the anchored regions below, so the
 * snippet it displays is this test, and it is green.
 *
 * <p>{@code hkj-test} is test-scope, which is why the laws live in a test rather than beside the
 * spec.
 */
@DisplayName(
    "the Nesting, Containers, and Sealed Hierarchies page's mappings obey the mapping laws")
class StructureBookTest {

  @Test
  @DisplayName("each container locates a failure by whatever identifies an element in it")
  void everyContainerLocatesItsOwnWay() {
    // ANCHOR: check_container_paths
    Validated<NonEmptyList<FieldError>, Crew> parsed =
        CrewMappingImpl.INSTANCE.parse(
            new CrewDto(
                Set.of("nope"), // a set has no index
                new String[] {"ada@example.org", "also-nope"}, // an array does
                Map.of("bad-key", "a note"))); // a map has the key as sent

    assertThatValidated(parsed)
        .isInvalid()
        .hasFieldErrors(
            "members.nope: not an email address",
            "reserves.1: not an email address",
            "notes.bad-key: not an email address");
    // ANCHOR_END: check_container_paths
  }
}
