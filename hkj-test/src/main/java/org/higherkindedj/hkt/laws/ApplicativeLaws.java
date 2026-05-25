// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flat law-verification helpers for {@link Applicative} instances.
 *
 * <p>Each helper checks one of the four classic applicative laws for a single fixture. Drive
 * coverage with {@code @ParameterizedTest @MethodSource} or jqwik {@code @Property @ForAll} over a
 * fixture stream rather than enumerating values inside the law method.
 */
public final class ApplicativeLaws {

  private ApplicativeLaws() {}

  /** Identity: {@code ap(of(id), v) == v}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A> void assertIdentity(
      Applicative<F> ap, Kind<F, A> v, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, Function<A, A>> ofId = ap.of(Function.identity());
    Kind<F, A> result = ap.ap(ofId, v);
    assertThat(eq.test(result, v)).as("Applicative identity: ap(of(id), v) == v").isTrue();
  }

  /** Homomorphism: {@code ap(of(f), of(x)) == of(f(x))}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertHomomorphism(
      Applicative<F> ap, A x, Function<A, B> f, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, B> lhs = ap.ap(ap.of(f), ap.of(x));
    Kind<F, B> rhs = ap.of(f.apply(x));
    assertThat(eq.test(lhs, rhs))
        .as("Applicative homomorphism: ap(of(f), of(x)) == of(f(x))")
        .isTrue();
  }

  /** Interchange: {@code ap(u, of(y)) == ap(of(f -> f(y)), u)} for {@code u : F<A -> B>}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertInterchange(
      Applicative<F> ap, Kind<F, Function<A, B>> u, A y, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, B> lhs = ap.ap(u, ap.of(y));
    Function<Function<A, B>, B> apply = f -> f.apply(y);
    Kind<F, B> rhs = ap.ap(ap.of(apply), u);
    assertThat(eq.test(lhs, rhs))
        .as("Applicative interchange: ap(u, of(y)) == ap(of(f -> f(y)), u)")
        .isTrue();
  }

  /**
   * Composition: {@code ap(ap(ap(of(compose), u), v), w) == ap(u, ap(v, w))} where {@code compose}
   * is {@code (f, g) -> x -> f(g(x))} (curried as {@code f -> g -> x -> f(g(x))}).
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B, C> void assertComposition(
      Applicative<F> ap,
      Kind<F, Function<B, C>> u,
      Kind<F, Function<A, B>> v,
      Kind<F, A> w,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Function<Function<B, C>, Function<Function<A, B>, Function<A, C>>> compose =
        f -> g -> x -> f.apply(g.apply(x));
    Kind<F, Function<Function<A, B>, Function<A, C>>> step1 = ap.ap(ap.of(compose), u);
    Kind<F, Function<A, C>> step2 = ap.ap(step1, v);
    Kind<F, C> lhs = ap.ap(step2, w);
    Kind<F, C> rhs = ap.ap(u, ap.ap(v, w));
    assertThat(eq.test(lhs, rhs)).as("Applicative composition").isTrue();
  }
}
