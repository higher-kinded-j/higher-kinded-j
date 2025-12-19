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
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link Maybe} values.
 *
 * <p>{@code MaybePath} provides a chainable API for composing operations on optional values. It
 * implements {@link Recoverable} with {@link Unit} as the error type, since the only "error" state
 * is the absence of a value.
 *
 * <h2>Creating MaybePath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * MaybePath<String> path = Path.maybe(someValue);
 * MaybePath<User> userPath = Path.maybe(userRepo.findById(id));
 * MaybePath<String> empty = Path.nothing();
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * String name = Path.maybe(userId)
 *     .via(id -> Path.maybe(userRepo.findById(id)))
 *     .map(User::getName)
 *     .filter(name -> !name.isEmpty())
 *     .getOrElse("Anonymous");
 * }</pre>
 *
 * <h2>Converting to other path types</h2>
 *
 * <pre>{@code
 * EitherPath<Error, User> result = Path.maybe(userId)
 *     .via(id -> Path.maybe(userRepo.findById(id)))
 *     .toEitherPath(Error.notFound("User not found"));
 * }</pre>
 *
 * @param <A> the type of the contained value
 */
public final class MaybePath<A> implements Recoverable<Unit, A> {

  private final Maybe<A> value;

  /**
   * Creates a new MaybePath wrapping the given Maybe.
   *
   * @param value the Maybe to wrap; must not be null
   */
  MaybePath(Maybe<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying Maybe value.
   *
   * @return the wrapped Maybe
   */
  public Maybe<A> run() {
    return value;
  }

  /**
   * Returns the contained value if present, otherwise returns the provided default.
   *
   * @param defaultValue the value to return if this path is empty
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
   * Filters the contained value by the predicate.
   *
   * <p>If this path contains a value and the predicate returns true, the value is kept. Otherwise,
   * the result is an empty path.
   *
   * @param predicate the condition to test; must not be null
   * @return this path if the predicate passes, otherwise an empty path
   * @throws NullPointerException if predicate is null
   */
  public MaybePath<A> filter(Predicate<? super A> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    if (value.isJust() && predicate.test(value.get())) {
      return this;
    }
    return new MaybePath<>(Maybe.nothing());
  }

  /**
   * Converts this MaybePath to an EitherPath.
   *
   * <p>If this path contains a value, returns an EitherPath with a Right value. If this path is
   * empty, returns an EitherPath with the provided error as the Left value.
   *
   * @param error the error to use if this path is empty
   * @param <E> the error type
   * @return an EitherPath representing this path's value or the provided error
   */
  public <E> EitherPath<E, A> toEitherPath(E error) {
    return value.isJust()
        ? new EitherPath<>(Either.right(value.get()))
        : new EitherPath<>(Either.left(error));
  }

  /**
   * Converts this MaybePath to a TryPath.
   *
   * <p>If this path contains a value, returns a TryPath with a Success. If this path is empty,
   * returns a TryPath with the provided exception as a Failure.
   *
   * @param exceptionSupplier provides the exception if this path is empty; must not be null
   * @return a TryPath representing this path's value or the provided exception
   * @throws NullPointerException if exceptionSupplier is null
   */
  public TryPath<A> toTryPath(Supplier<? extends Throwable> exceptionSupplier) {
    Objects.requireNonNull(exceptionSupplier, "exceptionSupplier must not be null");
    return value.isJust()
        ? new TryPath<>(Try.success(value.get()))
        : new TryPath<>(Try.failure(exceptionSupplier.get()));
  }

  /**
   * Converts this MaybePath to a ValidationPath.
   *
   * <p>If this path contains a value, returns a Valid. If this path is empty, returns an Invalid
   * with the provided error.
   *
   * @param errorIfEmpty the error to use if this path is empty; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @return a ValidationPath representing this path's value or the provided error
   * @throws NullPointerException if either argument is null
   */
  public <E> ValidationPath<E, A> toValidationPath(E errorIfEmpty, Semigroup<E> semigroup) {
    Objects.requireNonNull(errorIfEmpty, "errorIfEmpty must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return value.isJust()
        ? new ValidationPath<>(Validated.valid(value.get()), semigroup)
        : new ValidationPath<>(Validated.invalid(errorIfEmpty), semigroup);
  }

  /**
   * Converts this MaybePath to an OptionalPath.
   *
   * @return an OptionalPath containing the value if present, or empty otherwise
   */
  public OptionalPath<A> toOptionalPath() {
    return value.isJust()
        ? new OptionalPath<>(Optional.of(value.get()))
        : new OptionalPath<>(Optional.empty());
  }

  /**
   * Converts this MaybePath to an IdPath.
   *
   * <p>If this path contains a value, returns an IdPath wrapping it. If this path is empty, throws
   * the exception provided by the supplier.
   *
   * @param exceptionSupplier provides the exception if this path is empty; must not be null
   * @return an IdPath containing this path's value
   * @throws RuntimeException the exception from the supplier if this path is empty
   * @throws NullPointerException if exceptionSupplier is null
   */
  public IdPath<A> toIdPath(Supplier<? extends RuntimeException> exceptionSupplier) {
    Objects.requireNonNull(exceptionSupplier, "exceptionSupplier must not be null");
    if (value.isJust()) {
      return new IdPath<>(Id.of(value.get()));
    }
    throw exceptionSupplier.get();
  }

  // ===== Composable implementation =====

  @Override
  public <B> MaybePath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new MaybePath<>(value.map(mapper));
  }

  @Override
  public MaybePath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    if (value.isJust()) {
      consumer.accept(value.get());
    }
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> MaybePath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof MaybePath<?> otherMaybe)) {
      throw new IllegalArgumentException("Cannot zipWith non-MaybePath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    MaybePath<B> typedOther = (MaybePath<B>) otherMaybe;

    if (this.value.isJust() && typedOther.value.isJust()) {
      return new MaybePath<>(Maybe.just(combiner.apply(this.value.get(), typedOther.value.get())));
    }
    return new MaybePath<>(Maybe.nothing());
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
   * @return a new path containing the combined result
   */
  public <B, C, D> MaybePath<D> zipWith3(
      MaybePath<B> second,
      MaybePath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (this.value.isJust() && second.value.isJust() && third.value.isJust()) {
      return new MaybePath<>(
          Maybe.just(combiner.apply(this.value.get(), second.value.get(), third.value.get())));
    }
    return new MaybePath<>(Maybe.nothing());
  }

  // ===== Chainable implementation =====

  @Override
  public <B> MaybePath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    if (value.isNothing()) {
      return new MaybePath<>(Maybe.nothing());
    }

    Chainable<B> result = mapper.apply(value.get());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof MaybePath<?> maybePath)) {
      throw new IllegalArgumentException(
          "via mapper must return MaybePath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    MaybePath<B> typedResult = (MaybePath<B>) maybePath;
    return typedResult;
  }

  @Override
  public <B> MaybePath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    if (value.isNothing()) {
      return new MaybePath<>(Maybe.nothing());
    }

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof MaybePath<?> maybePath)) {
      throw new IllegalArgumentException(
          "then supplier must return MaybePath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    MaybePath<B> typedResult = (MaybePath<B>) maybePath;
    return typedResult;
  }

  // ===== Recoverable implementation =====

  @Override
  public MaybePath<A> recover(Function<? super Unit, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isJust()) {
      return this;
    }
    return new MaybePath<>(Maybe.just(recovery.apply(Unit.INSTANCE)));
  }

  @Override
  public MaybePath<A> recoverWith(Function<? super Unit, ? extends Recoverable<Unit, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isJust()) {
      return this;
    }
    Recoverable<Unit, A> result = recovery.apply(Unit.INSTANCE);
    Objects.requireNonNull(result, "recovery must not return null");
    if (!(result instanceof MaybePath<?> maybePath)) {
      throw new IllegalArgumentException(
          "recovery must return MaybePath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    MaybePath<A> typedResult = (MaybePath<A>) maybePath;
    return typedResult;
  }

  @Override
  public MaybePath<A> orElse(Supplier<? extends Recoverable<Unit, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isJust()) {
      return this;
    }
    Recoverable<Unit, A> result = alternative.get();
    Objects.requireNonNull(result, "alternative must not return null");
    if (!(result instanceof MaybePath<?> maybePath)) {
      throw new IllegalArgumentException(
          "alternative must return MaybePath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    MaybePath<A> typedResult = (MaybePath<A>) maybePath;
    return typedResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E2> Recoverable<E2, A> mapError(Function<? super Unit, ? extends E2> mapper) {
    // For MaybePath, mapError is effectively a no-op since Unit is the only error type.
    // We return this cast to the new error type, which is safe because MaybePath
    // doesn't actually store errors (Nothing has no error value).
    Objects.requireNonNull(mapper, "mapper must not be null");
    return (Recoverable<E2, A>) this;
  }

  // ===== FocusPath Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside an effect context. Since FocusPath always focuses on exactly one element, the result is
   * always a successful navigation if the MaybePath contains a value.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * FocusPath<User, String> namePath = UserFocus.name();
   *
   * MaybePath<User> userPath = Path.just(user);
   * MaybePath<String> name = userPath.focus(namePath);
   * // Equivalent to: userPath.map(namePath::get)
   * }</pre>
   *
   * @param path the FocusPath to apply
   * @param <B> the focused type
   * @return a new MaybePath containing the focused value
   */
  public <B> MaybePath<B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the contained value.
   *
   * <p>This bridges from the effect domain to the optics domain. The result flattens the two
   * optional layers: if either the MaybePath is empty or the AffinePath doesn't match, the result
   * is Nothing.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<User, String> emailPath = UserFocus.optionalEmail();
   *
   * MaybePath<User> userPath = Path.just(user);
   * MaybePath<String> email = userPath.focus(emailPath);
   * // Returns Nothing if user has no email
   * }</pre>
   *
   * @param path the AffinePath to apply
   * @param <B> the focused type
   * @return a new MaybePath containing the focused value if both succeed
   */
  public <B> MaybePath<B> focus(AffinePath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return via(a -> path.getOptional(a).map(Path::just).orElseGet(Path::nothing));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof MaybePath<?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "MaybePath(" + value + ")";
  }
}
