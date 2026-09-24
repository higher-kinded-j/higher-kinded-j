// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.mapping.FreshPackage.mapperAfterFirstUsing;
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
  @DisplayName("a constant on the spec reads null once the spec declares a leaf")
  void aConstantOnTheSpecReadsNullOnceTheSpecDeclaresALeaf() throws Exception {
    // ANCHOR: constant_proof
    // Two programs, each loading this package afresh and using the Impl before MAPPER:
    assertThat(mapperAfterFirstUsing("TicketMappingImpl", "TicketMapping")).isNotNull(); // no leaf
    assertThat(mapperAfterFirstUsing("PassMappingImpl", "PassMapping")).isNull(); // a leaf
    // ANCHOR_END: constant_proof
  }

  @Test
  @DisplayName("a null never reaches the leaf: both nulls are located, in declaration order")
  void aNullNeverReachesTheLeaf() {
    // ANCHOR: null_before_leaf
    assertThatValidated(CustomerMappingImpl.INSTANCE.parse(new CustomerDto(null, null)))
        .hasFieldErrors("name: must not be null", "email: must not be null");
    // ANCHOR_END: null_before_leaf
  }
}
