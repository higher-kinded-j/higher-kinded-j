// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flat law-verification helpers for {@link Functor} instances.
 *
 * <p>Each helper checks one law for a single fixture. Drive coverage with
 * {@code @ParameterizedTest @MethodSource} or jqwik {@code @Property @ForAll} over a fixture stream
 * rather than enumerating values inside the law method.
 *
 * <p>Library users can call these against their own {@code Functor} instances to verify the laws
 * hold on their custom type-class implementations.
 */
public final class FunctorLaws {

  private FunctorLaws() {}

  /** Identity: {@code map(id, fa) == fa}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A> void assertIdentity(
      Functor<F> functor, Kind<F, A> fa, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, A> mapped = functor.map(Function.identity(), fa);
    assertThat(eq.test(mapped, fa)).as("Functor identity: map(id, fa) == fa").isTrue();
  }

  /** Composition: {@code map(g∘f, fa) == map(g, map(f, fa))}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B, C> void assertComposition(
      Functor<F> functor,
      Kind<F, A> fa,
      Function<A, B> f,
      Function<B, C> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, C> lhs = functor.map(f.andThen(g), fa);
    Kind<F, C> rhs = functor.map(g, functor.map(f, fa));
    assertThat(eq.test(lhs, rhs))
        .as("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
        .isTrue();
  }
}
