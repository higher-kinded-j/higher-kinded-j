// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Base interface for effect contexts that add capabilities to computations.
 *
 * <p>Effect contexts wrap monad transformers with user-friendly APIs, hiding the complexity of
 * higher-kinded types while preserving their power. They provide familiar operations like {@link
 * #map}, {@code via} and {@code flatMap} that match the Effect Path API vocabulary.
 *
 * <h2>Chaining</h2>
 *
 * <p>Each context declares {@code via}, its alias {@code flatMap}, and {@code then} on itself,
 * typed to its own class, so javac checks every step. A step must be the chain's own kind of
 * context over the same effect, and its other type parameter varies as an Effect Path's does:
 *
 * <ul>
 *   <li>{@link ErrorContext} takes a step that fails with its error type or a subtype of it.
 *   <li>{@link ConfigContext} takes a step that reads its configuration type or a supertype of it.
 *   <li>{@link MutableContext} takes a step with its own state type only.
 * </ul>
 *
 * <p>This interface leaves chaining out because it cannot name the implementing class in a step's
 * type. Code written over any context can map it; code that chains takes the concrete context.
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
public sealed interface EffectContext<F extends WitnessArity<TypeArity.Unary>, A>
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
   * Returns the underlying Kind for advanced usage.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying Kind representing this context
   */
  Kind<?, A> underlying();
}
