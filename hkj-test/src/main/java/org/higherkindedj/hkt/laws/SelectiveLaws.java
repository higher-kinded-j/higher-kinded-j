// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flat law-verification helpers for {@link Selective} instances.
 *
 * <p>Verifies the two pure-input semantics laws that pin down {@code select}'s behaviour:
 *
 * <ul>
 *   <li><b>Left-pure</b>: {@code select(of(Left(a)), ff) ≡ ap(ff, of(a))} — a left choice forces
 *       the function to be applied
 *   <li><b>Right-pure</b>: {@code select(of(Right(b)), ff) ≡ of(b)} — a right choice short-circuits
 *       and ignores the function
 * </ul>
 *
 * <p>The function argument {@code ff} is taken as a {@code Kind<F, Function<A, B>>} rather than a
 * bare {@code Function<A, B>} so the laws can be verified against effectful function values, not
 * just pure-lifted ones. Callers passing a pure function should wrap it via {@code sel.of(f)} at
 * the call site.
 *
 * <p>The full free-applicative-based selective laws (associativity, distributivity) are not checked
 * here — they require infrastructure for free functors that is out of scope.
 */
public final class SelectiveLaws {

  private SelectiveLaws() {}

  /** Left-pure: {@code select(of(Left(a)), ff) == ap(ff, of(a))}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertLeftPure(
      Selective<F> sel,
      A leftValue,
      Kind<F, Function<A, B>> ff,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, Choice<A, B>> ofLeft = sel.of(Selective.left(leftValue));
    Kind<F, B> lhs = sel.select(ofLeft, ff);
    Kind<F, B> rhs = sel.ap(ff, sel.of(leftValue));
    assertThat(eq.test(lhs, rhs))
        .as("Selective left-pure: select(of(Left(a)), ff) == ap(ff, of(a))")
        .isTrue();
  }

  /** Right-pure: {@code select(of(Right(b)), ff) == of(b)}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertRightPure(
      Selective<F> sel,
      B rightValue,
      Kind<F, Function<A, B>> ff,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, Choice<A, B>> ofRight = sel.of(Selective.right(rightValue));
    Kind<F, B> lhs = sel.select(ofRight, ff);
    Kind<F, B> rhs = sel.of(rightValue);
    assertThat(eq.test(lhs, rhs))
        .as("Selective right-pure: select(of(Right(b)), ff) == of(b)")
        .isTrue();
  }
}
