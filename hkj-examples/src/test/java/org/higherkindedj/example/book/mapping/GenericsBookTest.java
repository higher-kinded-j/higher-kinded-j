// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The answers behind the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/generics.html">Generic Specs</a> page. The
 * page {@code {{#include}}}s the anchored regions below, so each answer it shows is this test, and
 * it is green.
 */
@DisplayName("the Generic Specs page's answers hold")
class GenericsBookTest {

  @Test
  @DisplayName("a spec that names its type arguments is concrete, generic records or not")
  void aSpecNamingItsTypeArgumentsIsConcrete() {
    // ANCHOR: check_access
    PageDto<String> wire = TagPageMappingImpl.INSTANCE.build(new Page<>(List.of("fp"), 1));
    assertThat(wire).isEqualTo(new PageDto<>(List.of("fp"), 1));

    assertThatThrownBy(() -> TagPageMappingImpl.class.getMethod("instance"))
        .isInstanceOf(NoSuchMethodException.class); // no generic accessor
    // ANCHOR_END: check_access
  }

  @Test
  @DisplayName("two same-typed abstract leaves swap without a compile error, to the wrong values")
  void sameTypedAbstractLeavesSwapSilently() {
    // ANCHOR: check_swap
    ValidatedPrism<String, LocalDate> uk = // 03/04/2026 is 3 April
        StandardCodecs.localDate(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
    ValidatedPrism<String, LocalDate> us = // 05/04/2026 is 4 May
        StandardCodecs.localDate(DateTimeFormatter.ofPattern("MM/dd/uuuu"));
    WindowDto<String> request = new WindowDto<>("03/04/2026", "05/04/2026");

    assertThatValidated(WindowMappingImpl.of(uk, us).parse(request)) // opens, then closes
        .hasValue(new Window<>(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 4)));
    assertThatValidated(WindowMappingImpl.of(us, uk).parse(request)) // swapped: it compiles
        .hasValue(new Window<>(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 4, 5))); // no error
    // ANCHOR_END: check_swap
  }
}
