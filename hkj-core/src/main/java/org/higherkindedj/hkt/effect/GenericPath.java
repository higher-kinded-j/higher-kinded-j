// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
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
 * <h2>Phase 2 Limitations</h2>
 *
 * <ul>
 *   <li>Cannot mix with concrete path types in via/zipWith operations
 *   <li>No automatic error recovery (no MonadError evidence)
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
public final class GenericPath<F, A> implements Chainable<A> {

  private final Kind<F, A> value;
  private final Monad<F> monad;

  /**
   * Creates a new GenericPath wrapping the given Kind with the specified Monad instance.
   *
   * @param value the Kind to wrap; must not be null
   * @param monad the Monad instance for this type; must not be null
   */
  private GenericPath(Kind<F, A> value, Monad<F> monad) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    this.monad = Objects.requireNonNull(monad, "monad must not be null");
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
  public static <F, A> GenericPath<F, A> of(Kind<F, A> value, Monad<F> monad) {
    return new GenericPath<>(value, monad);
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
  public static <F, A> GenericPath<F, A> pure(A value, Monad<F> monad) {
    Objects.requireNonNull(monad, "monad must not be null");
    return new GenericPath<>(monad.of(value), monad);
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
    return new GenericPath<>(monad.map(mapper, value), monad);
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
    return new GenericPath<>(result, monad);
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
    return new GenericPath<>(result, monad);
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

    return new GenericPath<>(result, monad);
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
