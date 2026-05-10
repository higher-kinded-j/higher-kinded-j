// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.either.Either;
import org.junit.jupiter.api.DisplayName;

/**
 * Coverage contract for {@link EitherAssert}. Every public method is exercised on at least one
 * passing input and at least one failing input via {@link AssertContract}.
 *
 * <p>For methods with two distinct failure branches (wrong-state vs right-state-wrong-value), each
 * branch is a separate row.
 */
@DisplayName("EitherAssert contract")
class EitherAssertContractTest
    extends AssertContract<Either<String, Integer>, EitherAssert<String, Integer>> {

  private static final Either<String, Integer> RIGHT_42 = Either.right(42);
  private static final Either<String, Integer> RIGHT_99 = Either.right(99);
  private static final Either<String, Integer> RIGHT_NULL = Either.right(null);
  private static final Either<String, Integer> LEFT_ERR = Either.left("err");
  private static final Either<String, Integer> LEFT_OTHER = Either.left("other");
  private static final Either<String, Integer> LEFT_NULL = Either.left(null);

  @Override
  protected Function<Either<String, Integer>, EitherAssert<String, Integer>> entry() {
    return EitherAssert::assertThatEither;
  }

  @Override
  protected Stream<Row<Either<String, Integer>, EitherAssert<String, Integer>>> rows() {
    return Stream.of(
        // State predicates
        row("isRight", RIGHT_42, LEFT_ERR, EitherAssert::isRight),
        row("isLeft", LEFT_ERR, RIGHT_42, EitherAssert::isLeft),

        // hasRight / hasLeft — both wrong-state and wrong-value failure branches
        row("hasRight value match", RIGHT_42, RIGHT_99, a -> a.hasRight(42)),
        row("hasRight wrong state", RIGHT_42, LEFT_ERR, a -> a.hasRight(42)),
        row("hasLeft value match", LEFT_ERR, LEFT_OTHER, a -> a.hasLeft("err")),
        row("hasLeft wrong state", LEFT_ERR, RIGHT_42, a -> a.hasLeft("err")),

        // *Satisfying — wrong-state branch (the consumer accepts everything)
        row(
            "hasRightSatisfying wrong state",
            RIGHT_42,
            LEFT_ERR,
            a -> a.hasRightSatisfying(v -> {})),
        row("hasLeftSatisfying wrong state", LEFT_ERR, RIGHT_42, a -> a.hasLeftSatisfying(v -> {})),
        // *Satisfying — inner-consumer-fails branch (no passing case for this specific chain)
        failOnly(
            "hasRightSatisfying inner fails",
            RIGHT_42,
            a ->
                a.hasRightSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),
        failOnly(
            "hasLeftSatisfying inner fails",
            LEFT_ERR,
            a ->
                a.hasLeftSatisfying(
                    v -> {
                      throw new AssertionError("inner");
                    })),

        // Null checks
        row("hasRightNull", RIGHT_NULL, RIGHT_42, EitherAssert::hasRightNull),
        row("hasRightNull wrong state", RIGHT_NULL, LEFT_ERR, EitherAssert::hasRightNull),
        row("hasLeftNull", LEFT_NULL, LEFT_ERR, EitherAssert::hasLeftNull),
        row("hasLeftNull wrong state", LEFT_NULL, RIGHT_42, EitherAssert::hasLeftNull),
        row("hasRightNonNull", RIGHT_42, RIGHT_NULL, EitherAssert::hasRightNonNull),
        row("hasRightNonNull wrong state", RIGHT_42, LEFT_ERR, EitherAssert::hasRightNonNull),
        row("hasLeftNonNull", LEFT_ERR, LEFT_NULL, EitherAssert::hasLeftNonNull),
        row("hasLeftNonNull wrong state", LEFT_ERR, RIGHT_42, EitherAssert::hasLeftNonNull));
  }
}
