// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: Understanding Kind - The Foundation of Higher-Kinded Types
 *
 * <p>In this tutorial, you'll learn about the Kind interface, which is the core mechanism for
 * simulating higher-kinded types in Java.
 *
 * <p>Key Concepts: - Kind<F, A> represents a type constructor F applied to a type A - F is a
 * "witness type" that represents the type constructor (e.g., Either, List, Maybe) - A is the type
 * parameter (e.g., Integer, String) - Widening: Converting a concrete type to Kind - Narrowing:
 * Converting Kind back to a concrete type
 *
 * <p>Replace each ___ with the correct code to make the tests pass.
 */
public class Tutorial01_KindBasics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /**
   * Exercise 1: Widening to Kind
   *
   * <p>Every higher-kinded type in this library can be "widened" to its Kind representation. This
   * allows us to work with different types uniformly.
   *
   * <p>Task: Widen an Either<String, Integer> to Kind<EitherKind.Witness<String>, Integer>
   */
  @Test
  void exercise1_widenEitherToKind() {
    Either<String, Integer> either = Either.right(42);

    // TODO: Replace null with code that widens either to Kind
    // Hint: Use the EITHER helper's widen method
    Kind<EitherKind.Witness<String>, Integer> kind = answerRequired();

    // Verify by narrowing back and checking the value
    assertThat(EITHER.narrow(kind).getRight()).isEqualTo(42);
  }

  /**
   * Exercise 2: Narrowing from Kind
   *
   * <p>Once you have a Kind, you can "narrow" it back to the concrete type to access specific
   * methods.
   *
   * <p>Task: Narrow a Kind back to Either and extract the value
   */
  @Test
  void exercise2_narrowKindToEither() {
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(Either.right(100));

    // TODO: Replace null with code that narrows kind to Either
    // Hint: Use the EITHER helper's narrow method
    Either<String, Integer> either = answerRequired();

    assertThat(either.isRight()).isTrue();
    assertThat(either.getRight()).isEqualTo(100);
  }

  /**
   * Exercise 3: Working with List Kind
   *
   * <p>Lists work the same way - they can be widened to Kind and narrowed back.
   *
   * <p>Task: Widen a List to Kind, then narrow it back
   */
  @Test
  void exercise3_listKindConversion() {
    List<String> list = List.of("apple", "banana", "cherry");

    // TODO: Replace null with code that widens the list to Kind
    // Hint: Use the LIST helper's widen method
    Kind<ListKind.Witness, String> kind = answerRequired();

    // TODO: Replace null with code that narrows kind back to List
    // Hint: Use the LIST helper's narrow method
    List<String> narrowedList = answerRequired();

    assertThat(narrowedList.size()).isEqualTo(3);
    assertThat(narrowedList).containsExactly("apple", "banana", "cherry");
  }

  /**
   * Exercise 4: Understanding Witness Types
   *
   * <p>The witness type (F in Kind<F, A>) identifies which type constructor we're working with. -
   * EitherKind.Witness<L> for Either<L, R> - ListKind.Witness for ListOf<A> - MaybeKind.Witness for
   * Maybe<A>
   *
   * <p>Task: Create a Kind for Either<String, Boolean> with the correct witness type
   */
  @Test
  void exercise4_understandingWitnessTypes() {
    Either<String, Boolean> either = Either.right(true);

    // TODO: Fill in the correct witness type for Either<String, Boolean>
    // Hint: The witness type should be EitherKind.Witness<String>
    Kind<EitherKind.Witness<String>, Boolean> kind = EITHER.widen(either);

    Either<String, Boolean> result = EITHER.narrow(kind);
    assertThat(result.getRight()).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 01: Kind Basics
   *
   * <p>You now understand: ✓ What Kind<F, A> represents ✓ How to widen concrete types to Kind ✓ How
   * to narrow Kind back to concrete types ✓ What witness types are and how they work
   *
   * <p>Next: Tutorial 02 - Functor Mapping
   */
}
