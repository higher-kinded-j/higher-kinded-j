// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

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

@DisplayName("OptionalMonad — Alternative operations")
class OptionalAlternativeTest extends OptionalTestBase {

  private Alternative<OptionalKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = Instances.alternative(optional());
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns empty")
    void emptyReturnsEmpty() {
      Kind<OptionalKind.Witness, Integer> empty = alternative.empty();
      assertThatOptionalKind(empty).isEmpty();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<OptionalKind.Witness, String> emptyString = alternative.empty();
      Kind<OptionalKind.Witness, Integer> emptyInt = alternative.empty();

      assertThatOptionalKind(emptyString).isEmpty();
      assertThatOptionalKind(emptyInt).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElse() Tests")
  class OrElseTests {

    @Test
    @DisplayName("orElse() returns first when present")
    void orElseReturnsFirstWhenPresent() {
      Kind<OptionalKind.Witness, Integer> present = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> fallback = alternative.of(10);

      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(present, () -> fallback);
      assertThatOptionalKind(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("orElse() returns second when first is empty")
    void orElseReturnsSecondWhenFirstIsEmpty() {
      Kind<OptionalKind.Witness, Integer> empty = alternative.empty();
      Kind<OptionalKind.Witness, Integer> fallback = alternative.of(10);

      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(empty, () -> fallback);
      assertThatOptionalKind(result).isPresent().contains(10);
    }

    @Test
    @DisplayName("orElse() returns empty when both are empty")
    void orElseReturnsEmptyWhenBothAreEmpty() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();

      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(empty1, () -> empty2);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("orElse() is lazy - doesn't evaluate second when first is present")
    void orElseIsLazy() {
      Kind<OptionalKind.Witness, Integer> present = alternative.of(42);
      boolean[] evaluated = {false};

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElse(
              present,
              () -> {
                evaluated[0] = true;
                return alternative.of(10);
              });

      assertThatOptionalKind(result).isPresent().contains(42);
      assertThat(evaluated[0]).isFalse();
    }

    @Test
    @DisplayName("orElse() evaluates second when first is empty")
    void orElseEvaluatesSecondWhenNeeded() {
      Kind<OptionalKind.Witness, Integer> empty = alternative.empty();
      boolean[] evaluated = {false};

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElse(
              empty,
              () -> {
                evaluated[0] = true;
                return alternative.of(10);
              });

      assertThatOptionalKind(result).isPresent().contains(10);
      assertThat(evaluated[0]).isTrue();
    }
  }

  @Nested
  @DisplayName("guard() Tests")
  class GuardTests {

    @Test
    @DisplayName("guard(true) returns present(Unit)")
    void guardTrueReturnsPresentUnit() {
      Kind<OptionalKind.Witness, Unit> result = alternative.guard(true);
      assertThatOptionalKind(result).isPresent().contains(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns empty")
    void guardFalseReturnsEmpty() {
      Kind<OptionalKind.Witness, Unit> result = alternative.guard(false);
      assertThatOptionalKind(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() returns first present")
    @SuppressWarnings("unchecked") // generic varargs call to orElseAll
    void orElseAllReturnsFirstPresent() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> present = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElseAll(empty1, () -> present, () -> empty2);
      assertThatOptionalKind(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("orElseAll() with multiple fallbacks")
    @SuppressWarnings("unchecked") // generic varargs call to orElseAll
    void orElseAllWithMultipleFallbacks() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty3 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> fallback = alternative.of(99);

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElseAll(empty1, () -> empty2, () -> empty3, () -> fallback);
      assertThatOptionalKind(result).isPresent().contains(99);
    }
  }

  @Nested
  @DisplayName("orElseAll(Iterable) Tests")
  class OrElseAllIterableTests {

    @Test
    @DisplayName("orElseAll(empty iterable) returns empty()")
    void orElseAllEmptyIterableReturnsEmpty() {
      Kind<OptionalKind.Witness, Integer> result = alternative.orElseAll(List.of());
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("orElseAll(Iterable) returns first present")
    void orElseAllIterableReturnsFirstPresent() {
      List<Kind<OptionalKind.Witness, Integer>> candidates =
          Arrays.asList(alternative.empty(), alternative.of(42), alternative.of(7));

      Kind<OptionalKind.Witness, Integer> result = alternative.orElseAll(candidates);
      assertThatOptionalKind(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("orElseAll(Iterable) returns empty() when all empty")
    void orElseAllIterableAllEmpty() {
      List<Kind<OptionalKind.Witness, Integer>> candidates =
          Arrays.asList(alternative.empty(), alternative.empty(), alternative.empty());

      Kind<OptionalKind.Witness, Integer> result = alternative.orElseAll(candidates);
      assertThatOptionalKind(result).isEmpty();
    }

    @Test
    @DisplayName("orElseAll(Iterable) with singleton returns that element")
    void orElseAllIterableSingleton() {
      Kind<OptionalKind.Witness, Integer> present = alternative.of(99);

      Kind<OptionalKind.Witness, Integer> result = alternative.orElseAll(List.of(present));
      assertThatOptionalKind(result).isPresent().contains(99);
    }

    @Test
    @DisplayName("orElseAll(Iterable) accepts an arbitrary Iterable, not just List")
    void orElseAllAcceptsArbitraryIterable() {
      List<Kind<OptionalKind.Witness, Integer>> backing = new ArrayList<>();
      backing.add(alternative.empty());
      backing.add(alternative.of(123));
      Iterable<Kind<OptionalKind.Witness, Integer>> iter = backing::iterator;

      Kind<OptionalKind.Witness, Integer> result = alternative.orElseAll(iter);
      assertThatOptionalKind(result).isPresent().contains(123);
    }

    @Test
    @DisplayName("orElseAll(null iterable) throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void orElseAllNullIterableThrows() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> alternative.orElseAll((Iterable<Kind<OptionalKind.Witness, Integer>>) null))
          .withMessageContaining("alternatives");
    }

    @Test
    @DisplayName("orElseAll(iterable with null element) throws NullPointerException")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void orElseAllNullElementThrows() {
      List<Kind<OptionalKind.Witness, Integer>> candidates = new ArrayList<>();
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
      Kind<OptionalKind.Witness, Integer> fa = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElse(alternative.empty(), () -> fa);
      assertThat(narrowToOptional(result)).isEqualTo(narrowToOptional(fa));
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<OptionalKind.Witness, Integer> fa = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);
      assertThat(narrowToOptional(result)).isEqualTo(narrowToOptional(fa));
    }

    @Test
    @DisplayName(
        "Associativity: orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), ()"
            + " -> fc)")
    void associativityLaw() {
      Kind<OptionalKind.Witness, Integer> fa = alternative.empty();
      Kind<OptionalKind.Witness, Integer> fb = alternative.of(10);
      Kind<OptionalKind.Witness, Integer> fc = alternative.of(20);

      Kind<OptionalKind.Witness, Integer> left =
          alternative.orElse(fa, () -> alternative.orElse(fb, () -> fc));
      Kind<OptionalKind.Witness, Integer> right =
          alternative.orElse(alternative.orElse(fa, () -> fb), () -> fc);

      assertThat(narrowToOptional(left)).isEqualTo(narrowToOptional(right));
    }
  }
}
