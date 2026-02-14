// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.context;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link ContextConverterOps} for widen/narrow operations, and providing
 * additional factory and utility instance methods for {@link Context} types.
 *
 * <p>Access these operations via the singleton {@code CONTEXT}. For example:
 *
 * <pre>{@code
 * Kind<ContextKind.Witness<String>, String> kind = ContextKindHelper.CONTEXT.widen(
 *     Context.ask(MY_SCOPED_VALUE));
 * }</pre>
 *
 * @see Context
 * @see ContextKind
 * @see ContextConverterOps
 */
public enum ContextKindHelper implements ContextConverterOps {
  /** Singleton instance for context operations. */
  CONTEXT;

  private static final Class<Context> CONTEXT_CLASS = Context.class;

  /**
   * Internal record implementing {@link ContextKind ContextKind<R, A>} to hold the concrete {@link
   * Context Context<R, A>} instance.
   *
   * @param <R> The scoped value type of the Context.
   * @param <A> The result type of the Context.
   * @param context The non-null, actual {@link Context Context<R, A>} instance.
   */
  record ContextHolder<R, A>(Context<R, A> context) implements ContextKind<R, A> {

    /**
     * Compact constructor for validation.
     *
     * @throws NullPointerException if the provided context is null.
     */
    ContextHolder {
      Validation.kind().requireForWiden(context, CONTEXT_CLASS);
    }
  }

  /**
   * Widens a concrete {@link Context Context<R, A>} instance into its higher-kinded representation,
   * {@code Kind<ContextKind.Witness<R>, A>}.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result produced by the Context.
   * @param context The concrete {@link Context Context<R, A>} instance to widen. Must be non-null.
   * @return A non-null {@code Kind<ContextKind.Witness<R>, A>} representing the wrapped Context.
   * @throws NullPointerException if {@code context} is null.
   */
  @Override
  public <R, A> Kind<ContextKind.Witness<R>, A> widen(Context<R, A> context) {
    return new ContextHolder<>(context);
  }

  /**
   * Narrows a {@code Kind<ContextKind.Witness<R>, A>} back to its concrete {@link Context
   * Context<R, A>} type.
   *
   * <p>This implementation uses a holder-based approach with pattern matching.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result produced by the Context.
   * @param kind The {@code Kind<ContextKind.Witness<R>, A>} instance to narrow. May be null.
   * @return The underlying, non-null {@link Context Context<R, A>} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input kind is null or not a
   *     valid ContextHolder.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <R, A> Context<R, A> narrow(@Nullable Kind<ContextKind.Witness<R>, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind,
            CONTEXT_CLASS,
            ContextHolder.class,
            holder -> ((ContextHolder<R, A>) holder).context());
  }

  /**
   * Creates a {@code Kind<ContextKind.Witness<R>, R>} that reads from the specified {@link
   * ScopedValue} and returns the value unchanged.
   *
   * @param <R> The type of the scoped value.
   * @param key The ScopedValue to read from. Must not be null.
   * @return A new Kind representing the Context that reads the scoped value.
   * @throws NullPointerException if key is null.
   */
  public <R> Kind<ContextKind.Witness<R>, R> ask(ScopedValue<R> key) {
    return widen(Context.ask(key));
  }

  /**
   * Creates a {@code Kind<ContextKind.Witness<R>, A>} that reads from the specified {@link
   * ScopedValue} and transforms the value.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the transformed result.
   * @param key The ScopedValue to read from. Must not be null.
   * @param f The function to apply. Must not be null.
   * @return A new Kind representing the Context.
   * @throws NullPointerException if key or f is null.
   */
  public <R, A> Kind<ContextKind.Witness<R>, A> asks(
      ScopedValue<R> key, Function<? super R, ? extends A> f) {
    Validation.function().requireFunction(f, "f", CONTEXT_CLASS, Operation.ASKS);
    return widen(Context.asks(key, f));
  }

  /**
   * Creates a {@code Kind<ContextKind.Witness<R>, A>} that succeeds with the given value.
   *
   * @param <R> The phantom type parameter for the scoped value.
   * @param <A> The type of the value.
   * @param value The value to wrap. May be null.
   * @return A new Kind representing a pure Context.
   */
  public <R, A> Kind<ContextKind.Witness<R>, A> succeed(@Nullable A value) {
    return widen(Context.succeed(value));
  }

  /**
   * Creates a {@code Kind<ContextKind.Witness<R>, A>} that fails with the given error.
   *
   * @param <R> The phantom type parameter for the scoped value.
   * @param <A> The phantom type parameter for the result.
   * @param error The error to fail with. Must not be null.
   * @return A new Kind representing a failed Context.
   * @throws NullPointerException if error is null.
   */
  public <R, A> Kind<ContextKind.Witness<R>, A> fail(Throwable error) {
    return widen(Context.fail(error));
  }

  /**
   * Runs a Context computation held within the Kind wrapper.
   *
   * @param <R> The type of the scoped value.
   * @param <A> The type of the result.
   * @param kind The Kind holding the Context. Must not be null.
   * @return The result of running the Context.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if kind is invalid.
   */
  public <R, A> @Nullable A runContext(Kind<ContextKind.Witness<R>, A> kind) {
    Validation.kind().requireNonNull(kind, CONTEXT_CLASS, Operation.RUN);
    return narrow(kind).run();
  }
}
