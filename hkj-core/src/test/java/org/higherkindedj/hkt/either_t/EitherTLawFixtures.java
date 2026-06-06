// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherTMonadTest.TestError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code EitherT} type-class tests (inner monad = {@code Optional},
 * Left = {@link TestError}).
 *
 * <p>Referenced from the {@code *LawTests} blocks via a fully-qualified {@code @MethodSource}. The
 * kinds span the three transformer states — {@code Right}, {@code Left}, and an empty outer {@code
 * Optional} — so each law is verified over all of them. The {@link TestError} Left type is shared
 * with {@link EitherTMonadTest} so the produced kinds match its witness exactly.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class EitherTLawFixtures {

  private EitherTLawFixtures() {}

  private static final MonadError<OptionalKind.Witness, Unit> OUTER =
      Instances.monadError(optional());

  private static <A> Optional<Either<TestError, A>> unwrapKind(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, A> kind) {
    var eitherT = EITHER_T.narrow(kind);
    Kind<OptionalKind.Witness, Either<TestError, A>> outerKind = eitherT.value();
    return OPTIONAL.narrow(outerKind);
  }

  /** Shared law equality: run/unwrap to {@code Optional<Either<TestError, ·>>} and compare. */
  static final BiPredicate<
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>,
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>>
      EQ = KindEquivalence.byEqualsAfter(EitherTLawFixtures::unwrapKind);

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> rightT(
      int value) {
    return EITHER_T.widen(EitherT.right(OUTER, value));
  }

  @SuppressWarnings("SameParameterValue") // kept parameterised
  private static Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> leftT(
      String code) {
    return EITHER_T.widen(EitherT.left(OUTER, new TestError(code)));
  }

  private static Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> emptyT() {
    Kind<OptionalKind.Witness, Either<TestError, Integer>> emptyOuter =
        OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  /** {@code Right(0/42/-1)}, a {@code Left}, and an empty outer {@code Optional}. */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("rightT(0)", rightT(0)),
        Arguments.of("rightT(42)", rightT(42)),
        Arguments.of("rightT(-1)", rightT(-1)),
        Arguments.of("leftT(E1)", leftT("E1")),
        Arguments.of("emptyT", emptyT()));
  }

  /** Scalar law values {@code {0, 42, -1}}. */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of(0), Arguments.of(42), Arguments.of(-1));
  }

  /** Scalar law strings {@code {"a", "hello"}}. */
  static Stream<Arguments> strings() {
    return Stream.of(Arguments.of("a"), Arguments.of("hello"));
  }
}
