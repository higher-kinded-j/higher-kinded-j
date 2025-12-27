// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Escape hatch for using custom monads in Path composition.
 *
 * <p>{@code GenericPath} allows advanced users to wrap any type that has a {@link Monad} instance,
 * enabling participation in Path-style composition without requiring explicit support in the
 * capability interface hierarchy.
 *
 * <h2>When to Use GenericPath</h2>
 *
 * <ul>
 *   <li><b>Custom domain monads</b>: When you have a custom Result/Outcome type
 *   <li><b>Third-party library interop</b>: Wrapping external library's monadic types
 *   <li><b>Testing/prototyping</b>: Quick experiments before adding full Path support
 * </ul>
 *
 * <h2>Phase 3 Enhancements</h2>
 *
 * <ul>
 *   <li>Optional {@link MonadError} support for error recovery
 *   <li>Natural transformation support via {@link #mapK(NaturalTransformation, Monad)}
 *   <li>Same witness type required for composition
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Wrap a custom monad
 * CustomResult<User> result = customService.getUser(id);
 * GenericPath<CustomResultKind.Witness, User> path =
 *     GenericPath.of(CustomResultKindHelper.widen(result), CustomResultMonad.INSTANCE);
 *
 * // Compose with map and via
 * GenericPath<CustomResultKind.Witness, String> name = path
 *     .map(User::getName)
 *     .via(n -> GenericPath.of(
 *         CustomResultKindHelper.widen(validateName(n)),
 *         CustomResultMonad.INSTANCE));
 *
 * // Convert to concrete path when narrower is available
 * MaybePath<User> maybePath = genericPath.toMaybePath(MaybeKindHelper::narrow);
 * }</pre>
 *
 * @param <F> the witness type of the underlying monad
 * @param <A> the value type
 * @see Monad
 * @see Kind
 */
public final class GenericPath<F extends WitnessArity<TypeArity.Unary>, A> implements Chainable<A> {

  private final Kind<F, A> value;
  private final Monad<F> monad;
  private final MonadError<F, ?> monadError; // null if not provided

  /**
   * Creates a new GenericPath wrapping the given Kind with the specified Monad instance.
   *
   * @param value the Kind to wrap; must not be null
   * @param monad the Monad instance for this type; must not be null
   * @param monadError the optional MonadError instance; may be null
   */
  private GenericPath(Kind<F, A> value, Monad<F> monad, MonadError<F, ?> monadError) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    this.monad = Objects.requireNonNull(monad, "monad must not be null");
    this.monadError = monadError; // nullable
  }

  // ===== Factory Methods =====

  /**
   * Creates a GenericPath from a Kind and Monad instance.
   *
   * @param value the Kind to wrap; must not be null
   * @param monad the Monad instance; must not be null
   * @param <F> the witness type
   * @param <A> the value type
   * @return a new GenericPath
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> GenericPath<F, A> of(
      Kind<F, A> value, Monad<F> monad) {
    return new GenericPath<>(value, monad, null);
  }

  /**
   * Creates a GenericPath from a Kind and MonadError instance.
   *
   * <p>This factory enables error recovery operations like {@link #recover(Function)}, {@link
   * #recoverWith(Function)}, and {@link #mapError(Function)}.
   *
   * @param value the Kind to wrap; must not be null
   * @param monadError the MonadError instance; must not be null
   * @param <F> the witness type
   * @param <E> the error type
   * @param <A> the value type
   * @return a new GenericPath with error recovery support
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A> GenericPath<F, A> of(
      Kind<F, A> value, MonadError<F, E> monadError) {
    Objects.requireNonNull(monadError, "monad must not be null");
    return new GenericPath<>(value, monadError, monadError);
  }

  /**
   * Lifts a pure value into a GenericPath using the Monad's {@code of} method.
   *
   * @param value the value to lift
   * @param monad the Monad instance; must not be null
   * @param <F> the witness type
   * @param <A> the value type
   * @return a new GenericPath containing the value
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> GenericPath<F, A> pure(
      A value, Monad<F> monad) {
    Objects.requireNonNull(monad, "monad must not be null");
    return new GenericPath<>(monad.of(value), monad, null);
  }

  /**
   * Lifts a pure value into a GenericPath using the MonadError's {@code of} method.
   *
   * <p>This factory enables error recovery operations.
   *
   * @param value the value to lift
   * @param monadError the MonadError instance; must not be null
   * @param <F> the witness type
   * @param <E> the error type
   * @param <A> the value type
   * @return a new GenericPath containing the value with error recovery support
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A> GenericPath<F, A> pure(
      A value, MonadError<F, E> monadError) {
    Objects.requireNonNull(monadError, "monad must not be null");
    return new GenericPath<>(monadError.of(value), monadError, monadError);
  }

  /**
   * Creates a GenericPath representing an error.
   *
   * <p>Requires a MonadError instance to lift the error into the monadic context.
   *
   * @param error the error value
   * @param monadError the MonadError instance; must not be null
   * @param <F> the witness type
   * @param <E> the error type
   * @param <A> the value type
   * @return a new GenericPath representing an error
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A> GenericPath<F, A> raiseError(
      E error, MonadError<F, E> monadError) {
    Objects.requireNonNull(monadError, "monadError must not be null");
    return new GenericPath<>(monadError.raiseError(error), monadError, monadError);
  }

  // ===== Terminal Operations =====

  /**
   * Returns the underlying Kind.
   *
   * <p><b>Note:</b> This exposes HKT types. Use conversion methods when a concrete path type is
   * available.
   *
   * @return the wrapped Kind
   */
  public Kind<F, A> runKind() {
    return value;
  }

  /**
   * Returns the Monad instance for this path.
   *
   * @return the Monad instance
   */
  public Monad<F> monad() {
    return monad;
  }

  /**
   * Returns whether this GenericPath supports error recovery operations.
   *
   * <p>Recovery is supported when a {@link MonadError} instance was provided at construction.
   *
   * @return true if recovery operations are available
   */
  public boolean supportsRecovery() {
    return monadError != null;
  }

  /**
   * Returns the optional MonadError instance if available.
   *
   * @param <E> the error type
   * @return an Optional containing the MonadError if present
   */
  @SuppressWarnings("unchecked")
  public <E> Optional<MonadError<F, E>> monadError() {
    return Optional.ofNullable((MonadError<F, E>) monadError);
  }

  // ===== Error Recovery =====

  /**
   * Recovers from an error by applying the given function.
   *
   * <p>Requires a MonadError instance to have been provided at construction.
   *
   * @param recovery the function to apply to the error to produce a recovery value
   * @param <E> the error type
   * @return a new GenericPath that recovers from errors
   * @throws UnsupportedOperationException if no MonadError was provided
   */
  @SuppressWarnings("unchecked")
  public <E> GenericPath<F, A> recover(Function<? super E, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (monadError == null) {
      throw new UnsupportedOperationException(
          "recover requires MonadError support. Use GenericPath.of(value, monadError) factory.");
    }
    MonadError<F, E> me = (MonadError<F, E>) monadError;
    Kind<F, A> recovered = me.handleError(value, recovery);
    return new GenericPath<>(recovered, monad, monadError);
  }

  /**
   * Recovers from an error by applying the given function that returns a new GenericPath.
   *
   * <p>Requires a MonadError instance to have been provided at construction.
   *
   * @param recovery the function to apply to the error to produce a recovery path
   * @param <E> the error type
   * @return a new GenericPath that recovers from errors
   * @throws UnsupportedOperationException if no MonadError was provided
   */
  @SuppressWarnings("unchecked")
  public <E> GenericPath<F, A> recoverWith(
      Function<? super E, ? extends GenericPath<F, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (monadError == null) {
      throw new UnsupportedOperationException(
          "recoverWith requires MonadError support. Use GenericPath.of(value, monadError) factory.");
    }
    MonadError<F, E> me = (MonadError<F, E>) monadError;
    Kind<F, A> recovered = me.handleErrorWith(value, e -> recovery.apply(e).runKind());
    return new GenericPath<>(recovered, monad, monadError);
  }

  /**
   * Transforms the error type using the given function.
   *
   * <p>This allows changing the error while keeping the success value intact. Requires a MonadError
   * instance to have been provided at construction.
   *
   * <p>Note: This method is useful for error type unification but requires the target MonadError to
   * be able to raise the new error type.
   *
   * @param mapper the function to transform the error
   * @param targetMonadError the MonadError for the target error type
   * @param <E1> the original error type
   * @param <E2> the new error type
   * @return a new GenericPath with the transformed error
   * @throws UnsupportedOperationException if no MonadError was provided
   */
  @SuppressWarnings("unchecked")
  public <E1, E2> GenericPath<F, A> mapError(
      Function<? super E1, ? extends E2> mapper, MonadError<F, E2> targetMonadError) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Objects.requireNonNull(targetMonadError, "targetMonadError must not be null");
    if (monadError == null) {
      throw new UnsupportedOperationException(
          "mapError requires MonadError support. Use GenericPath.of(value, monadError) factory.");
    }
    MonadError<F, E1> me = (MonadError<F, E1>) monadError;
    Kind<F, A> mapped =
        me.handleErrorWith(value, e -> targetMonadError.raiseError(mapper.apply(e)));
    return new GenericPath<>(mapped, targetMonadError, targetMonadError);
  }

  // ===== Natural Transformations =====

  /**
   * Transforms this GenericPath to a different effect type using a natural transformation.
   *
   * <p>This allows converting computations from one effect type to another while preserving the
   * structure.
   *
   * <pre>{@code
   * // Convert Maybe to Either with a default error
   * NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither = ...;
   * GenericPath<MaybeKind.Witness, User> maybePath = ...;
   * GenericPath<EitherKind.Witness<String>, User> eitherPath =
   *     maybePath.mapK(maybeToEither, eitherMonad);
   * }</pre>
   *
   * @param transform the natural transformation to apply; must not be null
   * @param targetMonad the Monad instance for the target type; must not be null
   * @param <G> the target witness type
   * @return a new GenericPath with the transformed effect type
   */
  public <G extends WitnessArity<TypeArity.Unary>> GenericPath<G, A> mapK(
      NaturalTransformation<F, G> transform, Monad<G> targetMonad) {
    Objects.requireNonNull(transform, "transform must not be null");
    Objects.requireNonNull(targetMonad, "targetMonad must not be null");
    Kind<G, A> transformed = transform.apply(value);
    return new GenericPath<>(transformed, targetMonad, null);
  }

  /**
   * Transforms this GenericPath to a different effect type using a natural transformation,
   * preserving error recovery capabilities.
   *
   * @param transform the natural transformation to apply; must not be null
   * @param targetMonadError the MonadError instance for the target type; must not be null
   * @param <G> the target witness type
   * @param <E> the error type
   * @return a new GenericPath with the transformed effect type and error recovery support
   */
  public <G extends WitnessArity<TypeArity.Unary>, E> GenericPath<G, A> mapK(
      NaturalTransformation<F, G> transform, MonadError<G, E> targetMonadError) {
    Objects.requireNonNull(transform, "transform must not be null");
    Objects.requireNonNull(targetMonadError, "targetMonadError must not be null");
    Kind<G, A> transformed = transform.apply(value);
    return new GenericPath<>(transformed, targetMonadError, targetMonadError);
  }

  // ===== Conversions =====

  /**
   * Converts to MaybePath using a narrowing function.
   *
   * <p>Example:
   *
   * <pre>{@code
   * MaybePath<User> maybePath = genericPath.toMaybePath(MaybeKindHelper::narrow);
   * }</pre>
   *
   * @param narrower function to convert Kind to Maybe
   * @return a MaybePath
   */
  public MaybePath<A> toMaybePath(Function<Kind<F, A>, Maybe<A>> narrower) {
    Objects.requireNonNull(narrower, "narrower must not be null");
    return new MaybePath<>(narrower.apply(value));
  }

  /**
   * Converts to EitherPath using a narrowing function.
   *
   * <p>Example:
   *
   * <pre>{@code
   * EitherPath<Error, User> eitherPath = genericPath.toEitherPath(EitherKindHelper::narrow);
   * }</pre>
   *
   * @param narrower function to convert Kind to Either
   * @param <E> the error type
   * @return an EitherPath
   */
  public <E> EitherPath<E, A> toEitherPath(Function<Kind<F, A>, Either<E, A>> narrower) {
    Objects.requireNonNull(narrower, "narrower must not be null");
    return new EitherPath<>(narrower.apply(value));
  }

  // ===== Composable implementation =====

  @Override
  public <B> GenericPath<F, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new GenericPath<>(monad.map(mapper, value), monad, monadError);
  }

  @Override
  public GenericPath<F, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    return map(
        a -> {
          consumer.accept(a);
          return a;
        });
  }

  // ===== Combinable implementation =====

  @Override
  @SuppressWarnings("unchecked")
  public <B, C> GenericPath<F, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof GenericPath<?, ?> otherGeneric)) {
      throw new IllegalArgumentException(
          "GenericPath can only zipWith another GenericPath. Got: " + other.getClass());
    }

    // Runtime check: same witness type (relies on same Monad instance in practice)
    GenericPath<F, B> typedOther = (GenericPath<F, B>) otherGeneric;

    Kind<F, C> result =
        monad.flatMap(a -> monad.map(b -> combiner.apply(a, b), typedOther.value), value);
    return new GenericPath<>(result, monad, monadError);
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null and same witness type
   * @param third the third path; must not be null and same witness type
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result
   */
  public <B, C, D> GenericPath<F, D> zipWith3(
      GenericPath<F, B> second,
      GenericPath<F, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    Kind<F, D> result =
        monad.flatMap(
            a ->
                monad.flatMap(
                    b -> monad.map(c -> combiner.apply(a, b, c), third.value), second.value),
            value);
    return new GenericPath<>(result, monad, monadError);
  }

  // ===== Chainable implementation =====

  @Override
  @SuppressWarnings("unchecked")
  public <B> GenericPath<F, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    Kind<F, B> result =
        monad.flatMap(
            a -> {
              Chainable<B> chainResult = mapper.apply(a);
              Objects.requireNonNull(chainResult, "mapper must not return null");

              if (!(chainResult instanceof GenericPath<?, ?> gp)) {
                throw new IllegalArgumentException(
                    "GenericPath.via must return GenericPath. Got: " + chainResult.getClass());
              }

              return ((GenericPath<F, B>) gp).value;
            },
            value);

    return new GenericPath<>(result, monad, monadError);
  }

  @Override
  public <B> GenericPath<F, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return via(_ -> supplier.get());
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof GenericPath<?, ?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "GenericPath(" + value + ")";
  }
}
