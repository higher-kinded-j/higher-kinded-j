// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.capability;

import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
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
import org.higherkindedj.hkt.effect.VStreamPath;
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
 * <h2>The Family Parameter</h2>
 *
 * <p>{@code P} is the implementing Path's family: its own class with the value type left open, such
 * as {@code MaybePath<?>}. A step passed to {@link #via}, {@link #then} or {@link
 * Combinable#zipWith} must belong to the same family, so a step that returns another kind of Path,
 * or the same Path with another error, environment, state, log or witness type, does not compile.
 * Each Path chooses how its fixed type parameter may vary in a step:
 *
 * <ul>
 *   <li>An error type is covariant. An {@code EitherPath<E, A>} chains a step typed {@code
 *       EitherPath<NotFound, B>} when {@code NotFound} is a subtype of {@code E}, such as one
 *       variant of a sealed error. The same holds for {@code ValidationPath}, {@code VResultPath}
 *       and the warnings of {@code EitherOrBothPath}; the two that accumulate keep their own
 *       semigroup.
 *   <li>An environment is contravariant. A {@code ReaderPath<R, A>} chains a step that needs a
 *       supertype of {@code R}, that is, less of the environment.
 *   <li>A state, log or witness type is invariant. {@code WithStatePath}, {@code WriterPath},
 *       {@code FreePath}, {@code FreeApPath} and {@code GenericPath} chain only their own.
 * </ul>
 *
 * <p>A chain whose first path is typed on one variant of an error stays on that variant: write the
 * wider type where the chain starts, as in {@code Path.<AppError, User>right(user)}. On an {@code
 * EitherPath} or a {@code VResultPath} that a helper returned, write {@code .<AppError>mapError(e
 * -> e)}; a {@code ValidationPath} or an {@code EitherOrBothPath} widens with {@code mapErrorWith},
 * which takes a semigroup for the wider type.
 *
 * <p>Code written once over several Paths declares the family as a type parameter, as in {@code <P,
 * A> Chainable<P, A>}, to chain and combine. A type that leaves it open, as in {@code Chainable<?,
 * A>}, can be mapped, but no step fits it. Each Path still checks a step's kind when the chain
 * runs, and throws {@link IllegalArgumentException} for another kind: a raw type, code compiled
 * against an older version, or a hand-written {@code Combinable} that claims another Path's family
 * can still pass one.
 *
 * @param <P> the family of the implementing Path: its own class with the value type left open, such
 *     as {@code MaybePath<?>}; see {@link Chainable}
 * @param <A> the type of the contained value
 */
public sealed interface Chainable<P, A> extends Combinable<P, A>
    permits Recoverable,
        Effectful,
        ValidationPath,
        EitherOrBothPath,
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
        VStreamPath,
        TrampolinePath,
        FreePath {

  /**
   * Chains a dependent computation that returns a path.
   *
   * <p>This is the core monadic bind operation, named {@code via} to match the Focus DSL's
   * vocabulary. The function is applied to the contained value, and the resulting path becomes the
   * new path. The function must return a path of this path's family {@code P}.
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
  <B> Chainable<P, B> via(Function<? super A, ? extends Chainable<? extends P, B>> mapper);

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
  default <B> Chainable<P, B> flatMap(
      Function<? super A, ? extends Chainable<? extends P, B>> mapper) {
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
  <B> Chainable<P, B> then(Supplier<? extends Chainable<? extends P, B>> supplier);
}
