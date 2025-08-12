// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MaybeTraverseTest {

  private final MaybeTraverse traverse = MaybeTraverse.INSTANCE;
  private final Foldable<MaybeKind.Witness> foldable = MaybeTraverse.INSTANCE;

  // Using Validated as the Applicative context for the tests
  private final Applicative<ValidatedKind.Witness<String>> validatedApplicative =
      ValidatedMonad.instance(Semigroups.string());

  // A test function that validates an integer, returning Invalid for non-positive numbers.
  private final Function<Integer, Kind<ValidatedKind.Witness<String>, Integer>> validatePositive =
      i -> {
        if (i > 0) {
          return VALIDATED.widen(Validated.valid(i));
        } else {
          return VALIDATED.widen(Validated.invalid("Number must be positive"));
        }
      };

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    @DisplayName("map should apply a function over a Just value")
    void mapJust() {
      // Given a Just value
      Kind<MaybeKind.Witness, Integer> input = MAYBE.widen(Maybe.just(5));

      // When we map over it
      Kind<MaybeKind.Witness, Integer> result = traverse.map(i -> i * 10, input);

      // Then the result is a Just containing the new value
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(50));
    }

    @Test
    @DisplayName("map should do nothing to a Nothing value")
    void mapNothing() {
      // Given a Nothing value
      Kind<MaybeKind.Witness, Integer> input = MAYBE.widen(Maybe.nothing());

      // When we map over it
      Kind<MaybeKind.Witness, Integer> result = traverse.map(i -> i * 10, input);

      // Then the result is still Nothing
      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {
    @Test
    @DisplayName("traverse should map a function over a Just value successfully")
    void traverseJustSucceeds() {
      // Given a Just value
      Kind<MaybeKind.Witness, Integer> input = MAYBE.widen(Maybe.just(10));

      // When we traverse it with a function that returns Valid
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.traverse(validatedApplicative, input, validatePositive);

      // Then the result is a Valid containing a Just of the new value
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isValid()).isTrue();
      assertThat(MAYBE.narrow(validatedResult.get())).isEqualTo(Maybe.just(10));
    }

    @Test
    @DisplayName("traverse should return an Invalid when the function fails on a Just value")
    void traverseJustFails() {
      // Given a Just value that will cause the validation to fail
      Kind<MaybeKind.Witness, Integer> input = MAYBE.widen(Maybe.just(-5));

      // When we traverse it with a function that returns Invalid
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.traverse(validatedApplicative, input, validatePositive);

      // Then the result is an Invalid
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isInvalid()).isTrue();
      assertThat(validatedResult.getError()).isEqualTo("Number must be positive");
    }

    @Test
    @DisplayName("traverse should do nothing to a Nothing value")
    void traverseNothing() {
      // Given a Nothing value
      Kind<MaybeKind.Witness, Integer> input = MAYBE.widen(Maybe.nothing());

      // When we traverse it
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.traverse(validatedApplicative, input, validatePositive);

      // Then the result is a Valid containing Nothing
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isValid()).isTrue();
      assertThat(MAYBE.narrow(validatedResult.get())).isEqualTo(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    @Test
    @DisplayName("sequenceA should turn Just<Valid<A>> into Valid<Just<A>>")
    void sequenceJustSucceeds() {
      // Given a Just containing a Valid
      final Kind<ValidatedKind.Witness<String>, Integer> validatedKind =
          VALIDATED.widen(Validated.valid(123));
      final Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
          MAYBE.widen(Maybe.just(validatedKind));

      // When we sequence it
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.sequenceA(validatedApplicative, input);

      // Then the result is a Valid containing a Just
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isValid()).isTrue();
      assertThat(MAYBE.narrow(validatedResult.get())).isEqualTo(Maybe.just(123));
    }

    @Test
    @DisplayName("sequenceA should turn Just<Invalid<E>> into Invalid<E>")
    void sequenceJustFails() {
      // Given a Just containing an Invalid
      final Kind<ValidatedKind.Witness<String>, Integer> validatedKind =
          VALIDATED.widen(Validated.invalid("Validation Error"));
      final Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
          MAYBE.widen(Maybe.just(validatedKind));

      // When we sequence it
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.sequenceA(validatedApplicative, input);

      // Then the result is an Invalid
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isInvalid()).isTrue();
      assertThat(validatedResult.getError()).isEqualTo("Validation Error");
    }

    @Test
    @DisplayName("sequenceA should do nothing to a Nothing value")
    void sequenceNothing() {
      // Given a Nothing value
      final Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
          MAYBE.widen(Maybe.nothing());

      // When we sequence it
      Kind<ValidatedKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> result =
          traverse.sequenceA(validatedApplicative, input);

      // Then the result is a Valid containing Nothing
      Validated<String, Kind<MaybeKind.Witness, Integer>> validatedResult =
          VALIDATED.narrow(result);
      assertThat(validatedResult.isValid()).isTrue();
      assertThat(MAYBE.narrow(validatedResult.get()).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    @DisplayName("foldMap on a Just value should apply the function")
    void foldMap_onJust_shouldApplyFunction() {
      Kind<MaybeKind.Witness, Integer> just = MAYBE.widen(Maybe.just(10));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 3, just);

      assertThat(result).isEqualTo(30);
    }

    @Test
    @DisplayName("foldMap on a Nothing value should return the monoid's empty value")
    void foldMap_onNothing_shouldReturnMonoidEmpty() {
      Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 3, nothing);

      assertThat(result).isEqualTo(sumMonoid.empty());
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("foldMap on a Nothing value with String monoid should return empty string")
    void foldMap_onNothing_withStringMonoid_shouldReturnEmptyString() {
      Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, Object::toString, nothing);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }
  }
}
