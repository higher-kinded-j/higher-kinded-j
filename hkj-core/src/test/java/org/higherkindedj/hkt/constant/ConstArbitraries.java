// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.higherkindedj.hkt.Kind;

/**
 * Shared jqwik arbitraries for the {@code Const} property test (constant type = {@code Integer},
 * accumulated via the {@code sum} monoid).
 *
 * <p>The per-test {@code @Provide} methods delegate here so the {@code Const} generator and the
 * function pools are defined once.
 */
final class ConstArbitraries {

  private ConstArbitraries() {}

  /** {@code Const(i)} over ints in {@code [-100, 100]}; the phantom type is {@code Integer}. */
  static Arbitrary<Kind<ConstKind.Witness<Integer>, Integer>> constKinds() {
    return Arbitraries.integers().between(-100, 100).map(i -> CONST.widen(new Const<>(i)));
  }

  /** A small pool of total {@code Integer -> String} functions (phantom — never applied). */
  static Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }
}
