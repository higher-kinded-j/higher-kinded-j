// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 08: Natural Transformations
 *
 * <p>A natural transformation is a polymorphic function between type constructors. It converts F[A]
 * to G[A] for any type A, without knowing what A is.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>Natural transformations work for ANY type parameter
 *   <li>They are used to interpret Free monads and Free applicatives
 *   <li>They compose: (F ~> G) and (G ~> H) gives (F ~> H)
 *   <li>The identity transformation leaves values unchanged
 * </ul>
 *
 * <p>Links to documentation: <a
 * href="https://higher-kinded-j.github.io/latest/functional/natural_transformation.html">Natural
 * Transformation Guide</a>
 */
public class Tutorial08_NaturalTransformation {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Maybe to List transformation
   *
   * <p>Create a natural transformation that converts Maybe to List. Nothing becomes an empty list,
   * Just(x) becomes a singleton list [x].
   *
   * <p>Task: Implement the transformation
   */
  @Test
  void exercise1_maybeToList() {
    // TODO: Replace null with a Natural transformation from Maybe to List
    // Hint: Use Maybe.fold() to handle Nothing and Just cases
    // Nothing -> List.of(), Just(x) -> List.of(x)
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList = answerRequired();

    // Test with Just
    Kind<MaybeKind.Witness, String> justHello = MAYBE.widen(Maybe.just("hello"));
    Kind<ListKind.Witness, String> listFromJust = maybeToList.apply(justHello);
    assertThat(LIST.narrow(listFromJust)).containsExactly("hello");

    // Test with Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<ListKind.Witness, String> listFromNothing = maybeToList.apply(nothing);
    assertThat(LIST.narrow(listFromNothing)).isEmpty();
  }

  /**
   * Exercise 2: Either to Maybe transformation
   *
   * <p>Create a natural transformation that converts Either<String, A> to Maybe<A>. Left becomes
   * Nothing (discarding the error), Right(x) becomes Just(x).
   *
   * <p>Task: Implement the transformation
   */
  @Test
  void exercise2_eitherToMaybe() {
    // TODO: Replace null with a Natural transformation from Either<String, _> to Maybe
    // Hint: Use Either.fold() to handle Left and Right cases
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe = answerRequired();

    // Test with Right
    Kind<EitherKind.Witness<String>, Integer> right = EITHER.widen(Either.right(42));
    Kind<MaybeKind.Witness, Integer> maybeFromRight = eitherToMaybe.apply(right);
    assertThat(MAYBE.narrow(maybeFromRight)).isEqualTo(Maybe.just(42));

    // Test with Left
    Kind<EitherKind.Witness<String>, Integer> left = EITHER.widen(Either.left("error"));
    Kind<MaybeKind.Witness, Integer> maybeFromLeft = eitherToMaybe.apply(left);
    assertThat(MAYBE.narrow(maybeFromLeft)).isEqualTo(Maybe.nothing());
  }

  /**
   * Exercise 3: List head transformation
   *
   * <p>Create a natural transformation that takes the head of a list. Empty list becomes Nothing,
   * non-empty list becomes Just(first element).
   *
   * <p>Task: Implement the transformation
   */
  @Test
  void exercise3_listHead() {
    // TODO: Replace null with a Natural transformation from List to Maybe
    // Hint: Check if list is empty, return Maybe.nothing() or Maybe.just(list.get(0))
    Natural<ListKind.Witness, MaybeKind.Witness> listHead = answerRequired();

    // Test with non-empty list
    Kind<ListKind.Witness, String> nonEmpty = LIST.widen(List.of("first", "second"));
    Kind<MaybeKind.Witness, String> headNonEmpty = listHead.apply(nonEmpty);
    assertThat(MAYBE.narrow(headNonEmpty)).isEqualTo(Maybe.just("first"));

    // Test with empty list
    Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
    Kind<MaybeKind.Witness, String> headEmpty = listHead.apply(empty);
    assertThat(MAYBE.narrow(headEmpty)).isEqualTo(Maybe.nothing());
  }

  /**
   * Exercise 4: Composition of natural transformations
   *
   * <p>Natural transformations compose: (F ~> G) andThen (G ~> H) gives (F ~> H).
   *
   * <p>Task: Compose two transformations
   */
  @Test
  void exercise4_composition() {
    // Given transformations
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList =
        new Natural<>() {
          @Override
          public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            Maybe<A> maybe = MAYBE.narrow(fa);
            return LIST.widen(maybe.map(List::of).orElse(List.of()));
          }
        };

    Natural<ListKind.Witness, MaybeKind.Witness> listHead =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
            List<A> list = LIST.narrow(fa);
            return MAYBE.widen(list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0)));
          }
        };

    // TODO: Compose maybeToList and listHead to create Maybe ~> Maybe
    // Hint: Use maybeToList.andThen(listHead)
    Natural<MaybeKind.Witness, MaybeKind.Witness> composed = answerRequired();

    // Test: Just(x) -> [x] -> Just(x)
    Kind<MaybeKind.Witness, String> justValue = MAYBE.widen(Maybe.just("test"));
    Kind<MaybeKind.Witness, String> result = composed.apply(justValue);
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("test"));

    // Test: Nothing -> [] -> Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<MaybeKind.Witness, String> resultNothing = composed.apply(nothing);
    assertThat(MAYBE.narrow(resultNothing)).isEqualTo(Maybe.nothing());
  }

  /**
   * Exercise 5: Identity transformation
   *
   * <p>The identity transformation returns its input unchanged. It satisfies: identity.andThen(nat)
   * == nat nat.andThen(identity) == nat
   *
   * <p>Task: Use the identity transformation
   */
  @Test
  void exercise5_identity() {
    // TODO: Get the identity transformation for Maybe
    // Hint: Use Natural.identity()
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = answerRequired();

    Kind<MaybeKind.Witness, Integer> original = MAYBE.widen(Maybe.just(42));

    // Apply identity
    Kind<MaybeKind.Witness, Integer> afterIdentity = identity.apply(original);

    // Should be unchanged
    assertThat(MAYBE.narrow(afterIdentity)).isEqualTo(Maybe.just(42));

    // Should be the same reference (identity does nothing)
    assertThat(afterIdentity).isSameAs(original);
  }

  /**
   * Congratulations! You've completed Tutorial 08: Natural Transformations
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>Natural transformations convert between type constructors
   *   <li>They work polymorphically for any type parameter
   *   <li>Natural transformations compose with andThen
   *   <li>The identity transformation leaves values unchanged
   *   <li>They are essential for Free monad interpretation
   * </ul>
   *
   * <p>Next: Tutorial 09 - Coyoneda
   */
}
