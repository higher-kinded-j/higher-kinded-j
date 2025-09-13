// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.lazy;

import static org.higherkindedj.hkt.util.ErrorHandling.*;

import org.higherkindedj.hkt.Kind;
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

  private static final String TYPE_NAME = "Lazy";

  /**
   * Internal record implementing {@link LazyKind} to hold the concrete {@link Lazy} instance.
   * Updated to use standardized holder validation.
   *
   * @param <A> The result type of the Lazy computation.
   * @param lazyInstance The non-null, actual {@link Lazy} instance.
   */
  record LazyHolder<A>(Lazy<A> lazyInstance) implements LazyKind<A> {
    LazyHolder {
      requireNonNullForHolder(lazyInstance, TYPE_NAME);
    }
  }

  /**
   * Widens a concrete {@link Lazy<A>} instance into its higher-kinded representation, {@code
   * Kind<LazyKind.Witness, A>}. Implements {@link LazyConverterOps#widen}.
   */
  @Override
  public <A> Kind<LazyKind.Witness, A> widen(Lazy<A> lazy) {
    requireNonNullForWiden(lazy, TYPE_NAME);
    return new LazyHolder<>(lazy);
  }

  /**
   * Narrows a {@code Kind<LazyKind.Witness, A>} back to its concrete {@link Lazy<A>} type.
   * Implements {@link LazyConverterOps#narrow}.
   */
  @Override
  public <A> Lazy<A> narrow(@Nullable Kind<LazyKind.Witness, A> kind) {
    return narrowKind(kind, TYPE_NAME + "Holder", this::extractLazy);
  }

  /**
   * Creates a {@link Kind<LazyKind.Witness,A>} by deferring the execution of a {@link
   * ThrowableSupplier}.
   */
  public <A> Kind<LazyKind.Witness, A> defer(ThrowableSupplier<A> computation) {
    return this.widen(Lazy.defer(computation));
  }

  /** Creates an already evaluated {@link Kind<LazyKind.Witness,A>} that holds a known value. */
  public <A> Kind<LazyKind.Witness, A> now(@Nullable A value) {
    return this.widen(Lazy.now(value));
  }

  /**
   * Forces the evaluation of the {@link Lazy} computation held within the {@link Kind} wrapper and
   * retrieves its result.
   */
  public <A> @Nullable A force(Kind<LazyKind.Witness, A> kind) throws Throwable {
    return this.narrow(kind).force();
  }

  private <A> Lazy<A> extractLazy(Kind<LazyKind.Witness, A> kind) {
    return switch (kind) {
      case LazyHolder<A> holder -> holder.lazyInstance();
      default -> throw new ClassCastException(); // Will be caught and wrapped by narrowKind
    };
  }
}
