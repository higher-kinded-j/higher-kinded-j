// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

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
 * Tutorial 08: Natural Transformations - SOLUTIONS
 *
 * <p>This file contains the solutions for all exercises in Tutorial 08.
 */
public class Tutorial08_NaturalTransformation_Solution {

  @Test
  void exercise1_maybeToList() {
    // SOLUTION: Create a Natural transformation using anonymous class
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList =
        new Natural<>() {
          @Override
          public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            Maybe<A> maybe = MAYBE.narrow(fa);
            List<A> list = maybe.map(List::of).orElse(List.of());
            return LIST.widen(list);
          }
        };

    // Test with Just
    Kind<MaybeKind.Witness, String> justHello = MAYBE.widen(Maybe.just("hello"));
    Kind<ListKind.Witness, String> listFromJust = maybeToList.apply(justHello);
    assertThat(LIST.narrow(listFromJust)).containsExactly("hello");

    // Test with Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<ListKind.Witness, String> listFromNothing = maybeToList.apply(nothing);
    assertThat(LIST.narrow(listFromNothing)).isEmpty();
  }

  @Test
  void exercise2_eitherToMaybe() {
    // SOLUTION: Transform Either<String, A> to Maybe<A>
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<EitherKind.Witness<String>, A> fa) {
            Either<String, A> either = EITHER.narrow(fa);
            Maybe<A> maybe = either.fold(left -> Maybe.nothing(), Maybe::just);
            return MAYBE.widen(maybe);
          }
        };

    // Test with Right
    Kind<EitherKind.Witness<String>, Integer> right = EITHER.widen(Either.right(42));
    Kind<MaybeKind.Witness, Integer> maybeFromRight = eitherToMaybe.apply(right);
    assertThat(MAYBE.narrow(maybeFromRight)).isEqualTo(Maybe.just(42));

    // Test with Left
    Kind<EitherKind.Witness<String>, Integer> left = EITHER.widen(Either.left("error"));
    Kind<MaybeKind.Witness, Integer> maybeFromLeft = eitherToMaybe.apply(left);
    assertThat(MAYBE.narrow(maybeFromLeft)).isEqualTo(Maybe.nothing());
  }

  @Test
  void exercise3_listHead() {
    // SOLUTION: Take the head of a list
    Natural<ListKind.Witness, MaybeKind.Witness> listHead =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
            List<A> list = LIST.narrow(fa);
            Maybe<A> head = list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0));
            return MAYBE.widen(head);
          }
        };

    // Test with non-empty list
    Kind<ListKind.Witness, String> nonEmpty = LIST.widen(List.of("first", "second"));
    Kind<MaybeKind.Witness, String> headNonEmpty = listHead.apply(nonEmpty);
    assertThat(MAYBE.narrow(headNonEmpty)).isEqualTo(Maybe.just("first"));

    // Test with empty list
    Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
    Kind<MaybeKind.Witness, String> headEmpty = listHead.apply(empty);
    assertThat(MAYBE.narrow(headEmpty)).isEqualTo(Maybe.nothing());
  }

  @Test
  void exercise4_composition() {
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

    // SOLUTION: Compose using andThen
    Natural<MaybeKind.Witness, MaybeKind.Witness> composed = maybeToList.andThen(listHead);

    // Test: Just(x) -> [x] -> Just(x)
    Kind<MaybeKind.Witness, String> justValue = MAYBE.widen(Maybe.just("test"));
    Kind<MaybeKind.Witness, String> result = composed.apply(justValue);
    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("test"));

    // Test: Nothing -> [] -> Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<MaybeKind.Witness, String> resultNothing = composed.apply(nothing);
    assertThat(MAYBE.narrow(resultNothing)).isEqualTo(Maybe.nothing());
  }

  @Test
  void exercise5_identity() {
    // SOLUTION: Use Natural.identity()
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = Natural.identity();

    Kind<MaybeKind.Witness, Integer> original = MAYBE.widen(Maybe.just(42));

    // Apply identity
    Kind<MaybeKind.Witness, Integer> afterIdentity = identity.apply(original);

    // Should be unchanged
    assertThat(MAYBE.narrow(afterIdentity)).isEqualTo(Maybe.just(42));

    // Should be the same reference
    assertThat(afterIdentity).isSameAs(original);
  }
}
