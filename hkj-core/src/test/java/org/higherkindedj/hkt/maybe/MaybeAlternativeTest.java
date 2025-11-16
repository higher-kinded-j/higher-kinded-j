// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad Alternative Operations Test Suite")
class MaybeAlternativeTest extends MaybeTestBase {

  private Alternative<MaybeKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = MaybeMonad.INSTANCE;
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns Nothing")
    void emptyReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> empty = alternative.empty();

      Maybe<Integer> maybe = narrowToMaybe(empty);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<MaybeKind.Witness, String> emptyString = alternative.empty();
      Kind<MaybeKind.Witness, Integer> emptyInt = alternative.empty();

      assertThat(narrowToMaybe(emptyString).isNothing()).isTrue();
      assertThat(narrowToMaybe(emptyInt).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("orElse() Tests")
  class OrElseTests {

    @Test
    @DisplayName("orElse() returns first when Just")
    void orElseReturnsFirstWhenJust() {
      Kind<MaybeKind.Witness, Integer> just = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> fallback = alternative.of(10);

      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(just, () -> fallback);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("orElse() returns second when first is Nothing")
    void orElseReturnsSecondWhenFirstIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = alternative.empty();
      Kind<MaybeKind.Witness, Integer> fallback = alternative.of(10);

      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(nothing, () -> fallback);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("orElse() returns Nothing when both are Nothing")
    void orElseReturnsNothingWhenBothAreNothing() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();

      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(nothing1, () -> nothing2);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("orElse() is lazy - doesn't evaluate second when first is Just")
    void orElseIsLazy() {
      Kind<MaybeKind.Witness, Integer> just = alternative.of(42);
      boolean[] evaluated = {false};

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElse(
              just,
              () -> {
                evaluated[0] = true;
                return alternative.of(10);
              });

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.get()).isEqualTo(42);
      assertThat(evaluated[0]).isFalse();
    }

    @Test
    @DisplayName("orElse() evaluates second when first is Nothing")
    void orElseEvaluatesSecondWhenNeeded() {
      Kind<MaybeKind.Witness, Integer> nothing = alternative.empty();
      boolean[] evaluated = {false};

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElse(
              nothing,
              () -> {
                evaluated[0] = true;
                return alternative.of(10);
              });

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.get()).isEqualTo(10);
      assertThat(evaluated[0]).isTrue();
    }
  }

  @Nested
  @DisplayName("guard() Tests")
  class GuardTests {

    @Test
    @DisplayName("guard(true) returns Just(Unit)")
    void guardTrueReturnsJustUnit() {
      Kind<MaybeKind.Witness, Unit> result = alternative.guard(true);

      Maybe<Unit> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns Nothing")
    void guardFalseReturnsNothing() {
      Kind<MaybeKind.Witness, Unit> result = alternative.guard(false);

      Maybe<Unit> maybe = narrowToMaybe(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() returns first Just")
    void orElseAllReturnsFirstJust() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> just = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElseAll(nothing1, () -> just, () -> nothing2);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("orElseAll() with multiple fallbacks")
    void orElseAllWithMultipleFallbacks() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing3 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> fallback = alternative.of(99);

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElseAll(nothing1, () -> nothing2, () -> nothing3, () -> fallback);

      Maybe<Integer> maybe = narrowToMaybe(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(99);
    }
  }

  @Nested
  @DisplayName("Alternative Laws")
  class AlternativeLaws {

    @Test
    @DisplayName("Left Identity: orElse(empty(), () -> fa) == fa")
    void leftIdentityLaw() {
      Kind<MaybeKind.Witness, Integer> fa = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(alternative.empty(), () -> fa);

      assertThat(narrowToMaybe(result).get()).isEqualTo(narrowToMaybe(fa).get());
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<MaybeKind.Witness, Integer> fa = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      assertThat(narrowToMaybe(result).get()).isEqualTo(narrowToMaybe(fa).get());
    }

    @Test
    @DisplayName(
        "Associativity: orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), ()"
            + " -> fc)")
    void associativityLaw() {
      Kind<MaybeKind.Witness, Integer> fa = alternative.empty();
      Kind<MaybeKind.Witness, Integer> fb = alternative.of(10);
      Kind<MaybeKind.Witness, Integer> fc = alternative.of(20);

      Kind<MaybeKind.Witness, Integer> left =
          alternative.orElse(fa, () -> alternative.orElse(fb, () -> fc));
      Kind<MaybeKind.Witness, Integer> right =
          alternative.orElse(alternative.orElse(fa, () -> fb), () -> fc);

      assertThat(narrowToMaybe(left).get()).isEqualTo(narrowToMaybe(right).get());
    }
  }
}
