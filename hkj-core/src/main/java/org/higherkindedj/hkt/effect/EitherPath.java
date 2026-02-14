// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link Either} values.
 *
 * <p>{@code EitherPath} provides a chainable API for composing operations on values that may fail
 * with a typed error. It implements {@link Recoverable} with the left type {@code E} as the error
 * type.
 *
 * <h2>Creating EitherPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * EitherPath<Error, User> path = Path.right(user);
 * EitherPath<Error, User> error = Path.left(Error.notFound("User not found"));
 * EitherPath<Error, User> fromEither = Path.either(someEither);
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * EitherPath<Error, String> result = Path.right(userId)
 *     .via(id -> userService.findById(id))  // Returns EitherPath<Error, User>
 *     .map(User::getName)
 *     .via(name -> validateName(name));     // Returns EitherPath<Error, String>
 * }</pre>
 *
 * <h2>Error handling</h2>
 *
 * <pre>{@code
 * String name = Path.right(userId)
 *     .via(id -> userService.findById(id))
 *     .map(User::getName)
 *     .recover(error -> "Anonymous")
 *     .run()
 *     .getRight();
 * }</pre>
 *
 * @param <E> the type of the error (left value)
 * @param <A> the type of the success value (right value)
 */
public final class EitherPath<E, A> implements Recoverable<E, A> {

  private final Either<E, A> value;

  /**
   * Creates a new EitherPath wrapping the given Either.
   *
   * @param value the Either to wrap; must not be null
   */
  EitherPath(Either<E, A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying Either value.
   *
   * @return the wrapped Either
   */
  public Either<E, A> run() {
    return value;
  }

  /**
   * Returns the success value if present, otherwise returns the provided default.
   *
   * @param defaultValue the value to return if this path contains an error
   * @return the success value or the default
   */
  public A getOrElse(A defaultValue) {
    return value.fold(e -> defaultValue, a -> a);
  }

  /**
   * Returns the success value if present, otherwise invokes the supplier.
   *
   * @param supplier provides the default value; must not be null
   * @return the success value or the supplier's result
   * @throws NullPointerException if supplier is null
   */
  public A getOrElseGet(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return value.fold(e -> supplier.get(), a -> a);
  }

  /**
   * Folds both sides of this path into a single value.
   *
   * @param leftMapper the function to apply if this path contains an error; must not be null
   * @param rightMapper the function to apply if this path contains a value; must not be null
   * @param <B> the result type
   * @return the result of applying the appropriate function
   * @throws NullPointerException if either mapper is null
   */
  public <B> B fold(
      Function<? super E, ? extends B> leftMapper, Function<? super A, ? extends B> rightMapper) {
    Objects.requireNonNull(leftMapper, "leftMapper must not be null");
    Objects.requireNonNull(rightMapper, "rightMapper must not be null");
    return value.fold(leftMapper, rightMapper);
  }

  /**
   * Converts this EitherPath to a MaybePath, discarding the error.
   *
   * @return a MaybePath containing the value, or empty if this contains an error
   */
  public MaybePath<A> toMaybePath() {
    return value.fold(e -> new MaybePath<>(Maybe.nothing()), a -> new MaybePath<>(Maybe.just(a)));
  }

  /**
   * Converts this EitherPath to a TryPath.
   *
   * <p>If this path contains an error, the error mapper is used to create an exception.
   *
   * @param errorToException converts the error to an exception; must not be null
   * @return a TryPath representing this path's value or the exception
   * @throws NullPointerException if errorToException is null
   */
  public TryPath<A> toTryPath(Function<? super E, ? extends Throwable> errorToException) {
    Objects.requireNonNull(errorToException, "errorToException must not be null");
    return value.fold(
        e -> new TryPath<>(Try.failure(errorToException.apply(e))),
        a -> new TryPath<>(Try.success(a)));
  }

  /**
   * Swaps the left and right values.
   *
   * @return an EitherPath with left and right swapped
   */
  public EitherPath<A, E> swap() {
    return value.fold(
        e -> new EitherPath<>(Either.right(e)), a -> new EitherPath<>(Either.left(a)));
  }

  /**
   * Converts this EitherPath to a ValidationPath.
   *
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @return a ValidationPath with the same state
   * @throws NullPointerException if semigroup is null
   */
  public ValidationPath<E, A> toValidationPath(Semigroup<E> semigroup) {
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return value.fold(
        e -> new ValidationPath<>(Validated.invalid(e), semigroup),
        a -> new ValidationPath<>(Validated.valid(a), semigroup));
  }

  /**
   * Converts this EitherPath to an OptionalPath, discarding the error.
   *
   * @return an OptionalPath containing the value if Right, or empty if Left
   */
  public OptionalPath<A> toOptionalPath() {
    return value.fold(
        _ -> new OptionalPath<>(Optional.empty()), a -> new OptionalPath<>(Optional.of(a)));
  }

  // ===== Composable implementation =====

  @Override
  public <B> EitherPath<E, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new EitherPath<>(value.map(mapper));
  }

  @Override
  public EitherPath<E, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.ifRight(consumer);
    return this;
  }

  /**
   * Observes the error value without modifying it (for debugging).
   *
   * @param consumer the action to perform on the error; must not be null
   * @return this path unchanged
   * @throws NullPointerException if consumer is null
   */
  public EitherPath<E, A> peekLeft(Consumer<? super E> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.ifLeft(consumer);
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> EitherPath<E, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof EitherPath<?, ?> otherEither)) {
      throw new IllegalArgumentException("Cannot zipWith non-EitherPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    EitherPath<E, B> typedOther = (EitherPath<E, B>) otherEither;

    return this.value.fold(
        e -> new EitherPath<>(Either.left(e)),
        a ->
            typedOther.value.fold(
                e -> new EitherPath<>(Either.left(e)),
                b -> new EitherPath<>(Either.right(combiner.apply(a, b)))));
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
   * @return a new path containing the combined result, or the first error encountered
   */
  public <B, C, D> EitherPath<E, D> zipWith3(
      EitherPath<E, B> second,
      EitherPath<E, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return this.value.fold(
        e -> new EitherPath<>(Either.left(e)),
        a ->
            second.value.fold(
                e -> new EitherPath<>(Either.left(e)),
                b ->
                    third.value.fold(
                        e -> new EitherPath<>(Either.left(e)),
                        c -> new EitherPath<>(Either.right(combiner.apply(a, b, c))))));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> EitherPath<E, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    if (value.isLeft()) {
      @SuppressWarnings("unchecked")
      EitherPath<E, B> result = (EitherPath<E, B>) this;
      return result;
    }

    Chainable<B> result = mapper.apply(value.getRight());
    Objects.requireNonNull(result, "mapper must not return null");

    if (!(result instanceof EitherPath<?, ?> eitherPath)) {
      throw new IllegalArgumentException(
          "via mapper must return EitherPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    EitherPath<E, B> typedResult = (EitherPath<E, B>) eitherPath;
    return typedResult;
  }

  @Override
  public <B> EitherPath<E, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    if (value.isLeft()) {
      @SuppressWarnings("unchecked")
      EitherPath<E, B> result = (EitherPath<E, B>) this;
      return result;
    }

    Chainable<B> result = supplier.get();
    Objects.requireNonNull(result, "supplier must not return null");

    if (!(result instanceof EitherPath<?, ?> eitherPath)) {
      throw new IllegalArgumentException(
          "then supplier must return EitherPath, got: " + result.getClass());
    }

    @SuppressWarnings("unchecked")
    EitherPath<E, B> typedResult = (EitherPath<E, B>) eitherPath;
    return typedResult;
  }

  // ===== Recoverable implementation =====

  @Override
  public EitherPath<E, A> recover(Function<? super E, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isRight()) {
      return this;
    }
    return new EitherPath<>(Either.right(recovery.apply(value.getLeft())));
  }

  @Override
  public EitherPath<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    if (value.isRight()) {
      return this;
    }
    Recoverable<E, A> result = recovery.apply(value.getLeft());
    Objects.requireNonNull(result, "recovery must not return null");
    if (!(result instanceof EitherPath<?, ?> eitherPath)) {
      throw new IllegalArgumentException(
          "recovery must return EitherPath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherPath<E, A> typedResult = (EitherPath<E, A>) eitherPath;
    return typedResult;
  }

  @Override
  public EitherPath<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isRight()) {
      return this;
    }
    Recoverable<E, A> result = alternative.get();
    Objects.requireNonNull(result, "alternative must not return null");
    if (!(result instanceof EitherPath<?, ?> eitherPath)) {
      throw new IllegalArgumentException(
          "alternative must return EitherPath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherPath<E, A> typedResult = (EitherPath<E, A>) eitherPath;
    return typedResult;
  }

  @Override
  public <E2> EitherPath<E2, A> mapError(Function<? super E, ? extends E2> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new EitherPath<>(value.mapLeft(mapper));
  }

  // ===== FocusPath Bridge Methods =====

  /**
   * Applies a {@link FocusPath} to navigate within the Right value.
   *
   * <p>This bridges from the effect domain to the optics domain, allowing structural navigation
   * inside an effect context. Since FocusPath always focuses on exactly one element, the result is
   * always a successful navigation if this EitherPath contains a Right value.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * FocusPath<User, String> namePath = UserFocus.name();
   *
   * EitherPath<Error, User> userPath = Path.right(user);
   * EitherPath<Error, String> name = userPath.focus(namePath);
   * // Equivalent to: userPath.map(namePath::get)
   * }</pre>
   *
   * @param path the FocusPath to apply
   * @param <B> the focused type
   * @return a new EitherPath containing the focused value
   */
  public <B> EitherPath<E, B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the Right value.
   *
   * <p>This bridges from the effect domain to the optics domain. If the AffinePath doesn't match, a
   * Left is returned with the provided error value. This allows converting partial optics failures
   * to typed errors.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<User, String> emailPath = UserFocus.optionalEmail();
   *
   * EitherPath<Error, User> userPath = Path.right(user);
   * EitherPath<Error, String> email = userPath.focus(emailPath, Error.of("Email not found"));
   * // Returns Left(Error.of("Email not found")) if user has no email
   * }</pre>
   *
   * @param path the AffinePath to apply
   * @param errorIfAbsent the error to use if the path doesn't match
   * @param <B> the focused type
   * @return a new EitherPath containing the focused value or the error
   */
  public <B> EitherPath<E, B> focus(AffinePath<A, B> path, E errorIfAbsent) {
    Objects.requireNonNull(path, "path must not be null");
    return via(
        a ->
            path.getOptional(a)
                .<EitherPath<E, B>>map(Path::right)
                .orElseGet(() -> Path.left(errorIfAbsent)));
  }

  // ===== Lifting Methods =====

  /**
   * Lifts this Either into a deferred IO context.
   *
   * <p>The Either value is captured immediately but wrapped in IO for composition with other
   * IO-based operations. This is useful when you need to combine synchronous Either results with
   * deferred IO computations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * EitherPath<Error, User> validated = validateUser(input);
   * IOPath<Either<Error, User>> deferred = validated.liftIO();
   *
   * // Now compose with other IO operations
   * IOPath<Either<Error, Profile>> result = deferred.map(either ->
   *     either.map(user -> enrichUser(user)));
   * }</pre>
   *
   * @return an IOPath containing this Either
   */
  public IOPath<Either<E, A>> liftIO() {
    Either<E, A> captured = this.value;
    return new IOPath<>(IO.delay(() -> captured));
  }

  /**
   * Lifts this Either into an async CompletableFuture context.
   *
   * <p>Returns an already-completed future containing this Either. This is useful when you need to
   * combine synchronous Either results with async operations.
   *
   * @return a CompletableFuturePath containing this Either
   */
  public CompletableFuturePath<Either<E, A>> liftFuture() {
    return new CompletableFuturePath<>(CompletableFuture.completedFuture(this.value));
  }

  /**
   * Creates a deferred IO computation that produces an Either.
   *
   * <p>The supplier is not invoked until the IO is executed. This is useful for deferring
   * computations that may fail with typed errors.
   *
   * <p>Example:
   *
   * <pre>{@code
   * IOPath<Either<ApiError, User>> userIO = EitherPath.deferIO(() ->
   *     userService.fetch(userId)
   *         .mapError(ApiError::fromException));
   *
   * // Computation is deferred until:
   * Either<ApiError, User> result = userIO.unsafeRun();
   * }</pre>
   *
   * @param supplier the deferred computation; must not be null
   * @param <E> the error type
   * @param <A> the success type
   * @return an IOPath that will produce the Either when run
   * @throws NullPointerException if supplier is null
   */
  public static <E, A> IOPath<Either<E, A>> deferIO(Supplier<Either<E, A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new IOPath<>(IO.delay(supplier));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EitherPath<?, ?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "EitherPath(" + value + ")";
  }
}
