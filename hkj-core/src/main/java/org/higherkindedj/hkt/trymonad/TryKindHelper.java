// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
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
  TRY;

  private static final Class<Try> TRY_CLASS = Try.class;

  /**
   * An internal record that implements {@link TryKind}&lt;A&gt; to hold the concrete {@link
   * Try}&lt;A&gt; instance. This serves as the carrier for {@code Try} objects within the HKT
   * simulation.
   */
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {
    TryHolder {
      Validation.kind().requireForWiden(tryInstance, TRY_CLASS);
    }
  }

  /**
   * Widens a concrete {@link Try}&lt;A&gt; instance into its HKT representation, {@link
   * Kind}&lt;{@link TryKind.Witness}, A&gt;.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param tryInstance The concrete {@link Try}&lt;A&gt; instance to widen. Must be non-null.
   * @return A non-null {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing the wrapped
   *     {@code Try} computation.
   * @throws NullPointerException if {@code tryInstance} is {@code null}.
   */
  @Override
  public <A> Kind<TryKind.Witness, A> widen(Try<A> tryInstance) {
    return new TryHolder<>(tryInstance);
  }

  /**
   * Narrows a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; back to its concrete {@link
   * Try}&lt;A&gt; representation.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param kind The {@code Kind<TryKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Try}&lt;A&gt; instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} is {@code null}, or
   *     if the {@code kind} instance cannot be properly converted to a {@link Try} instance (e.g.,
   *     wrong type or invalid internal state).
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> Try<A> narrow(@Nullable Kind<TryKind.Witness, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind, TRY_CLASS, TryHolder.class, holder -> ((TryHolder<A>) holder).tryInstance());
  }

  /**
   * Creates a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing a successful computation
   * with the given value.
   *
   * @param <A> The type of the successful value.
   * @param value The successful value. Can be {@code null}.
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
   * @throws NullPointerException if {@code throwable} is null.
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
   * @throws NullPointerException if {@code supplier} is null.
   */
  public <A> Kind<TryKind.Witness, A> tryOf(Supplier<? extends A> supplier) {
    return this.widen(Try.of(supplier));
  }
}
