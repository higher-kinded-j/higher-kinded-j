// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * A Monad that also has a "zero" or "empty" element and supports alternative/choice operations.
 *
 * <p>MonadZero combines the power of {@link Monad} with {@link Alternative}, providing both monadic
 * bind and choice operations. This is useful for monads that can be filtered and combined, such as
 * {@code List}, {@code Optional}, or {@code Maybe}.
 *
 * <p>The {@code zero()} method provides the empty value for that monad (e.g., an empty list or
 * {@code Optional.empty()}), which serves as the implementation for {@link Alternative#empty()}.
 *
 * <p>This interface is particularly important for enabling the {@code when()} (filtering) clause in
 * for-comprehensions, allowing computations to be short-circuited.
 *
 * <p><b>Key Operations:</b>
 *
 * <ul>
 *   <li>{@link #zero()} - The empty/failure element (implements {@link Alternative#empty()})
 *   <li>{@link Alternative#orElse(Kind, java.util.function.Supplier)} - Combine alternatives
 *   <li>{@link Alternative#guard(boolean)} - Conditional success
 * </ul>
 *
 * @param <F> The witness type of the Monad.
 * @see Monad
 * @see Alternative
 */
@NullMarked
public interface MonadZero<F extends WitnessArity<TypeArity.Unary>>
    extends Monad<F>, Alternative<F> {

  /**
   * Returns the "zero" or "empty" value for this Monad.
   *
   * <p>This method provides the implementation for {@link Alternative#empty()}. The {@code zero()}
   * method is retained for compatibility and semantic clarity in the context of MonadZero.
   *
   * @param <A> The type parameter of the Kind, which will not be present.
   * @return The empty value for the monad (non-null), e.g., {@code Optional.empty()}.
   */
  <A> Kind<F, A> zero();

  /**
   * Provides the {@link Alternative#empty()} implementation by delegating to {@link #zero()}.
   *
   * @param <A> The type parameter of the Kind
   * @return The empty value for this Alternative, same as {@link #zero()}
   */
  @Override
  default <A> Kind<F, A> empty() {
    return zero();
  }

  /**
   * Filters a monadic value by a predicate. If the predicate returns {@code false} for the value,
   * the result is {@link #zero()} (the empty/failure value for this monad).
   *
   * <p>This is the monadic equivalent of {@link java.util.stream.Stream#filter} and {@link
   * java.util.Optional#filter}. Note the argument order: predicate first, then the monadic value —
   * this matches the typeclass-style of {@link Monad#flatMap(java.util.function.Function, Kind)},
   * not the instance-method style of {@code Stream.filter} / {@code Optional.filter}.
   *
   * <p>The default implementation is derived from {@link #flatMap(java.util.function.Function,
   * Kind)} and {@link #of(Object)} / {@link #zero()}, and so adds no new algebraic obligations:
   *
   * <pre>{@code filter(p, ma)  ≡  flatMap(a -> p.test(a) ? of(a) : zero(), ma) }</pre>
   *
   * <p>Concrete instances are free to override with a more efficient implementation (e.g. {@code
   * ListMonad} and {@code StreamMonad} avoid building per-element singleton/empty collections).
   *
   * <p><b>Examples:</b>
   *
   * <pre>{@code
   * // Maybe: keep evens
   * MonadZero<MaybeKind.Witness> mz = MaybeMonad.INSTANCE;
   * mz.filter(x -> x % 2 == 0, mz.of(4));  // Just(4)
   * mz.filter(x -> x % 2 == 0, mz.of(3));  // Nothing
   *
   * // List: keep positives
   * MonadZero<ListKind.Witness> lz = ListMonad.INSTANCE;
   * lz.filter(x -> x > 0, LIST.widen(List.of(-1, 2, -3, 4)));  // [2, 4]
   * }</pre>
   *
   * @param predicate the condition to test; must not be null
   * @param ma the monadic value to filter; must not be null
   * @param <A> the type of the value within the monad
   * @return a monadic value containing only the elements of {@code ma} that satisfy the predicate
   *     (e.g. {@code ma} or {@link #zero()} for single-valued monads such as {@code Maybe} / {@code
   *     Optional}; a possibly-smaller collection for {@code List} / {@code Stream})
   * @throws NullPointerException if {@code predicate} or {@code ma} is null
   */
  default <A> Kind<F, A> filter(Predicate<? super A> predicate, Kind<F, A> ma) {
    requireNonNull(predicate, "predicate for filter cannot be null");
    requireNonNull(ma, "Kind ma for filter cannot be null");
    return flatMap(a -> predicate.test(a) ? of(a) : zero(), ma);
  }
}
