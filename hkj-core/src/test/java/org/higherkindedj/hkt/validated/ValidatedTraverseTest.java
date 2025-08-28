// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

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

public class ValidatedTraverseTest {

  private final ValidatedTraverse<String> traverse = ValidatedTraverse.instance();
  private final Foldable<ValidatedKind.Witness<String>> foldable = ValidatedTraverse.instance();
  private final Applicative<MaybeKind.Witness> maybeApplicative = MaybeMonad.INSTANCE;

  // A test function that "validates" a number, returning its string representation in a Maybe,
  // but returns Nothing for negative numbers.
  private final Function<Integer, Kind<MaybeKind.Witness, String>> validatePositive =
      i -> {
        if (i >= 0) {
          return MAYBE.widen(Maybe.just("Number is " + i));
        } else {
          return MAYBE.widen(Maybe.nothing());
        }
      };

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    @DisplayName("map should apply a function to a Valid value")
    void mapValid() {
      // Given a Valid value
      Kind<ValidatedKind.Witness<String>, Integer> input = VALIDATED.widen(Validated.valid(10));

      // When we map over it
      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(i -> "Value: " + i, input);

      // Then the result is a Valid containing the new value
      assertThat(VALIDATED.<String, String>narrow(result)).isEqualTo(Validated.valid("Value: 10"));
    }

    @Test
    @DisplayName("map should do nothing to an Invalid value")
    void mapInvalid() {
      // Given an Invalid value
      Kind<ValidatedKind.Witness<String>, Integer> input =
          VALIDATED.widen(Validated.invalid("Error"));

      // When we map over it
      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(i -> "Value: " + i, input);

      // Then the result is still the original Invalid
      assertThat(VALIDATED.<String, String>narrow(result)).isEqualTo(Validated.invalid("Error"));
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {
    @Test
    @DisplayName("traverse should map a function over a Valid value and lift the result")
    void traverseValidSucceeds() {
      // Given a Valid value
      Kind<ValidatedKind.Witness<String>, Integer> input = VALIDATED.widen(Validated.valid(123));

      // When we traverse it with a function that succeeds
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is a Just containing a Valid of the new value
      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(VALIDATED.<String, String>narrow(maybeResult.get()))
          .isEqualTo(Validated.valid("Number is 123"));
    }

    @Test
    @DisplayName("traverse should return Nothing when the function fails on a Valid value")
    void traverseValidFails() {
      // Given a Valid value that will cause the function to fail
      Kind<ValidatedKind.Witness<String>, Integer> input = VALIDATED.widen(Validated.valid(-1));

      // When we traverse it with a function that returns Nothing
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is Nothing
      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse should do nothing to an Invalid value")
    void traverseInvalid() {
      // Given an Invalid value
      Kind<ValidatedKind.Witness<String>, Integer> input =
          VALIDATED.widen(Validated.invalid("Initial Error"));

      // When we traverse it
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, validatePositive, input);

      // Then the result is a Just containing the original Invalid
      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(VALIDATED.<String, String>narrow(maybeResult.get()))
          .isEqualTo(Validated.invalid("Initial Error"));
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    @Test
    @DisplayName("sequenceA should turn Valid<Just<A>> into Just<Valid<A>>")
    void sequenceValidSucceeds() {
      // Given a Valid containing a Just
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(123));
      final Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          VALIDATED.widen(Validated.valid(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing a Valid
      Maybe<Kind<ValidatedKind.Witness<String>, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(VALIDATED.<String, Integer>narrow(maybeResult.get()))
          .isEqualTo(Validated.valid(123));
    }

    @Test
    @DisplayName("sequenceA should turn Valid<Nothing> into Nothing")
    void sequenceValidFails() {
      // Given a Valid containing a Nothing
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.nothing());
      final Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          VALIDATED.widen(Validated.valid(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is Nothing
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA should do nothing to an Invalid value")
    void sequenceInvalid() {
      // Given an Invalid value
      final Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          VALIDATED.widen(Validated.invalid("Initial Error"));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing the original Invalid
      Maybe<Kind<ValidatedKind.Witness<String>, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(VALIDATED.<String, Integer>narrow(maybeResult.get()))
          .isEqualTo(Validated.invalid("Initial Error"));
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    @DisplayName("foldMap on a Valid value should apply the function")
    void foldMap_onValid_shouldApplyFunction() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.widen(Validated.valid(10));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i + 5, valid);

      assertThat(result).isEqualTo(15);
    }

    @Test
    @DisplayName("foldMap on an Invalid value should return the monoid's empty value")
    void foldMap_onInvalid_shouldReturnMonoidEmpty() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("Error"));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i + 5, invalid);

      assertThat(result).isEqualTo(sumMonoid.empty());
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("foldMap on an Invalid value with String monoid should return empty string")
    void foldMap_onInvalid_withStringMonoid_shouldReturnEmptyString() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid =
          VALIDATED.widen(Validated.invalid("Error"));
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, Object::toString, invalid);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }
  }
}
