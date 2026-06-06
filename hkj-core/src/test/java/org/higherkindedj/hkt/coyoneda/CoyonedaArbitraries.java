// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Shared jqwik arbitraries for the Coyoneda property test (underlying functor = {@code Maybe}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Coyoneda} generator and the
 * function pools are defined once.
 */
final class CoyonedaArbitraries {

  private CoyonedaArbitraries() {}

  /** {@code lift(just)} over ints in [-100,100], with ~15% {@code lift(nothing())}. */
  static Arbitrary<Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer>> coyonedaKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .map(i -> i == null ? COYONEDA.lift(MAYBE.nothing()) : COYONEDA.lift(MAYBE.just(i)));
  }

  /** A small pool of total {@code Integer -> String} functions. */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  /** A small pool of total {@code String -> Integer} functions. */
  static Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }
}
