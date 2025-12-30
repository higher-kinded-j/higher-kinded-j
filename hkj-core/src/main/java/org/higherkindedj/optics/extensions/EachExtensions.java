// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.TraverseTraversals;
import org.jspecify.annotations.NullMarked;

/**
 * Provides {@link Each} instances for hkj-core types.
 *
 * <p>This class contains factory methods that create {@code Each} instances for:
 *
 * <ul>
 *   <li>{@link Maybe} - traverses the value if Just (0 or 1 element)
 *   <li>{@link Either} - traverses the Right value (0 or 1 element)
 *   <li>{@link Try} - traverses the Success value (0 or 1 element)
 *   <li>{@link Validated} - traverses the Valid value (0 or 1 element)
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 *
 * <pre>{@code
 * // Maybe traversal
 * Each<Maybe<String>, String> maybeEach = EachExtensions.maybeEach();
 * Traversal<Maybe<String>, String> trav = maybeEach.each();
 * Maybe<String> upper = Traversals.modify(trav, String::toUpperCase, maybe);
 *
 * // Either right traversal
 * Each<Either<Error, Value>, Value> eitherEach = EachExtensions.eitherRightEach();
 * List<Value> values = Traversals.getAll(eitherEach.each(), either);
 * }</pre>
 *
 * @see Each
 * @see Traversal
 * @see org.higherkindedj.optics.each.EachInstances
 */
@NullMarked
public final class EachExtensions {

  /** Private constructor to prevent instantiation. */
  private EachExtensions() {}

  // ===== Maybe =====

  /**
   * Creates an {@link Each} instance for {@link Maybe} types.
   *
   * <p>The returned {@code Each} traverses the value if Just (0 or 1 element). Does not support
   * indexed access.
   *
   * @param <A> The element type of the maybe
   * @return An {@code Each} instance for maybes
   */
  public static <A> Each<Maybe<A>, A> maybeEach() {
    return new MaybeEach<>();
  }

  private static final class MaybeEach<A> implements Each<Maybe<A>, A> {
    @Override
    public Traversal<Maybe<A>, A> each() {
      return TraverseTraversals.forMaybe();
    }
  }

  // ===== Either (Right) =====

  /**
   * Creates an {@link Each} instance for the Right value of {@link Either} types.
   *
   * <p>The returned {@code Each} traverses the Right value if present (0 or 1 element). Left values
   * are not traversed. Does not support indexed access.
   *
   * @param <L> The Left type of the either
   * @param <R> The Right type of the either (traversed element type)
   * @return An {@code Each} instance for Either right values
   */
  public static <L, R> Each<Either<L, R>, R> eitherRightEach() {
    return new EitherRightEach<>();
  }

  private static final class EitherRightEach<L, R> implements Each<Either<L, R>, R> {
    @Override
    public Traversal<Either<L, R>, R> each() {
      return new EitherRightTraversal<>();
    }
  }

  private static final class EitherRightTraversal<L, R> implements Traversal<Either<L, R>, R> {
    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Either<L, R>> modifyF(
        Function<R, Kind<F, R>> f, Either<L, R> source, Applicative<F> app) {
      if (source.isRight()) {
        return app.map(Either::right, f.apply(source.getRight()));
      }
      return app.of(source);
    }
  }

  // ===== Try (Success) =====

  /**
   * Creates an {@link Each} instance for the Success value of {@link Try} types.
   *
   * <p>The returned {@code Each} traverses the Success value if present (0 or 1 element). Failure
   * values are not traversed. Does not support indexed access.
   *
   * @param <A> The Success type of the try
   * @return An {@code Each} instance for Try success values
   */
  public static <A> Each<Try<A>, A> trySuccessEach() {
    return new TrySuccessEach<>();
  }

  private static final class TrySuccessEach<A> implements Each<Try<A>, A> {
    @Override
    public Traversal<Try<A>, A> each() {
      return new TrySuccessTraversal<>();
    }
  }

  private static final class TrySuccessTraversal<A> implements Traversal<Try<A>, A> {
    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Try<A>> modifyF(
        Function<A, Kind<F, A>> f, Try<A> source, Applicative<F> app) {
      if (source instanceof Try.Success<A> success) {
        return app.map(Try::success, f.apply(success.value()));
      }
      return app.of(source);
    }
  }

  // ===== Validated (Valid) =====

  /**
   * Creates an {@link Each} instance for the Valid value of {@link Validated} types.
   *
   * <p>The returned {@code Each} traverses the Valid value if present (0 or 1 element). Invalid
   * values are not traversed. Does not support indexed access.
   *
   * @param <E> The Error type of the validated
   * @param <A> The Valid type of the validated (traversed element type)
   * @return An {@code Each} instance for Validated valid values
   */
  public static <E, A> Each<Validated<E, A>, A> validatedEach() {
    return new ValidatedValidEach<>();
  }

  private static final class ValidatedValidEach<E, A> implements Each<Validated<E, A>, A> {
    @Override
    public Traversal<Validated<E, A>, A> each() {
      return new ValidatedValidTraversal<>();
    }
  }

  private static final class ValidatedValidTraversal<E, A>
      implements Traversal<Validated<E, A>, A> {
    @Override
    public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Validated<E, A>> modifyF(
        Function<A, Kind<F, A>> f, Validated<E, A> source, Applicative<F> app) {
      if (source.isValid()) {
        return app.map(Validated::valid, f.apply(source.get()));
      }
      return app.of(source);
    }
  }
}
