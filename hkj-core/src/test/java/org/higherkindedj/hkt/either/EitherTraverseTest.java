// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EitherTraverseTest {

  private final EitherTraverse<String> traverse = EitherTraverse.instance();
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

  @Test
  @DisplayName("map should apply a function to a Right value")
  void mapRight() {
    // Given a Right value
    Kind<EitherKind.Witness<String>, Integer> input = EITHER.widen(Either.right(42));

    // When we map over it
    Kind<EitherKind.Witness<String>, String> result = traverse.map(i -> "Answer: " + i, input);

    // Then the result is a Right with the new value
    assertThat(EITHER.<String, String>narrow(result)).isEqualTo(Either.right("Answer: 42"));
  }

  @Test
  @DisplayName("map should do nothing to a Left value")
  void mapLeft() {
    // Given a Left value
    Kind<EitherKind.Witness<String>, Integer> input = EITHER.widen(Either.left("Error"));

    // When we map over it
    Kind<EitherKind.Witness<String>, String> result = traverse.map(i -> "Answer: " + i, input);

    // Then the result is still the original Left
    assertThat(EITHER.<String, String>narrow(result)).isEqualTo(Either.left("Error"));
  }

  @Test
  @DisplayName("traverse should map a function over a Right value successfully")
  void traverseRightSucceeds() {
    // Given a Right value
    Either<String, Integer> either = Either.right(123);
    Kind<EitherKind.Witness<String>, Integer> input = EITHER.widen(either);

    // When we traverse it with a function that succeeds
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is a Just containing a Right of the new value
    Maybe<Kind<EitherKind.Witness<String>, String>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(EITHER.<String, String>narrow(maybeResult.get()))
        .isEqualTo(Either.right("Positive: 123"));
  }

  @Test
  @DisplayName("traverse should return Nothing when the function fails on a Right value")
  void traverseRightFails() {
    // Given a Right value that will cause the function to fail
    Either<String, Integer> either = Either.right(-1);
    Kind<EitherKind.Witness<String>, Integer> input = EITHER.widen(either);

    // When we traverse it with a function that returns Nothing
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is Nothing
    assertThat(MAYBE.narrow(result).isNothing()).isTrue();
  }

  @Test
  @DisplayName("traverse should do nothing to a Left value")
  void traverseLeft() {
    // Given a Left value
    Either<String, Integer> either = Either.left("Initial Error");
    Kind<EitherKind.Witness<String>, Integer> input = EITHER.widen(either);

    // When we traverse it
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is a Just containing the original Left
    Maybe<Kind<EitherKind.Witness<String>, String>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(EITHER.<String, String>narrow(maybeResult.get()))
        .isEqualTo(Either.left("Initial Error"));
  }

  @Test
  @DisplayName("sequenceA should turn Right<Just<A>> into Just<Right<A>>")
  void sequenceRightSucceeds() {
    // Given a Right containing a Just
    final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(42));
    final Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
        EITHER.widen(Either.right(maybeKind));

    // When we sequence it
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
        traverse.sequenceA(maybeApplicative, input);

    // Then the result is a Just containing a Right
    Maybe<Kind<EitherKind.Witness<String>, Integer>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(EITHER.<String, Integer>narrow(maybeResult.get())).isEqualTo(Either.right(42));
  }

  @Test
  @DisplayName("sequenceA should turn Right<Nothing> into Nothing")
  void sequenceRightFails() {
    // Given a Right containing a Nothing
    final Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.nothing());
    final Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
        EITHER.widen(Either.right(maybeKind));

    // When we sequence it
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
        traverse.sequenceA(maybeApplicative, input);

    // Then the result is Nothing
    assertThat(MAYBE.narrow(result).isNothing()).isTrue();
  }

  @Test
  @DisplayName("sequenceA should do nothing to a Left value")
  void sequenceLeft() {
    // Given a Left value
    final Either<String, Kind<MaybeKind.Witness, Integer>> either = Either.left("Initial Error");
    final Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
        EITHER.widen(either);

    // When we sequence it
    Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
        traverse.sequenceA(maybeApplicative, input);

    // Then the result is a Just containing the original Left
    Maybe<Kind<EitherKind.Witness<String>, Integer>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(EITHER.<String, Integer>narrow(maybeResult.get()))
        .isEqualTo(Either.left("Initial Error"));
  }
}
