// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KindEquivalence")
class KindEquivalenceTest {

  private final BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> eq =
      KindEquivalence.byEqualsAfter(MAYBE::narrow);

  @Test
  @DisplayName("byEqualsAfter returns true for equal narrowed values")
  void equalValues() {
    Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(42));
    Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(42));
    assertThat(eq.test(a, b)).isTrue();
  }

  @Test
  @DisplayName("byEqualsAfter returns false for differing narrowed values")
  void differingValues() {
    Kind<MaybeKind.Witness, Integer> a = MAYBE.widen(Maybe.just(42));
    Kind<MaybeKind.Witness, Integer> b = MAYBE.widen(Maybe.just(7));
    assertThat(eq.test(a, b)).isFalse();
  }

  @Test
  @DisplayName("byEqualsAfter compares Just to Nothing as not equal")
  void differingStructure() {
    Kind<MaybeKind.Witness, Integer> just = MAYBE.widen(Maybe.just(42));
    Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());
    assertThat(eq.test(just, nothing)).isFalse();
  }
}
