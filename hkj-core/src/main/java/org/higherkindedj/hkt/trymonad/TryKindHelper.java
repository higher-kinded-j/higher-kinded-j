// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link TryConverterOps} for widen/narrow operations, and providing additional
 * factory instance methods for {@link Try} types.
 *
 * <p>Access these operations via the singleton {@code TRY}. For example: {@code
 * TryKindHelper.TRY.widen(myTryValue);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.trymonad.TryKindHelper.TRY; TRY.widen(...);}
 */
public enum TryKindHelper implements TryConverterOps {
  TRY; // Singleton instance named TRY

  /** Error message for when a null {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Try";

  /**
   * Error message prefix for when the {@link Kind} instance is not the expected {@link TryHolder}
   * type.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a TryHolder: ";

  /**
   * Error message for when a {@link TryHolder} internally contains a null {@link Try} instance,
   * which is invalid.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "TryHolder contained null Try instance";

  /**
   * An internal record that implements {@link TryKind}&lt;A&gt; to hold the concrete {@link
   * Try}&lt;A&gt; instance. This serves as the carrier for {@code Try} objects within the HKT
   * simulation.
   */
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {}

  /**
   * Widens a concrete {@link Try}&lt;A&gt; instance into its HKT representation, {@link
   * Kind}&lt;{@link TryKind.Witness}, A&gt; (effectively {@link TryKind}&lt;A&gt;). Implements
   * {@link TryConverterOps#widen}.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param tryInstance The concrete {@link Try}&lt;A&gt; instance to widen. Must be non-null.
   * @return A non-null {@link Kind<TryKind.Witness, A>} representing the wrapped {@code Try}
   *     computation.
   * @throws NullPointerException if {@code tryInstance} is {@code null}.
   */
  @Override
  public <A> Kind<TryKind.Witness, A> widen(Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for widen");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Narrows a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; (which is effectively a {@link
   * TryKind}&lt;A&gt;) back to its concrete {@link Try}&lt;A&gt; representation. Implements {@link
   * TryConverterOps#narrow}.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param kind The {@code Kind<TryKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Try}&lt;A&gt; instance.
   * @throws KindUnwrapException if {@code kind} is {@code null}, if {@code kind} is not an instance
   *     of {@link TryHolder}, or if the {@code TryHolder} internally contains a {@code null} {@link
   *     Try} instance (which indicates an invalid state).
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> Try<A> narrow(@Nullable Kind<TryKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case TryKindHelper.TryHolder<?> holder -> {
        Try<?> internalTry = holder.tryInstance();
        if (internalTry == null) {
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield (Try<A>) internalTry;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Creates a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing a successful computation
   * with the given value.
   *
   * @param <A> The type of the successful value.
   * @param value The successful value.
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the successful computation.
   */
  public <A> Kind<TryKind.Witness, A> success(@Nullable A value) {
    return this.widen(Try.success(value));
  }

  /**
   * Creates a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing a failed computation with
   * the given {@link Throwable}.
   *
   * @param <A> The phantom type parameter representing the value type of the {@code Try}.
   * @param throwable The non-null {@link Throwable} representing the failure.
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the failed computation.
   */
  public <A> Kind<TryKind.Witness, A> failure(Throwable throwable) {
    return this.widen(Try.failure(throwable));
  }

  /**
   * Executes a {@link Supplier} and wraps its outcome into a {@link Kind}&lt;{@link
   * TryKind.Witness}, A&gt;.
   *
   * @param <A> The type of the value supplied by the {@code supplier}.
   * @param supplier The non-null {@link Supplier} to execute.
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the outcome.
   */
  public <A> Kind<TryKind.Witness, A> tryOf(Supplier<? extends A> supplier) {
    return this.widen(Try.of(supplier));
  }
}
