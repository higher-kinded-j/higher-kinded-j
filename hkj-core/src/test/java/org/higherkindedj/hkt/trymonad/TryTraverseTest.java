// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

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

public class TryTraverseTest {

  private final TryTraverse traverse = TryTraverse.INSTANCE;
  private final Foldable<TryKind.Witness> foldable = TryTraverse.INSTANCE;
  private final Applicative<MaybeKind.Witness> maybeApplicative = MaybeMonad.INSTANCE;

  // A test function that returns a Maybe of a string, or Nothing if the input is negative.
  private final Function<Integer, Kind<MaybeKind.Witness, String>> validatePositive =
      i -> {
        if (i >= 0) {
          return MAYBE.widen(Maybe.just("Value: " + i));
        } else {
          return MAYBE.widen(Maybe.nothing());
        }
      };

  @Nested
  @DisplayName("map method")
  class MapTests {
    @Test
    @DisplayName("map should apply a function to a Success value")
    void mapSuccess() {
      // Given a Success value
      Kind<TryKind.Witness, Integer> input = TRY.widen(Try.success(10));

      // When we map over it
      Kind<TryKind.Witness, Integer> result = traverse.map(i -> i * 2, input);

      // Then the result is a Success with the new value
      assertThat(TRY.narrow(result)).isEqualTo(Try.success(20));
    }

    @Test
    @DisplayName("map should do nothing to a Failure value")
    void mapFailure() {
      // Given a Failure value
      final Throwable error = new RuntimeException("Error");
      Kind<TryKind.Witness, Integer> input = TRY.widen(Try.failure(error));

      // When we map over it
      Kind<TryKind.Witness, Integer> result = traverse.map(i -> i * 2, input);
      final Try<Integer> tryResult = TRY.narrow(result);

      // Then the result is still the original Failure
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isInstanceOf(RuntimeException.class).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("traverse method")
  class TraverseTests {
    @Test
    @DisplayName("traverse should map a function over a Success value")
    void traverseSuccessSucceeds() throws Throwable {
      // Given a Success value
      Kind<TryKind.Witness, Integer> input = TRY.widen(Try.success(42));

      // When we traverse it with a function that succeeds
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, input, validatePositive);

      // Then the result is a Just containing a Success of the new value
      Maybe<Kind<TryKind.Witness, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(TRY.narrow(maybeResult.get()).get()).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("traverse should return Nothing when the function fails on a Success value")
    void traverseSuccessFails() {
      // Given a Success value that will cause the function to fail
      Kind<TryKind.Witness, Integer> input = TRY.widen(Try.success(-1));

      // When we traverse it with a function that returns Nothing
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, input, validatePositive);

      // Then the result is Nothing
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse should do nothing to a Failure value")
    void traverseFailure() {
      // Given a Failure value
      final Throwable error = new RuntimeException("Initial Error");
      Kind<TryKind.Witness, Integer> input = TRY.widen(Try.failure(error));

      // When we traverse it
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, String>> result =
          traverse.traverse(maybeApplicative, input, validatePositive);

      // Then the result is a Just containing the original Failure
      Maybe<Kind<TryKind.Witness, String>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();

      final Try<String> tryResult = TRY.narrow(maybeResult.get());
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isInstanceOf(RuntimeException.class).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("sequenceA method")
  class SequenceATests {
    @Test
    @DisplayName("sequenceA should turn Success<Just<A>> into Just<Success<A>>")
    void sequenceSuccessSucceeds() {
      // Given a Success containing a Just
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(123));
      final Kind<TryKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
          TRY.widen(Try.success(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing a Success
      Maybe<Kind<TryKind.Witness, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();
      assertThat(TRY.narrow(maybeResult.get())).isEqualTo(Try.success(123));
    }

    @Test
    @DisplayName("sequenceA should turn Success<Nothing> into Nothing")
    void sequenceSuccessFails() {
      // Given a Success containing a Nothing
      final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.nothing());
      final Kind<TryKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
          TRY.widen(Try.success(maybeKind));

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is Nothing
      assertThat(MAYBE.narrow(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("sequenceA should do nothing to a Failure value")
    void sequenceFailure() {
      // Given a Failure value
      final Throwable error = new RuntimeException("Initial Error");
      final Try<Kind<MaybeKind.Witness, Integer>> tryMonad = Try.failure(error);
      final Kind<TryKind.Witness, Kind<MaybeKind.Witness, Integer>> input = TRY.widen(tryMonad);

      // When we sequence it
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      // Then the result is a Just containing the original Failure
      Maybe<Kind<TryKind.Witness, Integer>> maybeResult = MAYBE.narrow(result);
      assertThat(maybeResult.isJust()).isTrue();

      final Try<Integer> tryResult = TRY.narrow(maybeResult.get());
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isInstanceOf(RuntimeException.class).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("foldMap method")
  class FoldMapTests {
    @Test
    @DisplayName("foldMap on a Success value should apply the function")
    void foldMap_onSuccess_shouldApplyFunction() {
      Kind<TryKind.Witness, Integer> success = TRY.widen(Try.success(10));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 2, success);

      assertThat(result).isEqualTo(20);
    }

    @Test
    @DisplayName("foldMap on a Failure value should return the monoid's empty value")
    void foldMap_onFailure_shouldReturnMonoidEmpty() {
      Kind<TryKind.Witness, Integer> failure = TRY.widen(Try.failure(new Exception("fail")));
      Monoid<Integer> sumMonoid = Monoids.integerAddition();

      Integer result = foldable.foldMap(sumMonoid, i -> i * 2, failure);

      assertThat(result).isEqualTo(sumMonoid.empty());
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("foldMap on a Failure value with String monoid should return empty string")
    void foldMap_onFailure_withStringMonoid_shouldReturnEmptyString() {
      Kind<TryKind.Witness, Integer> failure = TRY.widen(Try.failure(new Exception("fail")));
      Monoid<String> stringMonoid = Monoids.string();

      String result = foldable.foldMap(stringMonoid, Object::toString, failure);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }
  }
}
