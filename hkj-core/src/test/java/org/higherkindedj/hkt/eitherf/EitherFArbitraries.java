// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherf;

import static org.higherkindedj.hkt.eitherf.EitherFKindHelper.EITHERF;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.Identity;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Shared jqwik arbitraries for the EitherF property test (left algebra = {@code Identity}, right
 * algebra = {@code Maybe}).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code EitherF} generator and the
 * function pools are defined once.
 */
final class EitherFArbitraries {

  private EitherFArbitraries() {}

  /**
   * {@code Left(Identity)} for even values, {@code Right(Just)} for odd, over ints in [-100,100].
   */
  static Arbitrary<Kind<EitherFKind.Witness<IdentityKind.Witness, MaybeKind.Witness>, Integer>>
      eitherFKinds() {
    return Arbitraries.integers()
        .between(-100, 100)
        .map(
            i ->
                i % 2 == 0
                    ? EITHERF.widen(
                        EitherF.left(IdentityKindHelper.IDENTITY.widen(new Identity<>(i))))
                    : EITHERF.widen(EitherF.right(MAYBE.widen(Maybe.just(i)))));
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
