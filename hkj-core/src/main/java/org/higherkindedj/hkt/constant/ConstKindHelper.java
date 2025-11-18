// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum providing widen/narrow operations for {@link Const} types with the Kind and Kind2 systems.
 *
 * <p>Provides both:
 *
 * <ul>
 *   <li>{@code widen2/narrow2} for bifunctor operations ({@code Kind2<ConstKind2.Witness, C, A>})
 *   <li>{@code widen/narrow} for partially-applied applicative operations ({@code
 *       Kind<ConstKind.Witness<M>, A>})
 * </ul>
 *
 * <p>Access these operations via the singleton {@code CONST}. For example: {@code
 * ConstKindHelper.CONST.widen2(new Const<>("hello"));} Or, with static import: {@code import static
 * org.higherkindedj.hkt.constant.ConstKindHelper.CONST; CONST.widen2(...);}
 */
public enum ConstKindHelper {
  CONST;

  private static final Class<Const> CONST_CLASS = Const.class;

  /**
   * Internal record implementing {@link ConstKind2} to hold the concrete {@link Const} instance for
   * bifunctor operations.
   *
   * @param <C> The type of the constant value.
   * @param <A> The phantom type parameter.
   * @param const_ The non-null, actual {@link Const} instance.
   */
  record ConstKind2Holder<C, A>(Const<C, A> const_) implements ConstKind2<C, A> {

    public ConstKind2Holder {
      Validation.kind().requireForWiden(const_, CONST_CLASS);
    }
  }

  /**
   * Internal record implementing {@link ConstKind} to hold the concrete {@link Const} instance for
   * partially-applied applicative operations.
   *
   * @param <M> The fixed constant value type.
   * @param <A> The phantom type parameter.
   * @param const_ The non-null, actual {@link Const} instance.
   */
  record ConstKindHolder<M, A>(Const<M, A> const_) implements ConstKind<M, A> {

    public ConstKindHolder {
      Validation.kind().requireForWiden(const_, CONST_CLASS);
    }
  }

  /**
   * Widens a concrete {@code Const<C, A>} instance into its Kind2 representation, {@code
   * Kind2<ConstKind2.Witness, C, A>}.
   *
   * @param <C> The type of the constant value of the {@code Const}.
   * @param <A> The phantom type parameter of the {@code Const}.
   * @param const_ The concrete {@code Const<C, A>} instance to widen. Must not be null.
   * @return A {@code Kind2<ConstKind2.Witness, C, A>} representing the wrapped {@code Const}. Never
   *     null.
   * @throws NullPointerException if {@code const_} is {@code null}.
   */
  public <C, A> Kind2<ConstKind2.Witness, C, A> widen2(Const<C, A> const_) {
    return new ConstKind2Holder<>(const_);
  }

  /**
   * Narrows a {@code Kind2<ConstKind2.Witness, C, A>} back to its concrete {@code Const<C, A>}
   * type.
   *
   * @param <C> The type of the constant value of the target {@code Const}.
   * @param <A> The phantom type parameter of the target {@code Const}.
   * @param kind The {@code Kind2<ConstKind2.Witness, C, A>} instance to narrow. May be {@code
   *     null}.
   * @return The underlying {@code Const<C, A>} instance. Never null.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *     of a {@code Const<C,A>}.
   */
  @SuppressWarnings("unchecked")
  public <C, A> Const<C, A> narrow2(@Nullable Kind2<ConstKind2.Witness, C, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException("Cannot narrow null Kind2 for Const");
    }
    if (!(kind instanceof ConstKind2Holder<?, ?>)) {
      throw new KindUnwrapException(
          "Kind2 instance cannot be narrowed to Const (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    // Safe cast due to type erasure and holder validation
    return ((ConstKind2Holder<C, A>) kind).const_();
  }

  /**
   * Widens a concrete {@code Const<M, A>} instance into its partially-applied Kind representation,
   * {@code Kind<ConstKind.Witness<M>, A>}.
   *
   * <p>This is used for applicative operations where the first type parameter {@code M} is fixed.
   *
   * @param <M> The fixed type of the constant value.
   * @param <A> The phantom type parameter of the {@code Const}.
   * @param const_ The concrete {@code Const<M, A>} instance to widen. Must not be null.
   * @return A {@code Kind<ConstKind.Witness<M>, A>} representing the wrapped {@code Const}. Never
   *     null.
   * @throws NullPointerException if {@code const_} is {@code null}.
   */
  public <M, A> Kind<ConstKind.Witness<M>, A> widen(Const<M, A> const_) {
    return new ConstKindHolder<>(const_);
  }

  /**
   * Narrows a {@code Kind<ConstKind.Witness<M>, A>} back to its concrete {@code Const<M, A>} type.
   *
   * @param <M> The fixed type of the constant value.
   * @param <A> The phantom type parameter of the target {@code Const}.
   * @param kind The {@code Kind<ConstKind.Witness<M>, A>} instance to narrow. May be {@code null}.
   * @return The underlying {@code Const<M, A>} instance. Never null.
   * @throws KindUnwrapException if the input {@code kind} is {@code null} or not a representation
   *     of a {@code Const<M,A>}.
   */
  @SuppressWarnings("unchecked")
  public <M, A> Const<M, A> narrow(@Nullable Kind<ConstKind.Witness<M>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException("Cannot narrow null Kind for Const");
    }
    if (!(kind instanceof ConstKindHolder<?, ?>)) {
      throw new KindUnwrapException(
          "Kind instance cannot be narrowed to Const (received: "
              + kind.getClass().getSimpleName()
              + ")");
    }
    // Safe cast due to type erasure and holder validation
    return ((ConstKindHolder<M, A>) kind).const_();
  }
}
