// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OptionalMonad Alternative Operations Test Suite")
class OptionalAlternativeTest {

  private Alternative<OptionalKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = OptionalMonad.INSTANCE;
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns Optional.empty()")
    void emptyReturnsOptionalEmpty() {
      Kind<OptionalKind.Witness, Integer> empty = alternative.empty();

      Optional<Integer> optional = OPTIONAL.narrow(empty);
      assertThat(optional).isEmpty();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<OptionalKind.Witness, String> emptyString = alternative.empty();
      Kind<OptionalKind.Witness, Integer> emptyInt = alternative.empty();

      assertThat(OPTIONAL.narrow(emptyString)).isEmpty();
      assertThat(OPTIONAL.narrow(emptyInt)).isEmpty();
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

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional).isPresent();
      assertThat(optional.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("orElse() returns second when first is empty")
    void orElseReturnsSecondWhenFirstIsEmpty() {
      Kind<OptionalKind.Witness, Integer> empty = alternative.empty();
      Kind<OptionalKind.Witness, Integer> fallback = alternative.of(10);

      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(empty, () -> fallback);

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional).isPresent();
      assertThat(optional.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("orElse() returns empty when both are empty")
    void orElseReturnsEmptyWhenBothAreEmpty() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();

      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(empty1, () -> empty2);

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional).isEmpty();
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

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional.get()).isEqualTo(42);
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

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional.get()).isEqualTo(10);
      assertThat(evaluated[0]).isTrue();
    }
  }

  @Nested
  @DisplayName("guard() Tests")
  class GuardTests {

    @Test
    @DisplayName("guard(true) returns Optional.of(Unit)")
    void guardTrueReturnsOptionalOfUnit() {
      Kind<OptionalKind.Witness, Unit> result = alternative.guard(true);

      Optional<Unit> optional = OPTIONAL.narrow(result);
      assertThat(optional).isPresent();
      assertThat(optional.get()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns Optional.empty()")
    void guardFalseReturnsOptionalEmpty() {
      Kind<OptionalKind.Witness, Unit> result = alternative.guard(false);

      Optional<Unit> optional = OPTIONAL.narrow(result);
      assertThat(optional).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() returns first present")
    void orElseAllReturnsFirstPresent() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> present = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElseAll(empty1, () -> present, () -> empty2);

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional).isPresent();
      assertThat(optional.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("orElseAll() with multiple fallbacks")
    void orElseAllWithMultipleFallbacks() {
      Kind<OptionalKind.Witness, Integer> empty1 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty2 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> empty3 = alternative.empty();
      Kind<OptionalKind.Witness, Integer> fallback = alternative.of(99);

      Kind<OptionalKind.Witness, Integer> result =
          alternative.orElseAll(empty1, () -> empty2, () -> empty3, () -> fallback);

      Optional<Integer> optional = OPTIONAL.narrow(result);
      assertThat(optional).isPresent();
      assertThat(optional.get()).isEqualTo(99);
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

      assertThat(OPTIONAL.narrow(result).get()).isEqualTo(OPTIONAL.narrow(fa).get());
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<OptionalKind.Witness, Integer> fa = alternative.of(42);
      Kind<OptionalKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      assertThat(OPTIONAL.narrow(result).get()).isEqualTo(OPTIONAL.narrow(fa).get());
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

      assertThat(OPTIONAL.narrow(left).get()).isEqualTo(OPTIONAL.narrow(right).get());
    }
  }
}
