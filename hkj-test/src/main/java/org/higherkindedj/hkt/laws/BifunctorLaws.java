// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.laws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flat law-verification helpers for {@link Bifunctor} instances.
 *
 * <p>Each helper checks one law for a single fixture. Drive coverage with
 * {@code @ParameterizedTest @MethodSource} or jqwik {@code @Property @ForAll} over a fixture stream
 * rather than enumerating values inside the law method.
 *
 * <p>Library users can call these against their own {@code Bifunctor} instances to verify the laws
 * hold on their custom type-class implementations. The full set checked here is identity,
 * composition, and the {@code first}/{@code second} consistency laws (which pin the convenience
 * single-side maps to {@code bimap}).
 *
 * <p>Equality is supplied as a {@code BiPredicate} over {@code Kind2<F, ?, ?>} (e.g. {@code (a, b)
 * -> HELPER.narrow2(a).equals(HELPER.narrow2(b))}) so the laws can be checked structurally rather
 * than by reference.
 */
public final class BifunctorLaws {

  private BifunctorLaws() {}

  /** Identity: {@code bimap(id, id, fab) == fab}. */
  public static <F extends WitnessArity<TypeArity.Binary>, A, B> void assertIdentity(
      Bifunctor<F> bifunctor, Kind2<F, A, B> fab, BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq) {
    Function<A, A> idA = a -> a;
    Function<B, B> idB = b -> b;
    Kind2<F, A, B> bimapped = bifunctor.bimap(idA, idB, fab);
    assertThat(eq.test(bimapped, fab)).as("Bifunctor identity: bimap(id, id, fab) == fab").isTrue();
  }

  /** Composition: {@code bimap(f2∘f1, g2∘g1, fab) == bimap(f2, g2, bimap(f1, g1, fab))}. */
  public static <F extends WitnessArity<TypeArity.Binary>, A, B, C, D, E> void assertComposition(
      Bifunctor<F> bifunctor,
      Kind2<F, A, B> fab,
      Function<A, C> f1,
      Function<C, E> f2,
      Function<B, D> g1,
      Function<D, E> g2,
      BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq) {
    Kind2<F, E, E> lhs = bifunctor.bimap(f1.andThen(f2), g1.andThen(g2), fab);
    Kind2<F, E, E> rhs = bifunctor.bimap(f2, g2, bifunctor.bimap(f1, g1, fab));
    assertThat(eq.test(lhs, rhs))
        .as("Bifunctor composition: bimap(f2∘f1, g2∘g1, fab) == bimap(f2, g2, bimap(f1, g1, fab))")
        .isTrue();
  }

  /** First-map consistency: {@code first(f, fab) == bimap(f, id, fab)}. */
  public static <F extends WitnessArity<TypeArity.Binary>, A, B, C> void assertFirstConsistency(
      Bifunctor<F> bifunctor,
      Kind2<F, A, B> fab,
      Function<A, C> f,
      BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq) {
    Function<B, B> idB = b -> b;
    Kind2<F, C, B> viaFirst = bifunctor.first(f, fab);
    Kind2<F, C, B> viaBimap = bifunctor.bimap(f, idB, fab);
    assertThat(eq.test(viaFirst, viaBimap))
        .as("Bifunctor first-consistency: first(f, fab) == bimap(f, id, fab)")
        .isTrue();
  }

  /** Second-map consistency: {@code second(g, fab) == bimap(id, g, fab)}. */
  public static <F extends WitnessArity<TypeArity.Binary>, A, B, D> void assertSecondConsistency(
      Bifunctor<F> bifunctor,
      Kind2<F, A, B> fab,
      Function<B, D> g,
      BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq) {
    Function<A, A> idA = a -> a;
    Kind2<F, A, D> viaSecond = bifunctor.second(g, fab);
    Kind2<F, A, D> viaBimap = bifunctor.bimap(idA, g, fab);
    assertThat(eq.test(viaSecond, viaBimap))
        .as("Bifunctor second-consistency: second(g, fab) == bimap(id, g, fab)")
        .isTrue();
  }
}
