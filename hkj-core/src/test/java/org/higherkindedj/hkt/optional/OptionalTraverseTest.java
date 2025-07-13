// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class OptionalTraverseTest {

  private final OptionalTraverse traverse = OptionalTraverse.INSTANCE;
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

  @Test
  @DisplayName("traverse should map a function over a present Optional")
  void traversePresentSucceeds() {
    // Given a present Optional
    Optional<Integer> optional = Optional.of(123);
    Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(optional);

    // When we traverse it with a function that returns a Just
    Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is a Just containing a present Optional of the new value
    Maybe<Kind<OptionalKind.Witness, String>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(OPTIONAL.narrow(maybeResult.get())).isEqualTo(Optional.of("Positive: 123"));
  }

  @Test
  @DisplayName("traverse should return Nothing when the function fails on a present Optional")
  void traversePresentFails() {
    // Given a present Optional with a value that will cause the function to fail
    Optional<Integer> optional = Optional.of(-1);
    Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(optional);

    // When we traverse it with a function that returns Nothing
    Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is Nothing
    assertThat(MAYBE.narrow(result).isNothing()).isTrue();
  }

  @Test
  @DisplayName("traverse should do nothing to an empty Optional")
  void traverseEmpty() {
    // Given an empty Optional
    Optional<Integer> optional = Optional.empty();
    Kind<OptionalKind.Witness, Integer> input = OPTIONAL.widen(optional);

    // When we traverse it
    Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, String>> result =
        traverse.traverse(maybeApplicative, input, validatePositive);

    // Then the result is a Just containing an empty Optional
    Maybe<Kind<OptionalKind.Witness, String>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(OPTIONAL.narrow(maybeResult.get())).isEqualTo(Optional.empty());
  }

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
    final Optional<Kind<MaybeKind.Witness, Integer>> optional = Optional.empty();
    final Kind<OptionalKind.Witness, Kind<MaybeKind.Witness, Integer>> input =
        OPTIONAL.widen(optional);

    // When we sequence it
    Kind<MaybeKind.Witness, Kind<OptionalKind.Witness, Integer>> result =
        traverse.sequenceA(maybeApplicative, input);

    // Then the result is a Just containing an empty Optional
    Maybe<Kind<OptionalKind.Witness, Integer>> maybeResult = MAYBE.narrow(result);
    assertThat(maybeResult.isJust()).isTrue();
    assertThat(OPTIONAL.narrow(maybeResult.get()).isEmpty()).isTrue();
  }
}
