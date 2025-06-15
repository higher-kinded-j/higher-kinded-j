// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link LazyConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link Lazy} types.
 *
 * <p>Access these operations via the singleton {@code LAZY}. For example: {@code
 * LazyKindHelper.LAZY.widen(myLazyValue);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY; LAZY.widen(...);}
 */
public enum LazyKindHelper implements LazyConverterOps {
  LAZY;

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for Lazy";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a LazyHolder: ";

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_TYPE_NULL_MSG = "Cannot widen null Kind for Lazy";

  /**
   * Internal record implementing {@link LazyKind} to hold the concrete {@link Lazy} instance.
   * Changed to package-private for potential test access.
   */
  record LazyHolder<A>(@NonNull Lazy<A> lazyInstance) implements LazyKind<A> {}

  /**
   * Widens a concrete {@link Lazy<A>} instance into its higher-kinded representation, {@code
   * Kind<LazyKind.Witness, A>}. Implements {@link LazyConverterOps#widen}.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param lazy The non-null, concrete {@link Lazy<A>} instance to widen.
   * @return A non-null {@link Kind<LazyKind.Witness, A>} representing the wrapped {@code Lazy}
   *     computation.
   * @throws NullPointerException if {@code lazy} is {@code null}.
   */
  @Override
  public <A> @NonNull Kind<LazyKind.Witness, A> widen(@NonNull Lazy<A> lazy) {
    Objects.requireNonNull(lazy, "Input Lazy cannot be null for widen");
    return new LazyHolder<>(lazy);
  }

  /**
   * Narrows a {@code Kind<LazyKind.Witness, A>} back to its concrete {@link Lazy<A>} type.
   * Implements {@link LazyConverterOps#narrow}.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param kind The {@code Kind<LazyKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Lazy<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code LazyHolder}, or if the holder's internal {@code Lazy} instance is {@code null}.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> @NonNull Lazy<A> narrow(@Nullable Kind<LazyKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case LazyKindHelper.LazyHolder<?> holder -> (Lazy<A>) holder.lazyInstance();
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Creates a {@link Kind<LazyKind.Witness,A>} by deferring the execution of a {@link
   * ThrowableSupplier}.
   *
   * @param <A> The type of the value that will be produced by the computation.
   * @param computation The non-null {@link ThrowableSupplier} representing the deferred
   *     computation.
   * @return A new, non-null {@code Kind<LazyKind.Witness, A>} representing the deferred {@code
   *     Lazy} computation.
   */
  public <A> @NonNull Kind<LazyKind.Witness, A> defer(@NonNull ThrowableSupplier<A> computation) {
    return this.widen(Lazy.defer(computation));
  }

  /**
   * Creates an already evaluated {@link Kind<LazyKind.Witness,A>} that holds a known value.
   *
   * @param <A> The type of the value.
   * @param value The pre-computed value to be wrapped. Can be {@code null}.
   * @return A new, non-null {@code Kind<LazyKind.Witness, A>} representing an already evaluated
   *     {@code Lazy} computation.
   */
  public <A> @NonNull Kind<LazyKind.Witness, A> now(@Nullable A value) {
    return this.widen(Lazy.now(value));
  }

  /**
   * Forces the evaluation of the {@link Lazy} computation held within the {@link Kind} wrapper and
   * retrieves its result.
   *
   * @param <A> The type of the result produced by the {@code Lazy} computation.
   * @param kind The non-null {@code Kind<LazyKind.Witness, A>} holding the {@code Lazy}
   *     computation.
   * @return The result of the {@code Lazy} computation. Can be {@code null}.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   * @throws Throwable if the underlying {@link ThrowableSupplier} throws an exception.
   */
  public <A> @Nullable A force(@NonNull Kind<LazyKind.Witness, A> kind) throws Throwable {
    return this.narrow(kind).force();
  }
}
