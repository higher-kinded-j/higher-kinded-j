// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Shared law fixtures for the {@code Const} type-class tests (Applicative over a {@code sum} monoid
 * and Bifunctor).
 *
 * <p>Referenced from the per-type-class {@code Laws} blocks via a fully-qualified
 * {@code @MethodSource} and reused by the property test. {@code Const<C, A>} is a phantom
 * applicative — the phantom {@code A} is ignored and the held monoidal value {@code C} carries all
 * the meaning — so both equalities below compare by unwrapping and comparing the constant value.
 */
@SuppressWarnings("unused") // referenced reflectively via @MethodSource
final class ConstLawFixtures {

  private ConstLawFixtures() {}

  /** Two partially-applied {@code Const} kinds are equal iff their accumulated values are equal. */
  static final BiPredicate<Kind<ConstKind.Witness<Integer>, ?>, Kind<ConstKind.Witness<Integer>, ?>>
      EQ = (k1, k2) -> Objects.equals(CONST.narrow(k1).value(), CONST.narrow(k2).value());

  /** Two {@code Const} {@code Kind2}s are equal iff their underlying {@code Const}s are equal. */
  static final BiPredicate<Kind2<ConstKind2.Witness, ?, ?>, Kind2<ConstKind2.Witness, ?, ?>>
      BIFUNCTOR_EQ = (k1, k2) -> CONST.narrow2(k1).equals(CONST.narrow2(k2));

  /**
   * Partially-applied applicative fixtures: {@code Const(0)}, {@code Const(42)}, {@code Const(-1)}.
   */
  static Stream<Arguments> kinds() {
    return Stream.of(
        Arguments.of("Const(0)", CONST.widen(new Const<Integer, Integer>(0))),
        Arguments.of("Const(42)", CONST.widen(new Const<Integer, Integer>(42))),
        Arguments.of("Const(-1)", CONST.widen(new Const<Integer, Integer>(-1))));
  }

  /**
   * Scalar law values for the applicative homomorphism/interchange laws (phantom inputs to {@code
   * of}). Typed as {@code String} to line up with the shared {@code validMapper} ({@code String ->
   * Integer}); the value is never applied since {@code Const} is phantom.
   */
  static Stream<Arguments> values() {
    return Stream.of(Arguments.of("a"), Arguments.of("test"), Arguments.of(""));
  }

  /** Bifunctor {@code Kind2} fixtures spanning a few constant values. */
  static Stream<Arguments> kind2s() {
    return Stream.of(
        Arguments.of("Const(\"hello\")", CONST.widen2(new Const<String, Integer>("hello"))),
        Arguments.of("Const(\"\")", CONST.widen2(new Const<String, Integer>(""))),
        Arguments.of("Const(\"world\")", CONST.widen2(new Const<String, Integer>("world"))));
  }
}
