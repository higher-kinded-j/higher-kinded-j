// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeMonad — Alternative operations")
class MaybeAlternativeTest extends MaybeTestBase {

  private Alternative<MaybeKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = Instances.alternative(maybe());
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns Nothing")
    void emptyReturnsNothing() {
      Kind<MaybeKind.Witness, Integer> empty = alternative.empty();
      assertThatMaybe(empty).isNothing();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<MaybeKind.Witness, String> emptyString = alternative.empty();
      Kind<MaybeKind.Witness, Integer> emptyInt = alternative.empty();

      assertThatMaybe(emptyString).isNothing();
      assertThatMaybe(emptyInt).isNothing();
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
      assertThatMaybe(result).isJust().hasValue(42);
    }

    @Test
    @DisplayName("orElse() returns second when first is Nothing")
    void orElseReturnsSecondWhenFirstIsNothing() {
      Kind<MaybeKind.Witness, Integer> nothing = alternative.empty();
      Kind<MaybeKind.Witness, Integer> fallback = alternative.of(10);

      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(nothing, () -> fallback);
      assertThatMaybe(result).isJust().hasValue(10);
    }

    @Test
    @DisplayName("orElse() returns Nothing when both are Nothing")
    void orElseReturnsNothingWhenBothAreNothing() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();

      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(nothing1, () -> nothing2);
      assertThatMaybe(result).isNothing();
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

      assertThatMaybe(result).isJust().hasValue(42);
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

      assertThatMaybe(result).isJust().hasValue(10);
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
      assertThatMaybe(result).isJust().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns Nothing")
    void guardFalseReturnsNothing() {
      Kind<MaybeKind.Witness, Unit> result = alternative.guard(false);
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() returns first Just")
    @SuppressWarnings("unchecked") // generic varargs call to orElseAll
    void orElseAllReturnsFirstJust() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> just = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElseAll(nothing1, () -> just, () -> nothing2);
      assertThatMaybe(result).isJust().hasValue(42);
    }

    @Test
    @DisplayName("orElseAll() with multiple fallbacks")
    @SuppressWarnings("unchecked") // generic varargs call to orElseAll
    void orElseAllWithMultipleFallbacks() {
      Kind<MaybeKind.Witness, Integer> nothing1 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing2 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> nothing3 = alternative.empty();
      Kind<MaybeKind.Witness, Integer> fallback = alternative.of(99);

      Kind<MaybeKind.Witness, Integer> result =
          alternative.orElseAll(nothing1, () -> nothing2, () -> nothing3, () -> fallback);
      assertThatMaybe(result).isJust().hasValue(99);
    }
  }

  @Nested
  @DisplayName("orElseAll(Iterable) Tests")
  class OrElseAllIterableTests {

    @Test
    @DisplayName("orElseAll(empty iterable) returns empty()")
    void orElseAllEmptyIterableReturnsEmpty() {
      Kind<MaybeKind.Witness, Integer> result = alternative.orElseAll(List.of());
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("orElseAll(Iterable) returns first Just")
    void orElseAllIterableReturnsFirstJust() {
      List<Kind<MaybeKind.Witness, Integer>> candidates =
          Arrays.asList(alternative.empty(), alternative.of(42), alternative.of(7));

      Kind<MaybeKind.Witness, Integer> result = alternative.orElseAll(candidates);
      assertThatMaybe(result).isJust().hasValue(42);
    }

    @Test
    @DisplayName("orElseAll(Iterable) returns Nothing when all empty")
    void orElseAllIterableAllEmpty() {
      List<Kind<MaybeKind.Witness, Integer>> candidates =
          Arrays.asList(alternative.empty(), alternative.empty(), alternative.empty());

      Kind<MaybeKind.Witness, Integer> result = alternative.orElseAll(candidates);
      assertThatMaybe(result).isNothing();
    }

    @Test
    @DisplayName("orElseAll(Iterable) with singleton returns that element")
    void orElseAllIterableSingleton() {
      Kind<MaybeKind.Witness, Integer> just = alternative.of(99);

      Kind<MaybeKind.Witness, Integer> result = alternative.orElseAll(List.of(just));
      assertThatMaybe(result).isJust().hasValue(99);
    }

    @Test
    @DisplayName("orElseAll(Iterable) accepts arbitrary Iterable, not just List")
    void orElseAllAcceptsArbitraryIterable() {
      List<Kind<MaybeKind.Witness, Integer>> backing = new ArrayList<>();
      backing.add(alternative.empty());
      backing.add(alternative.of(123));
      Iterable<Kind<MaybeKind.Witness, Integer>> iter = backing::iterator;

      Kind<MaybeKind.Witness, Integer> result = alternative.orElseAll(iter);
      assertThatMaybe(result).isJust().hasValue(123);
    }

    @Test
    @DisplayName("orElseAll(null iterable) throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // deliberately passing null to verify rejection
    void orElseAllNullIterableThrows() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> alternative.orElseAll((Iterable<Kind<MaybeKind.Witness, Integer>>) null))
          .withMessageContaining("alternatives");
    }

    @Test
    @DisplayName("orElseAll(iterable with null element) throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // deliberately adding null element to verify rejection
    void orElseAllNullElementThrows() {
      List<Kind<MaybeKind.Witness, Integer>> candidates = new ArrayList<>();
      candidates.add(alternative.empty());
      candidates.add(null);
      candidates.add(alternative.of(1));

      assertThatNullPointerException().isThrownBy(() -> alternative.orElseAll(candidates));
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

      assertThat(narrowToMaybe(result)).isEqualTo(narrowToMaybe(fa));
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<MaybeKind.Witness, Integer> fa = alternative.of(42);
      Kind<MaybeKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      assertThat(narrowToMaybe(result)).isEqualTo(narrowToMaybe(fa));
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

      assertThat(narrowToMaybe(left)).isEqualTo(narrowToMaybe(right));
    }
  }
}
