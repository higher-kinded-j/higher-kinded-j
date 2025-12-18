// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;

/**
 * A capability interface representing types that support error recovery.
 *
 * <p>This capability extends {@link Chainable} and corresponds to the MonadError typeclass. Types
 * implementing this interface can recover from errors and transform error types.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #recover(Function)} - Recover from an error by providing a fallback value
 *   <li>{@link #recoverWith(Function)} - Recover from an error by providing a fallback path
 *   <li>{@link #orElse(Supplier)} - Provide an alternative path if this one fails
 *   <li>{@link #mapError(Function)} - Transform the error type without affecting success values
 * </ul>
 *
 * <h2>Error Type Parameter</h2>
 *
 * <p>The error type {@code E} varies by path type:
 *
 * <ul>
 *   <li>{@code MaybePath} uses {@code Unit} (absence is the only "error")
 *   <li>{@code EitherPath<E, A>} uses {@code E} (the left type)
 *   <li>{@code TryPath} uses {@code Throwable}
 * </ul>
 *
 * @param <E> the type of error this path can contain
 * @param <A> the type of the contained value
 */
public sealed interface Recoverable<E, A> extends Chainable<A>
    permits MaybePath, EitherPath, TryPath, ValidationPath, CompletableFuturePath {

  /**
   * Recovers from an error by providing a fallback value.
   *
   * <p>If this path contains an error, the recovery function is applied to produce a success value.
   * If this path already contains a value, it is returned unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * String name = Path.maybe(userId)
   *     .via(id -> userRepo.findById(id))
   *     .map(User::getName)
   *     .recover(error -> "Anonymous");
   * }</pre>
   *
   * @param recovery the function to apply to the error to produce a value; must not be null
   * @return a path containing either the original value or the recovered value
   * @throws NullPointerException if recovery is null
   */
  Recoverable<E, A> recover(Function<? super E, ? extends A> recovery);

  /**
   * Recovers from an error by providing a fallback path.
   *
   * <p>If this path contains an error, the recovery function is applied to produce an alternative
   * path. If this path already contains a value, it is returned unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<User> user = Path.maybe(userId)
   *     .via(id -> primaryRepo.findById(id))
   *     .recoverWith(error -> Path.maybe(secondaryRepo.findById(userId)));
   * }</pre>
   *
   * @param recovery the function to apply to the error to produce a fallback path; must not be null
   * @return either this path (if successful) or the fallback path
   * @throws NullPointerException if recovery is null or returns null
   */
  Recoverable<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> recovery);

  /**
   * Provides an alternative path if this one represents an error.
   *
   * <p>This is a convenience method that ignores the specific error and provides a fallback. It is
   * equivalent to {@code recoverWith(ignored -> alternative.get())}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<Config> config = Path.maybe(loadFromFile())
   *     .orElse(() -> Path.maybe(loadDefaults()));
   * }</pre>
   *
   * @param alternative provides the fallback path; must not be null
   * @return either this path (if successful) or the alternative
   * @throws NullPointerException if alternative is null or returns null
   */
  Recoverable<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative);

  /**
   * Transforms the error type without affecting success values.
   *
   * <p>If this path contains an error, the function is applied to transform it. If this path
   * contains a value, it is returned with the error type changed (the value is unchanged).
   *
   * <p>Example:
   *
   * <pre>{@code
   * EitherPath<AppError, User> result = Path.tryOf(() -> parseUser(json))
   *     .toEitherPath()
   *     .mapError(ex -> new AppError("Parse failed", ex));
   * }</pre>
   *
   * @param mapper the function to transform the error; must not be null
   * @param <E2> the new error type
   * @return a path with the transformed error type
   * @throws NullPointerException if mapper is null
   */
  <E2> Recoverable<E2, A> mapError(Function<? super E, ? extends E2> mapper);
}
