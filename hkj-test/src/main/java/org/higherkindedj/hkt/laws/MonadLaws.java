// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flat law-verification helpers for {@link Monad} instances.
 *
 * <p>Each helper checks one law for a single fixture. Drive coverage with
 * {@code @ParameterizedTest @MethodSource} or jqwik {@code @Property @ForAll} over a fixture stream
 * rather than enumerating values inside the law method.
 *
 * <p>Library users can call these against their own {@code Monad} instances to verify the laws hold
 * on their custom type-class implementations.
 */
public final class MonadLaws {

  private MonadLaws() {}

  /** Left identity: {@code flatMap(f, of(a)) == f(a)}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B> void assertLeftIdentity(
      Monad<F> monad, A value, Function<A, Kind<F, B>> f, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, B> lhs = monad.flatMap(f, monad.of(value));
    Kind<F, B> rhs = f.apply(value);
    assertThat(eq.test(lhs, rhs)).as("Monad left identity: flatMap(f, of(a)) == f(a)").isTrue();
  }

  /** Right identity: {@code flatMap(of, m) == m}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A> void assertRightIdentity(
      Monad<F> monad, Kind<F, A> ma, BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, A> result = monad.flatMap(monad::of, ma);
    assertThat(eq.test(result, ma)).as("Monad right identity: flatMap(of, m) == m").isTrue();
  }

  /** Associativity: {@code flatMap(g, flatMap(f, m)) == flatMap(a -> flatMap(g, f(a)), m)}. */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B, C> void assertAssociativity(
      Monad<F> monad,
      Kind<F, A> ma,
      Function<A, Kind<F, B>> f,
      Function<B, Kind<F, C>> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
    Kind<F, C> lhs = monad.flatMap(g, monad.flatMap(f, ma));
    Kind<F, C> rhs = monad.flatMap(a -> monad.flatMap(g, f.apply(a)), ma);
    assertThat(eq.test(lhs, rhs))
        .as("Monad associativity: flatMap(g, flatMap(f, m)) == flatMap(a -> flatMap(g, f(a)), m)")
        .isTrue();
  }
}
