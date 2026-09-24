// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.util.ArrayList;
import java.util.List;
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
  }

  @Test
  @DisplayName("a sealed dispatch adds no path segment: a list index, then the subtype's component")
  void aSealedDispatchAddsNoSegment() {
    // ANCHOR: check_payment_path
    assertThatValidated(
            CheckoutMappingImpl.INSTANCE.parse(
                new CheckoutDto(
                    "C-1", List.of(new BankDto("GB33BUKB20201555555555"), new CardDto(null)))))
        .hasFieldErrors("payments.1.pan: must not be null");
    // ANCHOR_END: check_payment_path
  }

  @Test
  @DisplayName(
      "a same-typed list crosses as an unmodifiable copy, so the domain's list is untouched")
  void aSameTypedListCrossesAsAnUnmodifiableCopy() {
    // ANCHOR: check_copy
    List<String> tags = new ArrayList<>(List.of("vip")); // the domain's own, mutable list
    MemoDto dto = MemoMappingImpl.INSTANCE.build(new Memo("Call back", tags));

    assertThatThrownBy(() -> dto.tags().add("urgent"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(tags).containsExactly("vip"); // and the domain never shared it
    // ANCHOR_END: check_copy
  }
}
