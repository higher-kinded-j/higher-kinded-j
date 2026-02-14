// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OptionalTraverseTest {

  private final OptionalTraverse traverse = OptionalTraverse.INSTANCE;
  private final Foldable<OptionalKind.Witness> foldable = OptionalTraverse.INSTANCE;
  private final Applicative<MaybeKind.Witness> maybeApplicative = MaybeMonad.INSTANCE;

  // A test function that "validates" an integer, returning a Maybe.
  // It returns Nothing for non-positive numbers.
  private final Function<Integer, Kind<MaybeKind.Witness, String>> validatePositive =
      i -> {
        if (i > 0) {
          return MAYBE.widen(Maybe.just("Positive: " + i));
        } else {
          return MAYBE.widen(Maybe.nothing());
        }
      };

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    @DisplayName("map should apply a function to a present Optional")
    void mapPresent() {
      // Given a present Optional
      Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(Optional.of(10));

      // When we map over it
      Kind<OptionalKind.Witness, Integer> result = traverse.map(i -> i * 2, input);

      // Then the result is a present Optional with the new value
      assertThat(OPTIONAL.narrow(result)).isEqualTo(Optional.of(20));
    }

    @Test
    @DisplayName("map should do nothing to an empty Optional")
    void mapEmpty() {
      // Given an empty Optional
      Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(Optional.empty());

      // When we map over it
      Kind<OptionalKind.Witness, Integer> result = traverse.map(i -> i * 2, input);

      // Then the result is an empty Optional
      assertThat(OPTIONAL.narrow(result)).isEqualTo(Optional.empty());
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {
    @Test
    @DisplayName("traverse should map a function over a present Optional")
    void traversePresentSucceeds() {
      // Given a present Optional
      Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(Optional.of(123));

      // When we traverse it with a function that returns a Just
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is a Just containing a present Optional of the new value
      Maybe<Kind<OptionalKind.Witness, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(OPTIONAL.narrow(maybeResult.get())).isEqualTo(Optional.of("Positive: 123"));
    }

    @Test
    @DisplayName("traverse should return Nothing when the function fails on a present Optional")
    void traversePresentFails() {
      // Given a present Optional with a value that will cause the function to fail
      Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(Optional.of(-1));

      // When we traverse it with a function that returns Nothing
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is Nothing
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse should do nothing to an empty Optional")
    void traverseEmpty() {
      // Given an empty Optional
      Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(Optional.empty());

      // When we traverse it
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is a Just containing an empty Optional
      Maybe<Kind<OptionalKind.Witness, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(OPTIONAL.narrow(maybeResult.get())).isEqualTo(Optional.empty());
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    @Test
    @DisplayName("sequenceA should turn Optional<Just<A>> into Just<Optional<A>>")
    void sequencePresentSucceeds() {
      // Given an Optional containing a Just
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(42));
      final Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
          OPTIONAL.widen(Optional.of(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing an Optional
      Maybe<Kind<OptionalKind.Witness, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(OPTIONAL.narrow(maybeResult.get())).isEqualTo(Optional.of(42));
    }

    @Test
    @DisplayName("sequenceA should turn Optional<Nothing> into Nothing")
    void sequencePresentFails() {
      // Given an Optional containing a Nothing
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.nothing());
      final Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
          OPTIONAL.widen(Optional.of(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is Nothing
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA should do nothing to an empty Optional")
    void sequenceEmpty() {
      // Given an empty Optional
      final Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
          OPTIONAL.widen(Optional.empty());

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing an empty Optional
      Maybe<Kind<OptionalKind.Witness, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(OPTIONAL.narrow(maybeResult.get()).isEmpty()).isTrue();
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    @DisplayName("foldMap on a present Optional should apply the function")
    void foldMap_onPresent_shouldApplyFunction() {
      Kind<OptionalKind.Witness, Integer> present = OPTIONAL.widen(Optional.of(5));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 10, present);

      assertThat(result).isEqualTo(50);
    }

    @Test
    @DisplayName("foldMap on an empty Optional should return the monoid's empty value")
    void foldMap_onEmpty_shouldReturnMonoidEmpty() {
      Kind<OptionalKind.Witness, Integer> empty = OPTIONAL.widen(Optional.empty());
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 10, empty);

      assertThat(result).isEqualTo(sumMonoid.empty());
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("foldMap on an empty Optional with String monoid should return empty string")
    void foldMap_onEmpty_withStringMonoid_shouldReturnEmptyString() {
      Kind<OptionalKind.Witness, Integer> empty = OPTIONAL.widen(Optional.empty());
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, Object::toString, empty);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }
  }
}
