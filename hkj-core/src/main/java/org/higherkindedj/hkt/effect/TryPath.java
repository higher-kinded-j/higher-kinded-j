// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
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
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A fluent path wrapper for {@link Try} values.
 *
 * <p>{@code TryPath} provides a chainable API for composing operations that may throw exceptions.
 * It implements {@link Recoverable} with {@link Throwable} as the error type.
 *
 * <h2>Creating TryPath instances</h2>
 *
 * <p>Use the {@link Path} factory class to create instances:
 *
 * <pre>{@code
 * TryPath<String> path = Path.tryOf(() -> Files.readString(file));
 * TryPath<Integer> success = Path.success(42);
 * TryPath<Integer> failure = Path.failure(new IOException("File not found"));
 * }</pre>
 *
 * <h2>Composing operations</h2>
 *
 * <pre>{@code
 * TryPath<Config> config = Path.tryOf(() -> readConfigFile())
 *     .map(Config::parse)
 *     .via(c -> Path.tryOf(() -> validate(c)));
 * }</pre>
 *
 * <h2>Exception handling</h2>
 *
 * <pre>{@code
 * String content = Path.tryOf(() -> Files.readString(path))
 *     .recover(ex -> "default content")
 *     .run()
 *     .get();
 * }</pre>
 *
 * @param <A> the type of the success value
 */
public final class TryPath<A> implements Recoverable<Throwable, A> {

  private final Try<A> value;

  /**
   * Creates a new TryPath wrapping the given Try.
   *
   * @param value the Try to wrap; must not be null
   */
  TryPath(Try<A> value) {
    this.value = Objects.requireNonNull(value, "value must not be null");
  }

  /**
   * Returns the underlying Try value.
   *
   * @return the wrapped Try
   */
  public Try<A> run() {
    return value;
  }

  /**
   * Returns the success value if present, otherwise returns the provided default.
   *
   * @param defaultValue the value to return if this path contains a failure
   * @return the success value or the default
   */
  public A getOrElse(A defaultValue) {
    return value.orElse(defaultValue);
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
    return value.orElseGet(supplier);
  }

  /**
   * Folds both cases of this path into a single value.
   *
   * @param successMapper the function to apply if this path contains a value; must not be null
   * @param failureMapper the function to apply if this path contains an exception; must not be null
   * @param <B> the result type
   * @return the result of applying the appropriate function
   * @throws NullPointerException if either mapper is null
   */
  public <B> B fold(
      Function<? super A, ? extends B> successMapper,
      Function<? super Throwable, ? extends B> failureMapper) {
    Objects.requireNonNull(successMapper, "successMapper must not be null");
    Objects.requireNonNull(failureMapper, "failureMapper must not be null");
    return value.fold(successMapper, failureMapper);
  }

  /**
   * Converts this TryPath to a MaybePath, discarding the exception.
   *
   * @return a MaybePath containing the value, or empty if this contains an exception
   */
  public MaybePath<A> toMaybePath() {
    return value.fold(a -> new MaybePath<>(Maybe.just(a)), ex -> new MaybePath<>(Maybe.nothing()));
  }

  /**
   * Converts this TryPath to an EitherPath.
   *
   * <p>The exception is transformed using the provided function to create the error type.
   *
   * @param exceptionToError converts the exception to an error; must not be null
   * @param <E> the error type
   * @return an EitherPath representing this path's value or the transformed error
   * @throws NullPointerException if exceptionToError is null
   */
  public <E> EitherPath<E, A> toEitherPath(
      Function<? super Throwable, ? extends E> exceptionToError) {
    Objects.requireNonNull(exceptionToError, "exceptionToError must not be null");
    return value.fold(
        a -> new EitherPath<>(Either.right(a)),
        ex -> new EitherPath<>(Either.left(exceptionToError.apply(ex))));
  }

  /**
   * Converts this TryPath to a ValidationPath.
   *
   * <p>The exception is transformed using the provided function to create the error type.
   *
   * @param exceptionToError converts the exception to an error; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @return a ValidationPath representing this path's value or the transformed error
   * @throws NullPointerException if either argument is null
   */
  public <E> ValidationPath<E, A> toValidationPath(
      Function<? super Throwable, ? extends E> exceptionToError, Semigroup<E> semigroup) {
    Objects.requireNonNull(exceptionToError, "exceptionToError must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return value.fold(
        a -> new ValidationPath<>(Validated.valid(a), semigroup),
        ex -> new ValidationPath<>(Validated.invalid(exceptionToError.apply(ex)), semigroup));
  }

  /**
   * Converts this TryPath to an OptionalPath, discarding the exception.
   *
   * @return an OptionalPath containing the value if Success, or empty if Failure
   */
  public OptionalPath<A> toOptionalPath() {
    return value.fold(
        a -> new OptionalPath<>(Optional.of(a)), _ -> new OptionalPath<>(Optional.empty()));
  }

  // ===== Composable implementation =====

  @Override
  public <B> TryPath<B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new TryPath<>(value.map(mapper));
  }

  @Override
  public TryPath<A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    if (value.isSuccess()) {
      try {
        consumer.accept(value.get());
      } catch (Throwable ignored) {
        // peek should not modify the path even if consumer throws
      }
    }
    return this;
  }

  /**
   * Observes the failure without modifying it (for debugging).
   *
   * @param consumer the action to perform on the exception; must not be null
   * @return this path unchanged
   * @throws NullPointerException if consumer is null
   */
  public TryPath<A> peekFailure(Consumer<? super Throwable> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    value.fold(
        a -> null, // Success case - do nothing
        ex -> {
          consumer.accept(ex);
          return null;
        });
    return this;
  }

  // ===== Combinable implementation =====

  @Override
  public <B, C> TryPath<C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof TryPath<?> otherTry)) {
      throw new IllegalArgumentException("Cannot zipWith non-TryPath: " + other.getClass());
    }

    @SuppressWarnings("unchecked")
    TryPath<B> typedOther = (TryPath<B>) otherTry;

    return this.value.fold(
        a ->
            typedOther.value.fold(
                b -> new TryPath<>(Try.of(() -> combiner.apply(a, b))),
                ex -> new TryPath<>(Try.failure(ex))),
        ex -> new TryPath<>(Try.failure(ex)));
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
   * @return a new path containing the combined result, or the first failure encountered
   */
  public <B, C, D> TryPath<D> zipWith3(
      TryPath<B> second,
      TryPath<C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    return this.value.fold(
        a ->
            second.value.fold(
                b ->
                    third.value.fold(
                        c -> new TryPath<>(Try.of(() -> combiner.apply(a, b, c))),
                        ex -> new TryPath<>(Try.failure(ex))),
                ex -> new TryPath<>(Try.failure(ex))),
        ex -> new TryPath<>(Try.failure(ex)));
  }

  // ===== Chainable implementation =====

  @Override
  public <B> TryPath<B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");

    if (value.isFailure()) {
      @SuppressWarnings("unchecked")
      TryPath<B> result = (TryPath<B>) this;
      return result;
    }

    try {
      A successValue = value.get();
      Chainable<B> result = mapper.apply(successValue);
      Objects.requireNonNull(result, "mapper must not return null");

      if (!(result instanceof TryPath<?> tryPath)) {
        throw new IllegalArgumentException(
            "via mapper must return TryPath, got: " + result.getClass());
      }

      @SuppressWarnings("unchecked")
      TryPath<B> typedResult = (TryPath<B>) tryPath;
      return typedResult;
    } catch (Throwable ex) {
      return new TryPath<>(Try.failure(ex));
    }
  }

  @Override
  public <B> TryPath<B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    if (value.isFailure()) {
      @SuppressWarnings("unchecked")
      TryPath<B> result = (TryPath<B>) this;
      return result;
    }

    try {
      Chainable<B> result = supplier.get();
      Objects.requireNonNull(result, "supplier must not return null");

      if (!(result instanceof TryPath<?> tryPath)) {
        throw new IllegalArgumentException(
            "then supplier must return TryPath, got: " + result.getClass());
      }

      @SuppressWarnings("unchecked")
      TryPath<B> typedResult = (TryPath<B>) tryPath;
      return typedResult;
    } catch (Throwable ex) {
      return new TryPath<>(Try.failure(ex));
    }
  }

  // ===== Recoverable implementation =====

  @Override
  public TryPath<A> recover(Function<? super Throwable, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return new TryPath<>(value.recover(recovery));
  }

  @Override
  public TryPath<A> recoverWith(
      Function<? super Throwable, ? extends Recoverable<Throwable, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return value.fold(
        a -> this,
        ex -> {
          Recoverable<Throwable, A> result = recovery.apply(ex);
          Objects.requireNonNull(result, "recovery must not return null");
          if (!(result instanceof TryPath<?> tryPath)) {
            throw new IllegalArgumentException(
                "recovery must return TryPath, got: " + result.getClass());
          }
          @SuppressWarnings("unchecked")
          TryPath<A> typedResult = (TryPath<A>) tryPath;
          return typedResult;
        });
  }

  @Override
  public TryPath<A> orElse(Supplier<? extends Recoverable<Throwable, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    if (value.isSuccess()) {
      return this;
    }
    Recoverable<Throwable, A> result = alternative.get();
    Objects.requireNonNull(result, "alternative must not return null");
    if (!(result instanceof TryPath<?> tryPath)) {
      throw new IllegalArgumentException(
          "alternative must return TryPath, got: " + result.getClass());
    }
    @SuppressWarnings("unchecked")
    TryPath<A> typedResult = (TryPath<A>) tryPath;
    return typedResult;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E2> Recoverable<E2, A> mapError(Function<? super Throwable, ? extends E2> mapper) {
    // TryPath uses Throwable as a fixed error type. mapError transforms the error type,
    // but for TryPath we can't actually change the underlying Throwable.
    // We return this cast to the new error type, which is a limitation of the type system.
    Objects.requireNonNull(mapper, "mapper must not be null");
    return (Recoverable<E2, A>) this;
  }

  /**
   * Transforms the exception in a failure.
   *
   * <p>If this path is a failure, applies the function to transform the exception into a new
   * exception. If this path is a success, returns unchanged.
   *
   * @param mapper the function to transform the exception; must not be null
   * @return a TryPath with the transformed exception
   * @throws NullPointerException if mapper is null
   */
  public TryPath<A> mapException(Function<? super Throwable, ? extends Throwable> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return value.fold(a -> this, ex -> new TryPath<>(Try.failure(mapper.apply(ex))));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TryPath<?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "TryPath(" + value + ")";
  }
}
