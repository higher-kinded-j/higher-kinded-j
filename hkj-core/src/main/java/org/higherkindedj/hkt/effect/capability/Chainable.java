// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.effect.WriterPath;

/**
 * A capability interface representing types that support sequencing dependent computations.
 *
 * <p>This capability extends {@link Combinable} and corresponds to the Monad typeclass. Types
 * implementing this interface can chain computations where subsequent operations depend on previous
 * results.
 *
 * <h2>Operations</h2>
 *
 * <ul>
 *   <li>{@link #via(Function)} - Chain a dependent computation (the core operation)
 *   <li>{@link #flatMap(Function)} - Alias for via (matches traditional monad terminology)
 *   <li>{@link #then(Supplier)} - Sequence an independent computation, discarding this result
 * </ul>
 *
 * <h2>The {@code via} Pattern</h2>
 *
 * <p>The {@code via} method is the central operation for Effect Path composition, deliberately
 * named to match the Focus DSL's vocabulary. Where FocusPath's {@code via} navigates through data
 * structures, EffectPath's {@code via} navigates through effect types.
 *
 * <h2>Laws</h2>
 *
 * <p>Implementations must satisfy the Monad laws:
 *
 * <ul>
 *   <li><b>Left identity:</b> {@code pure(a).via(f)} equals {@code f.apply(a)}
 *   <li><b>Right identity:</b> {@code path.via(x -> pure(x))} equals {@code path}
 *   <li><b>Associativity:</b> {@code path.via(f).via(g)} equals {@code path.via(x ->
 *       f.apply(x).via(g))}
 * </ul>
 *
 * @param <A> the type of the contained value
 */
public sealed interface Chainable<A> extends Combinable<A>
    permits Recoverable,
        Effectful,
        ValidationPath,
        IdPath,
        OptionalPath,
        GenericPath,
        ReaderPath,
        WithStatePath,
        WriterPath,
        LazyPath,
        ListPath,
        NonDetPath,
        StreamPath,
        TrampolinePath,
        FreePath {

  /**
   * Chains a dependent computation that returns a path.
   *
   * <p>This is the core monadic bind operation, named {@code via} to match the Focus DSL's
   * vocabulary. The function is applied to the contained value, and the resulting path becomes the
   * new path.
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<User> user = Path.maybe(userId)
   *     .via(id -> userRepo.findById(id))      // Returns MaybePath<User>
   *     .via(user -> validateUser(user));      // Returns MaybePath<User>
   * }</pre>
   *
   * @param mapper the function to apply, returning a new path; must not be null
   * @param <B> the type of the value in the returned path
   * @return the path returned by the function, or an error/empty path if this is an error/empty
   * @throws NullPointerException if mapper is null or returns null
   */
  <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> mapper);

  /**
   * Chains a dependent computation that returns a path.
   *
   * <p>This is an alias for {@link #via(Function)} that matches traditional monad terminology.
   *
   * @param mapper the function to apply, returning a new path; must not be null
   * @param <B> the type of the value in the returned path
   * @return the path returned by the function, or an error/empty path if this is an error/empty
   * @throws NullPointerException if mapper is null or returns null
   */
  default <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> mapper) {
    return via(mapper);
  }

  /**
   * Sequences an independent computation, discarding this path's result.
   *
   * <p>This operation is useful when you need to perform an effect but don't care about its result,
   * only about sequencing it after this computation.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Path.maybe(userId)
   *     .via(id -> userRepo.findById(id))
   *     .then(() -> Path.maybe(logService.recordAccess()));  // Log access, discard result
   * }</pre>
   *
   * @param supplier provides the next path to sequence; must not be null
   * @param <B> the type of the value in the returned path
   * @return the path from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  <B> Chainable<B> then(Supplier<? extends Chainable<B>> supplier);
}
