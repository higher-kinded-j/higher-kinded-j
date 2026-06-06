// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherTMonadTest.TestError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Shared jqwik arbitraries for the EitherT property tests (inner monad = {@code Optional}, Left =
 * {@link TestError}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code EitherT} generator and the
 * function pools are defined once rather than copy-pasted into {@code EitherTMonadPropertyTest}.
 * The {@link TestError} Left type is shared with {@link EitherTMonadTest} so the produced kinds
 * match its witness exactly.
 */
final class EitherTArbitraries {

  private EitherTArbitraries() {}

  private static final MonadError<OptionalKind.Witness, Unit> OUTER =
      Instances.monadError(optional());

  private static <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> rightT(R value) {
    return EITHER_T.widen(EitherT.right(OUTER, value));
  }

  private static <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> leftT(
      String code) {
    return EITHER_T.widen(EitherT.left(OUTER, new TestError(code)));
  }

  private static <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> emptyT() {
    Kind<OptionalKind.Witness, Either<TestError, R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  /** Mix of Right (success), Left (in-Either error), and empty-outer Optional states. */
  static Arbitrary<Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>>
      eitherTKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(emptyT());
              }
              if (i % 5 == 0) {
                return Arbitraries.of("err-a", "err-b", "err-c").map(EitherTArbitraries::leftT);
              }
              return Arbitraries.just(rightT(i));
            });
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A small pool of {@code Integer -> EitherT<Optional, TestError, String>} kleisli arrows. */
  static Arbitrary<
          Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? rightT("even:" + i) : leftT("odd-" + i),
        i -> i > 0 ? rightT("positive:" + i) : emptyT(),
        i -> rightT("value:" + i),
        i -> i == 0 ? leftT("zero") : rightT(String.valueOf(i)));
  }

  /** A small pool of {@code String -> EitherT<Optional, TestError, String>} kleisli arrows. */
  static Arbitrary<
          Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> s.isEmpty() ? leftT("empty") : rightT(s.toUpperCase()),
        s -> s.length() > 3 ? rightT("long:" + s) : emptyT(),
        s -> rightT("transformed:" + s));
  }
}
