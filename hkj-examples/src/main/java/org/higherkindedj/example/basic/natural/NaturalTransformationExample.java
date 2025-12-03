// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.natural;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Demonstrates Natural Transformations in Higher-Kinded-J.
 *
 * <p>A natural transformation is a polymorphic function between type constructors. It transforms
 * F[A] to G[A] for any type A, without knowing what A is.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>Basic natural transformations (Maybe to List, Either to Maybe)
 *   <li>Composition of natural transformations
 *   <li>The identity transformation
 *   <li>Practical type conversions
 * </ul>
 *
 * <p>Run with: ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.natural.NaturalTransformationExample
 */
public class NaturalTransformationExample {

  public static void main(String[] args) {
    System.out.println("=== Natural Transformation Examples ===\n");

    maybeToListExample();
    eitherToMaybeExample();
    listHeadExample();
    compositionExample();
    identityExample();

    System.out.println("\n=== All examples completed ===");
  }

  /**
   * Example 1: Maybe to List transformation.
   *
   * <p>Nothing becomes an empty list, Just(x) becomes a singleton list [x].
   */
  private static void maybeToListExample() {
    System.out.println("--- Example 1: Maybe to List ---");

    // Define the natural transformation
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
    Kind<MaybeKind.Witness, String> justHello = MAYBE.widen(Maybe.just("Hello"));
    Kind<ListKind.Witness, String> listFromJust = maybeToList.apply(justHello);
    System.out.println("Maybe.just(\"Hello\") -> " + LIST.narrow(listFromJust));

    // Test with Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<ListKind.Witness, String> listFromNothing = maybeToList.apply(nothing);
    System.out.println("Maybe.nothing() -> " + LIST.narrow(listFromNothing));

    // Works with any type A
    Kind<MaybeKind.Witness, Integer> justNumber = MAYBE.widen(Maybe.just(42));
    Kind<ListKind.Witness, Integer> listFromNumber = maybeToList.apply(justNumber);
    System.out.println("Maybe.just(42) -> " + LIST.narrow(listFromNumber));

    System.out.println();
  }

  /**
   * Example 2: Either to Maybe transformation.
   *
   * <p>Left(error) becomes Nothing, Right(value) becomes Just(value). This discards the error
   * information.
   */
  private static void eitherToMaybeExample() {
    System.out.println("--- Example 2: Either to Maybe ---");

    // Define the natural transformation (for Either<String, A>)
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<EitherKind.Witness<String>, A> fa) {
            Either<String, A> either = EITHER.narrow(fa);
            Maybe<A> maybe =
                either.fold(
                    left -> Maybe.nothing(), // Error -> Nothing
                    right -> Maybe.just(right) // Success -> Just
                    );
            return MAYBE.widen(maybe);
          }
        };

    // Test with Right
    Kind<EitherKind.Witness<String>, Integer> right = EITHER.widen(Either.right(100));
    Kind<MaybeKind.Witness, Integer> maybeFromRight = eitherToMaybe.apply(right);
    System.out.println("Either.right(100) -> " + MAYBE.narrow(maybeFromRight));

    // Test with Left
    Kind<EitherKind.Witness<String>, Integer> left = EITHER.widen(Either.left("Error occurred"));
    Kind<MaybeKind.Witness, Integer> maybeFromLeft = eitherToMaybe.apply(left);
    System.out.println("Either.left(\"Error occurred\") -> " + MAYBE.narrow(maybeFromLeft));

    System.out.println();
  }

  /**
   * Example 3: List head transformation.
   *
   * <p>Empty list becomes Nothing, non-empty list becomes Just(first element).
   */
  private static void listHeadExample() {
    System.out.println("--- Example 3: List Head ---");

    // Define the natural transformation
    Natural<ListKind.Witness, MaybeKind.Witness> listHead =
        new Natural<>() {
          @Override
          @SuppressWarnings("unchecked")
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
            List<A> list = LIST.narrow(fa);
            Maybe<A> head = list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0));
            return MAYBE.widen(head);
          }
        };

    // Test with non-empty list
    Kind<ListKind.Witness, String> nonEmpty = LIST.widen(List.of("first", "second", "third"));
    Kind<MaybeKind.Witness, String> headOfNonEmpty = listHead.apply(nonEmpty);
    System.out.println(
        "List.of(\"first\", \"second\", \"third\").head -> " + MAYBE.narrow(headOfNonEmpty));

    // Test with empty list
    Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
    Kind<MaybeKind.Witness, String> headOfEmpty = listHead.apply(empty);
    System.out.println("List.of().head -> " + MAYBE.narrow(headOfEmpty));

    System.out.println();
  }

  /**
   * Example 4: Composition of natural transformations.
   *
   * <p>Natural transformations compose: (F ~> G) and (G ~> H) gives (F ~> H).
   */
  private static void compositionExample() {
    System.out.println("--- Example 4: Composition ---");

    // Maybe -> Optional
    Natural<MaybeKind.Witness, OptionalKind.Witness> maybeToOptional =
        new Natural<>() {
          @Override
          public <A> Kind<OptionalKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            Maybe<A> maybe = MAYBE.narrow(fa);
            Optional<A> optional = maybe.map(Optional::of).orElse(Optional.empty());
            return OPTIONAL.widen(optional);
          }
        };

    // Optional -> List
    Natural<OptionalKind.Witness, ListKind.Witness> optionalToList =
        new Natural<>() {
          @Override
          public <A> Kind<ListKind.Witness, A> apply(Kind<OptionalKind.Witness, A> fa) {
            Optional<A> optional = OPTIONAL.narrow(fa);
            List<A> list = optional.map(List::of).orElse(List.of());
            return LIST.widen(list);
          }
        };

    // Compose: Maybe -> Optional -> List
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList =
        maybeToOptional.andThen(optionalToList);

    // Test the composed transformation
    Kind<MaybeKind.Witness, String> justValue = MAYBE.widen(Maybe.just("composed"));
    Kind<ListKind.Witness, String> result = maybeToList.apply(justValue);
    System.out.println("Maybe.just(\"composed\") via Optional to List -> " + LIST.narrow(result));

    Kind<MaybeKind.Witness, String> nothingValue = MAYBE.widen(Maybe.nothing());
    Kind<ListKind.Witness, String> resultNothing = maybeToList.apply(nothingValue);
    System.out.println("Maybe.nothing() via Optional to List -> " + LIST.narrow(resultNothing));

    System.out.println();
  }

  /**
   * Example 5: Identity transformation.
   *
   * <p>The identity transformation returns its input unchanged.
   */
  private static void identityExample() {
    System.out.println("--- Example 5: Identity ---");

    // Get identity transformation for Maybe
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = Natural.identity();

    Kind<MaybeKind.Witness, String> original = MAYBE.widen(Maybe.just("unchanged"));
    Kind<MaybeKind.Witness, String> result = identity.apply(original);

    System.out.println("Original: " + MAYBE.narrow(original));
    System.out.println("After identity: " + MAYBE.narrow(result));
    System.out.println("Same reference: " + (original == result));
  }
}
