// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the Either property tests.
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Either<String, Integer>}
 * generator and the integer-to-string function pool are defined once rather than copy-pasted across
 * {@code EitherFunctorPropertyTest}, {@code EitherMonadPropertyTest} and {@code
 * EitherSelectivePropertyTest}.
 */
final class EitherArbitraries {

  private EitherArbitraries() {}

  /**
   * {@code Either<String, Integer>} kinds: mostly {@code Right(i)}, with ~15% {@code Left} (from
   * injected nulls) and every multiple of five mapped to a {@code Left} error, so both inhabitants
   * are exercised.
   *
   * @param bound the (inclusive) magnitude of the generated integers
   */
  static Arbitrary<Kind<EitherKind.Witness<String>, Integer>> eitherKinds(int bound) {
    return Arbitraries.integers()
        .between(-bound, bound)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(EITHER.widen(Either.left("null value")));
              }
              if (i % 5 == 0) {
                return Arbitraries.of("error: a", "error: b", "error: c")
                    .map(s -> EITHER.widen(Either.left(s)));
              }
              return Arbitraries.just(EITHER.widen(Either.right(i)));
            });
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }
}
