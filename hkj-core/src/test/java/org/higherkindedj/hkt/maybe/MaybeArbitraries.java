// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Shared jqwik arbitraries for the Maybe property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Maybe<Integer>} generator
 * and the integer-to-string function pool are defined once rather than copy-pasted across {@code
 * MaybeFunctorPropertyTest}, {@code MaybeMonadPropertyTest} and {@code MaybeSelectivePropertyTest}.
 */
final class MaybeArbitraries {

  private MaybeArbitraries() {}

  /**
   * {@code Maybe<Integer>} kinds: mostly {@code Just(i)}, with injected nulls collapsed to {@code
   * Nothing}, so both inhabitants are exercised.
   *
   * @param bound the (inclusive) magnitude of the generated integers
   * @param nullFraction the proportion of generated values injected as {@code Nothing}
   */
  static Arbitrary<Kind<MaybeKind.Witness, Integer>> maybeKinds(int bound, double nullFraction) {
    return Arbitraries.integers()
        .between(-bound, bound)
        .injectNull(nullFraction)
        .map(MaybeArbitraries::toMaybeKind);
  }

  /** Widens a possibly-{@code null} integer, with {@code null} collapsing to {@code Nothing}. */
  private static Kind<MaybeKind.Witness, Integer> toMaybeKind(@Nullable Integer i) {
    return MAYBE.widen(i == null ? Maybe.nothing() : Maybe.just(i));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }
}
