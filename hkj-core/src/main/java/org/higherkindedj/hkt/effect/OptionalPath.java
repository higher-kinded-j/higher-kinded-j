// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A fluent path wrapper for {@link java.util.Optional} values.
 *
 * <p>{@code OptionalPath} provides a bridge between Java's standard {@code Optional} type and the
 * Effect Path API. It wraps {@code Optional<A>} directly and implements {@link Chainable} for
 * composing operations.
 *
 * <h2>Why OptionalPath?</h2>
 *
 * <p>While {@link MaybePath} provides the same functionality with the library's {@link Maybe} type,
 * {@code OptionalPath} offers:
 *
 * <ul>
 *   <li>Familiar API for Java developers used to {@code Optional}
 *   <li>Direct interop with Java APIs that return {@code Optional}
 *   <li>Seamless conversion to/from other path types
 * </ul>
 *
 * <h2>Creating OptionalPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * OptionalPath<User> path = Path.optional(Optional.of(user));
 * OptionalPath<User> present = Path.present(user);
 * OptionalPath<User> absent = Path.absent();
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * OptionalPath<String> result = Path.optional(findUser(userId))
 *     .filter(User::isActive)
 *     .map(User::getName)
 *     .via(name -> Path.present(name.toUpperCase()));
 * }</pre>
 *
 * <h2>Converting to other paths</h2>
 *
 * <pre>{@code
 * MaybePath<User> maybe = Path.present(user).toMaybePath();
 * EitherPath<Error, User> either = Path.present(user).toEitherPath(Error.notFound());
 * }</pre>
 *
 * @param <A> the type of the contained value
 */
public final class OptionalPath<A> implements Chainable<A> {

  private final Optional<A> value;

  /**
   * Creates a new OptionalPath wrapping the given Optional.
   *
   * @param value the Optional to wrap; must not be null (but may be empty)
   */
  OptionalPath(Optional<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying Optional value.
   *
   * @return the wrapped Optional
   */
  public Optional<A> run() {
    return value;
  }

  /**
   * Returns the contained value if present, otherwise returns the default.
   *
   * @param defaultValue the value to return if empty
   * @return the contained value or the default
   */
  public A getOrElse(A defaultValue) {
    return value.orElse(defaultValue);
  }

  /**
   * Returns the contained value if present, otherwise invokes the supplier.
   *
   * @param supplier provides the default value; must not be null
   * @return the contained value or the supplier's result
   * @throws NullPointerException if supplier is null
   */
  public A getOrElseGet(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return value.orElseGet(supplier);
  }

  /**
   * Returns true if this path contains a value.
   *
   * @return true if present, false if empty
   */
  public boolean isPresent() {
    return value.isPresent();
  }

  /**
   * Returns true if this path is empty.
   *
   * @return true if empty, false if present
   */
  public boolean isEmpty() {
    return value.isEmpty();
  }

  // ===== Filtering =====

  /**
   * Filters the contained value using the predicate.
   *
   * <p>If the value is present and matches the predicate, returns this path. Otherwise returns an
   * empty path.
   *
   * @param predicate the predicate to test; must not be null
   * @return this path if present and matches, empty otherwise
   * @throws NullPointerException if predicate is null
   */
  public OptionalPath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    return new OptionalPath<>(value.filter(predicate));
  }

  // ===== Conversions =====

  /**
   * Converts this OptionalPath to a MaybePath.
   *
   * @return a MaybePath containing the value if present
   */
  public MaybePath<A> toMaybePath() {
    return value
        .<MaybePath<A>>map(a -> new MaybePath<>(Maybe.just(a)))
        .orElseGet(() -> new MaybePath<>(Maybe.nothing()));
  }

  /**
   * Converts this OptionalPath to an EitherPath.
   *
   * @param errorIfEmpty the error to use if this path is empty; must not be null
   * @param <E> the error type
   * @return an EitherPath with Right if present, Left with error if empty
   * @throws NullPointerException if errorIfEmpty is null
   */
  public <E> EitherPath<E, A> toEitherPath(E errorIfEmpty) {
    Objects.requireNonNull(errorIfEmpty, "errorIfEmpty must not be null");
    return value
        .<EitherPath<E, A>>map(a -> new EitherPath<>(Either.right(a)))
        .orElseGet(() -> new EitherPath<>(Either.left(errorIfEmpty)));
  }

  /**
   * Converts this OptionalPath to a ValidationPath.
   *
   * @param errorIfEmpty the error to use if this path is empty; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @return a ValidationPath with Valid if present, Invalid if empty
   * @throws NullPointerException if either argument is null
   */
  public <E> ValidationPath<E, A> toValidationPath(E errorIfEmpty, Semigroup<E> semigroup) {
    Objects.requireNonNull(errorIfEmpty, "errorIfEmpty must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return value
        .<ValidationPath<E, A>>map(a -> new ValidationPath<>(Validated.valid(a), semigroup))
        .orElseGet(() -> new ValidationPath<>(Validated.invalid(errorIfEmpty), semigroup));
  }

  // ===== Composable implementation =====

  @Override
  public <B> OptionalPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new OptionalPath<>(value.map(mapper));
  }

  @Override
  public OptionalPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.ifPresent(consumer);
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> OptionalPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof OptionalPath<?> otherOptional)) {
      throw new IllegalArgumentException("Cannot zipWith non-OptionalPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    OptionalPath<B> typedOther = (OptionalPath<B>) otherOptional;

    if (this.value.isEmpty() || typedOther.value.isEmpty()) {
      return new OptionalPath<>(Optional.empty());
    }

    return new OptionalPath<>(
        Optional.of(combiner.apply(this.value.get(), typedOther.value.get())));
  }

  /**
   * Combines this path with two others using a ternary function.
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner the function to combine the values; must not be null
   * @param <B> the type of the second path's value
   * @param <C> the type of the third path's value
   * @param <D> the type of the combined result
   * @return a new path containing the combined result, or empty if any is empty
   */
  public <B, C, D> OptionalPath<D> zipWith3(
      OptionalPath<B> second,
      OptionalPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (this.value.isEmpty() || second.value.isEmpty() || third.value.isEmpty()) {
      return new OptionalPath<>(Optional.empty());
    }

    return new OptionalPath<>(
        Optional.of(combiner.apply(this.value.get(), second.value.get(), third.value.get())));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> OptionalPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    if (value.isEmpty()) {
      return new OptionalPath<>(Optional.empty());
    }

    Chainable<B> result = mapper.apply(value.get());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof OptionalPath<?> optionalPath)) {
      throw new IllegalArgumentException(
          "via mapper must return OptionalPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    OptionalPath<B> typedResult = (OptionalPath<B>) optionalPath;
    return typedResult;
  }

  @Override
  public <B> OptionalPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    if (value.isEmpty()) {
      return new OptionalPath<>(Optional.empty());
    }

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof OptionalPath<?> optionalPath)) {
      throw new IllegalArgumentException(
          "then supplier must return OptionalPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    OptionalPath<B> typedResult = (OptionalPath<B>) optionalPath;
    return typedResult;
  }

  // ===== Recovery methods (similar to MaybePath) =====

  /**
   * Provides a fallback value if this path is empty.
   *
   * @param alternative the fallback value; must not be null
   * @return this path if present, or a path with the alternative
   * @throws NullPointerException if alternative is null
   */
  public OptionalPath<A> orElsePath(A alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isPresent()) {
      return this;
    }
    return new OptionalPath<>(Optional.of(alternative));
  }

  /**
   * Provides a fallback path if this path is empty.
   *
   * @param alternative supplies the fallback path; must not be null
   * @return this path if present, or the alternative path
   * @throws NullPointerException if alternative is null or returns null
   */
  public OptionalPath<A> orElsePathGet(Supplier<OptionalPath<A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isPresent()) {
      return this;
    }
    OptionalPath<A> result = alternative.get();
    Objects.requireNonNull(result, "alternative must not return null");
    return result;
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof OptionalPath<?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "OptionalPath(" + value.map(Object::toString).orElse("empty") + ")";
  }
}
