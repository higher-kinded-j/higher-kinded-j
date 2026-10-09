// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A minimal, package-private Const functor used internally by {@link
 * IndexedTraversal#asIndexedFold()} to fold an {@code imodifyF} traversal without rebuilding the
 * structure.
 *
 * <p>It is the indexed package's copy of {@code org.higherkindedj.optics.ConstForFold}, which is
 * private to its own package. The Const functor wraps a monoidal value and ignores its type
 * parameter, so {@code map} never runs the function that would rebuild the source.
 *
 * @param <M> The type of the accumulated monoidal value.
 * @param <A> The phantom type parameter (ignored).
 */
@NullMarked
record ConstForIndexedFold<M, A extends @Nullable Object>(M value)
    implements Kind<ConstForIndexedFold.Witness<M>, A> {

  /** Witness type for the Const functor, parameterised by the monoid type. */
  static final class Witness<M> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }

  @SuppressWarnings(
      "unchecked") // every Kind<Witness<M>, A> is a ConstForIndexedFold by construction
  static <M, A extends @Nullable Object> ConstForIndexedFold<M, A> narrow(
      Kind<Witness<M>, A> kind) {
    return (ConstForIndexedFold<M, A>) Objects.requireNonNull(kind);
  }

  /**
   * Creates an {@link Applicative} for the Const functor backed by the given {@link Monoid}.
   *
   * <p>The applicative's {@code of} returns the monoid's empty value, {@code map} preserves the
   * accumulated value (ignoring the function), and {@code ap} combines accumulated values using the
   * monoid.
   *
   * @param monoid The monoid used to combine accumulated values.
   * @param <M> The type of the accumulated monoidal value.
   * @return An Applicative instance for {@code ConstForIndexedFold.Witness<M>}.
   */
  static <M> Applicative<Witness<M>> applicative(Monoid<M> monoid) {
    return new Applicative<>() {
      @Override
      public <A> Kind<Witness<M>, A> of(A value) {
        return new ConstForIndexedFold<>(monoid.empty());
      }

      @Override
      public <A, B> Kind<Witness<M>, B> map(
          Function<? super A, ? extends B> f, Kind<Witness<M>, A> fa) {
        return new ConstForIndexedFold<>(narrow(fa).value());
      }

      @Override
      public <A, B> Kind<Witness<M>, B> ap(
          Kind<Witness<M>, ? extends Function<A, B>> ff, Kind<Witness<M>, A> fa) {
        return new ConstForIndexedFold<>(monoid.combine(narrow(ff).value(), narrow(fa).value()));
      }
    };
  }
}
