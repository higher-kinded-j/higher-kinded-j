// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.indexed;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * A minimal, package-private identity functor used internally by {@link
 * IndexedTraversal#asIndexedFold()} to run an {@code imodifyF} traversal whose only purpose is to
 * collect index/value pairs.
 *
 * <p>It exists because hkj-api cannot depend on hkj-core's {@code Id}. Unlike the earlier raw
 * {@code Kind} wrapper, this models the witness explicitly, so the value field is typed and only
 * the single, fundamental {@link #narrow} cast remains.
 *
 * @param <A> the wrapped value type
 */
@NullMarked
record IdBox<A>(A value) implements Kind<IdBox.Witness, A> {

  /** Witness type for the identity functor. */
  static final class Witness implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }

  @SuppressWarnings("unchecked") // every Kind<Witness, A> is an IdBox<A> by construction
  static <A> IdBox<A> narrow(Kind<Witness, A> kind) {
    return (IdBox<A>) Objects.requireNonNull(kind);
  }

  /**
   * Creates the identity {@link Applicative}: {@code of} wraps the value, {@code map} applies the
   * function, and {@code ap} applies the wrapped function to the wrapped value.
   *
   * @return an Applicative instance for {@code IdBox.Witness}.
   */
  static Applicative<Witness> applicative() {
    return new Applicative<>() {
      @Override
      public <A> Kind<Witness, A> of(A value) {
        return new IdBox<>(value);
      }

      @Override
      public <A, B> Kind<Witness, B> map(Function<? super A, ? extends B> f, Kind<Witness, A> fa) {
        return new IdBox<>(f.apply(narrow(fa).value()));
      }

      @Override
      public <A, B> Kind<Witness, B> ap(
          Kind<Witness, ? extends Function<A, B>> ff, Kind<Witness, A> fa) {
        return new IdBox<>(narrow(ff).value().apply(narrow(fa).value()));
      }
    };
  }
}
