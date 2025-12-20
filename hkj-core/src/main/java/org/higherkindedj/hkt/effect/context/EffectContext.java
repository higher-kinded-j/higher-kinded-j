// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;

/**
 * Base interface for effect contexts that add capabilities to computations.
 *
 * <p>Effect contexts wrap monad transformers with user-friendly APIs, hiding the complexity of
 * higher-kinded types while preserving their power. They provide familiar operations like {@link
 * #map} and {@link #via} that match the Effect Path API vocabulary.
 *
 * <h2>Available Contexts</h2>
 *
 * <ul>
 *   <li>{@link ErrorContext} - Typed error handling using {@code EitherT}
 *   <li>{@link OptionalContext} - Optional values using {@code MaybeT} (library's Maybe type)
 *   <li>{@link JavaOptionalContext} - Optional values using {@code OptionalT} (Java's Optional)
 *   <li>{@link ConfigContext} - Dependency injection using {@code ReaderT}
 *   <li>{@link MutableContext} - Stateful computations using {@code StateT}
 * </ul>
 *
 * <h2>Escape Hatch</h2>
 *
 * <p>For advanced usage, contexts provide access to the underlying transformer via {@link
 * #underlying()}. This allows power users to drop down to Layer 3 (raw transformers) when needed.
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <A> the value type
 */
public sealed interface EffectContext<F, A>
    permits ErrorContext, OptionalContext, JavaOptionalContext, ConfigContext, MutableContext {

  /**
   * Transforms the contained value using the provided function.
   *
   * <p>If this context contains an error or is empty, the function is not applied and the
   * error/empty state is preserved.
   *
   * @param mapper the function to apply to the value; must not be null
   * @param <B> the type of the transformed value
   * @return a new context with the transformed value
   * @throws NullPointerException if mapper is null
   */
  <B> EffectContext<F, B> map(Function<? super A, ? extends B> mapper);

  /**
   * Chains a dependent computation that returns a context.
   *
   * <p>This is the monadic bind operation, named {@code via} to match the Effect Path API
   * vocabulary. The function is applied to the contained value, and the resulting context becomes
   * the new context.
   *
   * <p>If this context contains an error or is empty, the function is not applied and the
   * error/empty state is propagated.
   *
   * @param fn the function to apply, returning a new context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or an error/empty context
   * @throws NullPointerException if fn is null or returns null
   */
  <B> EffectContext<F, B> via(Function<? super A, ? extends EffectContext<F, B>> fn);

  /**
   * Returns the underlying Kind for advanced usage.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying Kind representing this context
   */
  Kind<?, A> underlying();
}
