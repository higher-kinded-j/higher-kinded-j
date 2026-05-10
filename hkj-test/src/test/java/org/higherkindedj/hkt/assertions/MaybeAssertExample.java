// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;

import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.MaybeAssert}. */
@DisplayName("MaybeAssert showcase")
class MaybeAssertExample {

  @Test
  @DisplayName("Just is asserted with isJust() and hasValue()")
  void justValue() {
    Maybe<String> result = Maybe.just("hello");

    assertThatMaybe(result).isJust().hasValue("hello");
  }

  @Test
  @DisplayName("Nothing is asserted with isNothing()")
  void nothingValue() {
    Maybe<String> result = Maybe.nothing();

    assertThatMaybe(result).isNothing();
  }

  @Test
  @DisplayName("hasValueSatisfying() chains domain checks on the wrapped value")
  void satisfying() {
    Maybe<Integer> result = Maybe.just(42);

    assertThatMaybe(result)
        .isJust()
        .hasValueSatisfying(value -> assertThat(value).isPositive().isLessThan(100));
  }

  @Test
  @DisplayName("hasValueNonNull() asserts presence without checking equality")
  void nonNull() {
    Maybe<Object> result = Maybe.just(new Object());

    assertThatMaybe(result).isJust().hasValueNonNull();
  }
}
