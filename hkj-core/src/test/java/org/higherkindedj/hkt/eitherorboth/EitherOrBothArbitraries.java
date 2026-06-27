// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the {@link EitherOrBoth} property tests. Generators span all three
 * cases ({@code Left}, {@code Right}, {@code Both}) and the kleisli pools also return each case, so
 * the Monad laws are exercised across the accumulating {@code Both} combinations.
 */
final class EitherOrBothArbitraries {

  private EitherOrBothArbitraries() {}

  private static final Arbitrary<Integer> INTS = Arbitraries.integers().between(-100, 100);
  private static final Arbitrary<String> WARNINGS = Arbitraries.of("w1", "w2", "warn");

  /** {@code EitherOrBoth<String, Integer>} kinds across {@code Left}/{@code Right}/{@code Both}. */
  static Arbitrary<Kind<EitherOrBothKind.Witness<String>, Integer>> eobKinds() {
    Arbitrary<Kind<EitherOrBothKind.Witness<String>, Integer>> rights =
        INTS.map(i -> EITHER_OR_BOTH.widen(EitherOrBoth.right(i)));
    Arbitrary<Kind<EitherOrBothKind.Witness<String>, Integer>> lefts =
        WARNINGS.map(w -> EITHER_OR_BOTH.widen(EitherOrBoth.<String, Integer>left(w)));
    Arbitrary<Kind<EitherOrBothKind.Witness<String>, Integer>> boths =
        Combinators.combine(WARNINGS, INTS)
            .as((w, i) -> EITHER_OR_BOTH.widen(EitherOrBoth.both(w, i)));
    return Arbitraries.oneOf(rights, lefts, boths);
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  /** A pool of {@code Integer -> EitherOrBoth<String, String>} kleisli arrows over all cases. */
  static Arbitrary<Function<Integer, Kind<EitherOrBothKind.Witness<String>, String>>>
      intToEobString() {
    return Arbitraries.of(
        i -> EITHER_OR_BOTH.widen(EitherOrBoth.right("a:" + i)),
        i -> EITHER_OR_BOTH.widen(EitherOrBoth.left("L:" + i)),
        i -> EITHER_OR_BOTH.widen(EitherOrBoth.both("w:" + i, "b:" + i)));
  }

  /** A pool of {@code String -> EitherOrBoth<String, String>} kleisli arrows over all cases. */
  static Arbitrary<Function<String, Kind<EitherOrBothKind.Witness<String>, String>>>
      stringToEobString() {
    return Arbitraries.of(
        s -> EITHER_OR_BOTH.widen(EitherOrBoth.right(s.toUpperCase())),
        s -> EITHER_OR_BOTH.widen(EitherOrBoth.left("E:" + s)),
        s -> EITHER_OR_BOTH.widen(EitherOrBoth.both("x:" + s, s + "!")));
  }
}
