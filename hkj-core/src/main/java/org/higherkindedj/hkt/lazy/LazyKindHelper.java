// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
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

  private static final Class<Lazy> LAZY_CLASS = Lazy.class;

  /**
   * Internal record implementing {@link LazyKind} to hold the concrete {@link Lazy} instance.
   *
   * @param <A> The result type of the Lazy computation.
   * @param lazyInstance The non-null, actual {@link Lazy} instance.
   */
  record LazyHolder<A>(Lazy<A> lazyInstance) implements LazyKind<A> {
    LazyHolder {
      Validation.kind().requireForWiden(lazyInstance, LAZY_CLASS);
    }
  }

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
  public <A> Kind<LazyKind.Witness, A> widen(Lazy<A> lazy) {
    return new LazyHolder<>(lazy);
  }

  /**
   * Narrows a {@code Kind<LazyKind.Witness, A>} back to its concrete {@link Lazy<A>} type.
   * Implements {@link LazyConverterOps#narrow}.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * @param <A> The result type of the {@code Lazy} computation.
   * @param kind The {@code Kind<LazyKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Lazy<A>} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of the expected underlying holder type for Lazy.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> Lazy<A> narrow(@Nullable Kind<LazyKind.Witness, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind, LAZY_CLASS, LazyHolder.class, holder -> ((LazyHolder<A>) holder).lazyInstance());
  }

  /**
   * Creates a {@link Kind<LazyKind.Witness,A>} by deferring the execution of a {@link
   * ThrowableSupplier}.
   *
   * @param <A> The result type of the computation.
   * @param computation The computation to defer. Must not be null.
   * @return A non-null {@link Kind<LazyKind.Witness, A>} representing the deferred computation.
   * @throws NullPointerException if {@code computation} is null (enforced by Lazy.defer).
   */
  public <A> Kind<LazyKind.Witness, A> defer(ThrowableSupplier<A> computation) {
    return this.widen(Lazy.defer(computation));
  }

  /**
   * Creates an already evaluated {@link Kind<LazyKind.Witness,A>} that holds a known value.
   *
   * @param <A> The type of the value.
   * @param value The value to wrap. Can be {@code null}.
   * @return A non-null {@link Kind<LazyKind.Witness, A>} representing an already evaluated Lazy
   *     holding {@code value}.
   */
  public <A> Kind<LazyKind.Witness, A> now(@Nullable A value) {
    return this.widen(Lazy.now(value));
  }

  /**
   * Forces the evaluation of the {@link Lazy} computation held within the {@link Kind} wrapper and
   * retrieves its result.
   *
   * @param <A> The result type of the computation.
   * @param kind The {@code Kind<LazyKind.Witness, A>} holding the Lazy computation. Must not be
   *     null.
   * @return The result of forcing the Lazy computation. Can be {@code null} depending on type
   *     {@code A}.
   * @throws Throwable if the lazy computation fails.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} cannot be
   *     unwrapped.
   */
  public <A> @Nullable A force(Kind<LazyKind.Witness, A> kind) throws Throwable {
    return this.narrow(kind).force();
  }
}
