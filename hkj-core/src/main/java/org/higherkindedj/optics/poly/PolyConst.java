// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.poly;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.NullMarked;

/**
 * Package-private {@code Const} functor used by {@link PolyOptics#get} to extract the focused part
 * of a lens-shaped polymorphic optic without performing a modification.
 *
 * <p>The Const functor wraps a value of type {@code C} and ignores its phantom parameter. {@code
 * map} preserves the wrapped value (since the function is never applied), which is exactly the
 * behaviour we need to surface the captured focus through an optic's {@code modifyF}.
 *
 * <p>{@code ap} is intentionally unsupported; this Const is only used for lens / iso shaped
 * polymorphic optics whose {@code modifyF} call uses {@code map} (and never {@code ap} or {@code
 * of}). If a caller passes an optic whose shape requires {@code ap} or {@code of} (for example, a
 * prism that fails to match), a clear exception is raised pointing them at {@link
 * PolyOptics#modifyF}.
 *
 * @param <C> The type of the captured value.
 * @param <A> The phantom type parameter (ignored).
 */
@NullMarked
record PolyConst<C, A>(C value) implements Kind<PolyConst.Witness<C>, A> {

  /** Witness type for the {@link PolyConst} functor. */
  static final class Witness<C> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
  }

  @SuppressWarnings("unchecked")
  static <C, A> PolyConst<C, A> narrow(Kind<Witness<C>, A> kind) {
    return (PolyConst<C, A>) Objects.requireNonNull(kind, "kind");
  }

  /**
   * Returns an {@link Applicative} for {@code PolyConst} suitable for capturing the focus of a
   * lens-shaped polymorphic optic.
   *
   * <p>{@code map} returns a Const containing the previously captured value; {@code of} and {@code
   * ap} throw, because they imply an optic shape (prism / traversal) that {@link PolyOptics#get}
   * does not support.
   */
  static <C> Applicative<Witness<C>> applicative() {
    return new Applicative<>() {
      @Override
      public <A> Kind<Witness<C>, A> of(A value) {
        throw new UnsupportedOperationException(
            "PolyOptics.get is lens-like only. The optic invoked Applicative.of, "
                + "which means it has prism / traversal shape (it can fail to focus). "
                + "Use PolyOptics.modifyF with an appropriate Applicative instead.");
      }

      @Override
      public <A, B> Kind<Witness<C>, B> map(
          Function<? super A, ? extends B> f, Kind<Witness<C>, A> fa) {
        return new PolyConst<>(narrow(fa).value());
      }

      @Override
      public <A, B> Kind<Witness<C>, B> ap(
          Kind<Witness<C>, ? extends Function<A, B>> ff, Kind<Witness<C>, A> fa) {
        throw new UnsupportedOperationException(
            "PolyOptics.get is lens-like only. The optic invoked Applicative.ap, "
                + "which means it has traversal shape (it focuses on multiple parts). "
                + "Use PolyOptics.modifyF with an appropriate Applicative instead.");
      }
    };
  }
}
