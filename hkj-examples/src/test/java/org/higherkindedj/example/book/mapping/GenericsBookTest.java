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
  void aSpecNamingItsTypeArgumentsIsConcrete() throws Exception {
    // ANCHOR: check_access
    PageDto<String> wire = TagPageMappingImpl.INSTANCE.build(new Page<>(List.of("fp"), 1));
    assertThat(wire).isEqualTo(new PageDto<>(List.of("fp"), 1));

    assertThat(TagPageMappingImpl.class.getField("INSTANCE")).isNotNull(); // a plain constant
    assertThatThrownBy(() -> TagPageMappingImpl.class.getMethod("instance"))
        .isInstanceOf(NoSuchMethodException.class); // no generic accessor
    // ANCHOR_END: check_access
  }

  @Test
  @DisplayName("two same-typed abstract leaves swap without a compile error")
  void sameTypedAbstractLeavesSwapSilently() {
    // ANCHOR: check_swap
    ValidatedPrism<String, LocalDate> iso = StandardCodecs.localDate(); // 2026-07-28
    ValidatedPrism<String, LocalDate> uk = // 31/07/2026
        StandardCodecs.localDate(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
    WindowDto<String> request = new WindowDto<>("2026-07-28", "31/07/2026");

    assertThatValidated(WindowMappingImpl.of(iso, uk).parse(request)) // opens, then closes
        .hasValue(new Window<>(LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 31)));
    assertThatValidated(WindowMappingImpl.of(uk, iso).parse(request)) // swapped: it compiles
        .hasFieldErrors(
            "opens: not a date (expected e.g. 28/07/2026)",
            "closes: not an ISO-8601 date (expected e.g. 2026-07-28)");
    // ANCHOR_END: check_swap
  }
}
