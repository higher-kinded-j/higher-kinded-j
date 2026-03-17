// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * A minimal, package-private Const functor implementation used internally by {@link
 * Traversal#asFold()} to convert a Traversal into a Fold.
 *
 * <p>The Const functor wraps a monoidal value and ignores any type parameter {@code A}. This
 * enables extracting accumulated values from a {@code modifyF} traversal without actually modifying
 * the structure.
 *
 * @param <M> The type of the accumulated monoidal value.
 * @param <A> The phantom type parameter (ignored).
 */
@NullMarked
record ConstForFold<M, A>(M value) implements Kind<ConstForFold.Witness<M>, A> {

  /** Witness type for the Const functor, parameterized by the monoid type. */
  static final class Witness<M> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }

  @SuppressWarnings("unchecked")
  static <M, A> ConstForFold<M, A> narrow(Kind<Witness<M>, A> kind) {
    return (ConstForFold<M, A>) Objects.requireNonNull(kind);
  }

  /**
   * Creates an {@link Applicative} for the Const functor backed by the given {@link Monoid}.
   *
   * <p>The applicative's {@code of} returns the monoid's empty value, {@code map} preserves the
   * accumulated value (ignoring the function), and {@code ap} combines accumulated values using the
   * monoid. The default {@code map2} delegates to {@code map} and {@code ap}.
   *
   * @param monoid The monoid used to combine accumulated values.
   * @param <M> The type of the accumulated monoidal value.
   * @return An Applicative instance for {@code ConstForFold.Witness<M>}.
   */
  static <M> Applicative<Witness<M>> applicative(Monoid<M> monoid) {
    return new Applicative<>() {
      @Override
      public <A> Kind<Witness<M>, A> of(A value) {
        return new ConstForFold<>(monoid.empty());
      }

      @Override
      public <A, B> Kind<Witness<M>, B> map(
          Function<? super A, ? extends B> f, Kind<Witness<M>, A> fa) {
        return new ConstForFold<>(narrow(fa).value());
      }

      @Override
      public <A, B> Kind<Witness<M>, B> ap(
          Kind<Witness<M>, ? extends Function<A, B>> ff, Kind<Witness<M>, A> fa) {
        return new ConstForFold<>(monoid.combine(narrow(ff).value(), narrow(fa).value()));
      }
    };
  }
}
