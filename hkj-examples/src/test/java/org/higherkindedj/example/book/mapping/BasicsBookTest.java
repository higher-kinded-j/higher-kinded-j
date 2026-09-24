// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answers behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/basics.html">Record Mapping Basics</a>
 * page. The page {@code {{#include}}}s the anchored regions below, so each answer it shows is this
 * test, and it is green.
 */
@DisplayName("the Record Mapping Basics page's answers hold")
class BasicsBookTest {

  @Test
  @DisplayName("a null and a bad email are both reported, in declaration order")
  void nullAndBadLeafAccumulate() {
    // ANCHOR: null_and_leaf
    assertThatValidated(CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, "not-an-email")))
        .hasFieldErrors("name: must not be null", "email: not an email address");
    // ANCHOR_END: null_and_leaf
  }
}
