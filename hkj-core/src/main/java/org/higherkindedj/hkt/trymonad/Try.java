// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that may either result in a value (a {@link Success}) or an exception (a
 * {@link Failure}). {@code Try} is a sum type designed to encapsulate operations that can throw
 * {@link Throwable}s, allowing for more functional error handling.
 *
 * <p>It is similar to Scala's {@code Try} or Haskell's {@code Either Exception a}. {@code Try} is
 * right-biased, meaning operations like {@link #map(Function)} and {@link #flatMap(Function)}
 * operate on the value of a {@link Success} and pass a {@link Failure} through unchanged.
 *
 * <p>This is a sealed interface, with {@link Success} and {@link Failure} as its only direct
 * implementations (provided as nested records). This facilitates exhaustive pattern matching using
 * `switch` expressions.
 *
 * <p>Primary use cases include:
 *
 * <ul>
 *   <li>Converting exception-throwing APIs into pure functional computations.
 *   <li>Sequencing operations where any step might fail, without deeply nested try-catch blocks.
 *   <li>Providing a clear path for recovery from failures.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * public Try<Integer> parseInt(String s) {
 * return Try.of(() -> Integer.parseInt(s));
 * }
 *
 * Try<Integer> result = parseInt("123");
 * result.fold(
 * error -> { System.err.println("Failed: " + error.getMessage()); return -1; },
 * value -> { System.out.println("Succeeded: " + value); return value; }
 * );
 *
 * Try<Integer> doubled = result.map(v -> v * 2); // Only maps if result was Success
 *
 * Try<String> recovered = parseInt("abc").recover(ex -> "Default Value"); // Will be Success("Default Value")
 *
 * // Using toEither:
 * Either<String, Integer> eitherResult = parseInt("123").toEither(Throwable::getMessage);
 * // eitherResult will be Right(123)
 *
 * Either<String, Integer> eitherFailure = parseInt("xyz").toEither(t -> "Invalid input: " + t.getMessage());
 * // eitherFailure will be Left("Invalid input: For input string: \"xyz\"")
 * }</pre>
 *
 * @param <T> The type of the value if the computation is successful.
 * @see Success
 * @see Failure
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {

  Class<Try> TRY_CLASS = Try.class;

  /**
   * Executes a {@link Supplier} that produces a value of type {@code T} and wraps the outcome in a
   * {@code Try}. If the supplier executes successfully, its result (which can be {@code null}) is
   * wrapped in a {@link Success}. If the supplier throws any {@link Throwable} (including {@link
   * Error}s and checked/unchecked exceptions), it is caught and wrapped in a {@link Failure}.
   *
   * <p>This is the most common way to create a {@code Try} instance from potentially failable code.
   *
   * @param supplier The non-null computation (supplier) to execute. The supplier itself may return
   *     {@code null}.
   * @param <T> The type of the result produced by the supplier.
   * @return A non-null {@link Success} instance containing the supplier's result if execution is
   *     normal, or a non-null {@link Failure} instance containing the {@link Throwable} if an
   *     exception occurs.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <T> Try<T> of(Supplier<? extends T> supplier) {
    Validation.function().requireFunction(supplier, "supplier", TRY_CLASS, OF);
    try {
      return new Success<>(supplier.get());
    } catch (Throwable t) {
      return new Failure<>(t);
    }
  }

  /**
   * Creates a {@code Try} representing a successful computation with the given value. The provided
   * value can be {@code null}, in which case it results in {@code Success(null)}.
   *
   * @param value The successful value. Can be {@code null}.
   * @param <T> The type of the value.
   * @return A non-null {@link Success} instance holding the provided value.
   */
  static <T> Try<T> success(@Nullable T value) {
    return new Success<>(value);
  }

  /**
   * Creates a {@code Try} representing a failed computation due to the given {@link Throwable}.
   *
   * @param throwable The non-null {@link Throwable} that caused the failure.
   * @param <T> The phantom type of the value (since the computation failed to produce one).
   * @return A non-null {@link Failure} instance holding the {@link Throwable}.
   * @throws NullPointerException if {@code throwable} is null.
   */
  static <T> Try<T> failure(Throwable throwable) {
    Objects.requireNonNull(throwable, "Throwable for Failure cannot be null");
    return new Failure<>(throwable);
  }

  /**
   * Checks if this {@code Try} instance represents a successful computation (i.e., is a {@link
   * Success}).
   *
   * @return {@code true} if this is a {@link Success}, {@code false} otherwise.
   */
  boolean isSuccess();

  /**
   * Checks if this {@code Try} instance represents a failed computation (i.e., is a {@link
   * Failure}).
   *
   * @return {@code true} if this is a {@link Failure}, {@code false} otherwise.
   */
  boolean isFailure();

  /**
   * Retrieves the successful value if this is a {@link Success}. If this is a {@link Failure}, it
   * re-throws the original {@link Throwable} that caused the failure.
   *
   * <p><b>Caution:</b> This method breaks functional purity by potentially throwing an exception.
   * It's generally preferred to use methods like {@link #fold(Function, Function)}, {@link
   * #orElse(Object)}, {@link #recover(Function)}, or pattern matching to handle both success and
   * failure cases without throwing exceptions.
   *
   * @return The successful value (can be {@code null} if it was a {@code Success(null)}).
   * @throws Throwable if this is a {@link Failure}, the original exception is thrown.
   */
  @Nullable T get() throws Throwable;

  /**
   * Retrieves the successful value if this is a {@link Success}. If this is a {@link Failure}, it
   * returns the provided {@code other} value.
   *
   * @param other The alternative value to return if this is a {@link Failure}. Can be {@code null}
   *     if {@code T} is a nullable type.
   * @return The successful value if this is a {@link Success}, otherwise {@code other}.
   */
  @Nullable T orElse(@Nullable T other);

  /**
   * Retrieves the successful value if this is a {@link Success}. If this is a {@link Failure}, it
   * returns the value provided by the {@code supplier}. The supplier is only evaluated if this is a
   * {@link Failure}.
   *
   * @param supplier The non-null {@link Supplier} that provides an alternative value if this is a
   *     {@link Failure}.
   * @return The successful value if this is a {@link Success}, otherwise the result of {@code
   *     supplier.get()}.
   * @throws NullPointerException if {@code supplier} is null (but only if this is a {@link Failure}
   *     and the supplier needs to be invoked by the concrete implementation).
   */
  @Nullable T orElseGet(Supplier<? extends T> supplier);

  /**
   * Applies one of two functions depending on whether this is a {@link Success} or a {@link
   * Failure}. This allows for a complete handling of both outcomes, transforming them into a single
   * result of type {@code U}.
   *
   * @param successMapper The non-null function to apply if this is a {@link Success}.
   * @param failureMapper The non-null function to apply if this is a {@link Failure}.
   * @param <U> The target type to which both outcomes are mapped.
   * @return The result of applying the appropriate mapping function.
   * @throws NullPointerException if either {@code successMapper} or {@code failureMapper} is null.
   */
  default <U> U fold(
      Function<? super T, ? extends U> successMapper,
      Function<? super Throwable, ? extends U> failureMapper) {

    Validation.function().requireFunction(successMapper, "successMapper", TRY_CLASS, FOLD);
    Validation.function().requireFunction(failureMapper, "failureMapper", TRY_CLASS, FOLD);

    return switch (this) {
      case Success<T>(var value) -> successMapper.apply(value);
      case Failure<T>(var cause) -> failureMapper.apply(cause);
    };
  }

  /**
   * Converts this {@code Try} to an {@link Either}. If this is a {@link Success}, returns an {@code
   * Either.Right} containing the success value. If this is a {@link Failure}, applies the {@code
   * failureToLeftMapper} function to the {@link Throwable} and returns an {@code Either.Left}
   * containing the result.
   *
   * @param failureToLeftMapper A non-null function that maps the {@link Throwable} of a {@link
   *     Failure} to a value of type {@code L}.
   * @param <L> The type for the left side of the resulting {@code Either} (representing the error).
   * @return An {@code Either<L, T>} representing the outcome of this {@code Try}.
   * @throws NullPointerException if {@code failureToLeftMapper} is null.
   */
  default <L> Either<L, T> toEither(Function<? super Throwable, ? extends L> failureToLeftMapper) {
    Validation.function()
        .requireFunction(failureToLeftMapper, "failureToLeftMapper", TRY_CLASS, TO_EITHER);
    return switch (this) {
      case Success<T>(var value) -> Either.right(value);
      case Failure<T>(var cause) -> {
        L leftValue = failureToLeftMapper.apply(cause);
        Objects.requireNonNull(
            leftValue,
            "failureToLeftMapper returned null, which is not allowed for the left value of Either");
        yield Either.left(leftValue);
      }
    };
  }

  /**
   * If this is a {@link Success}, applies the given mapping function to its value. If the mapping
   * function itself throws a {@link Throwable}, the result is a new {@link Failure} containing that
   * {@link Throwable}. If this is a {@link Failure}, it returns the original {@link Failure}
   * instance unchanged.
   *
   * @param mapper The non-null function to apply to the successful value.
   * @param <U> The type of the value in the resulting {@link Try} if mapping is successful.
   * @return A new non-null {@code Try<U>} resulting from applying the mapper, or a {@link Failure}.
   * @throws NullPointerException if {@code mapper} is null (checked by implementations).
   */
  <U> Try<U> map(Function<? super T, ? extends U> mapper);

  /**
   * If this is a {@link Success}, applies the given {@code Try}-bearing function to its value and
   * returns the resulting {@code Try}. If the mapping function itself throws a {@link Throwable},
   * the result is a new {@link Failure} containing that {@link Throwable}. If this is a {@link
   * Failure}, it returns the original {@link Failure} instance unchanged.
   *
   * @param mapper The non-null function to apply to the successful value.
   * @param <U> The type parameter of the {@code Try} returned by the mapper.
   * @return The non-null {@code Try<U>} result from applying {@code mapper}, or a {@link Failure}.
   * @throws NullPointerException if {@code mapper} is null, or if {@code mapper} returns null
   *     (checked by implementations).
   */
  <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper);

  /**
   * If this is a {@link Failure}, applies the given recovery function to the {@link Throwable}. If
   * the recovery function successfully produces a value of type {@code T}, a {@link Success}
   * containing this value is returned. If the recovery function itself throws a {@link Throwable},
   * a new {@link Failure} containing this new {@link Throwable} is returned. If this is a {@link
   * Success}, it returns the original {@link Success} instance unchanged.
   *
   * @param recoveryFunction The non-null function to apply to the {@link Throwable} in case of a
   *     {@link Failure}.
   * @return A non-null {@code Try<T>} which is either the original {@link Success}, a new {@link
   *     Success} from recovery, or a new {@link Failure} if recovery also failed.
   * @throws NullPointerException if {@code recoveryFunction} is null (checked by implementations).
   */
  Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction);

  /**
   * If this is a {@link Failure}, applies the given {@code Try}-bearing recovery function to the
   * {@link Throwable}. This allows for a recovery path that itself might result in a {@link
   * Success} or a {@link Failure}. If the recovery function throws a {@link Throwable}, or returns
   * a {@code null} {@code Try}, the result is a new {@link Failure}. If this is a {@link Success},
   * it returns the original {@link Success} instance unchanged.
   *
   * @param recoveryFunction The non-null function to apply to the {@link Throwable}.
   * @return A non-null {@code Try<T>} which is either the original {@link Success}, the {@code Try}
   *     returned by the {@code recoveryFunction}, or a new {@link Failure} if recovery also failed
   *     or returned null.
   * @throws NullPointerException if {@code recoveryFunction} is null, or if it returns null
   *     (checked by implementations).
   */
  Try<T> recoverWith(Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction);

  /**
   * Performs one of two actions depending on whether this is a {@link Success} or a {@link
   * Failure}, using pattern matching. This is primarily for side effects.
   *
   * @param successAction The non-null action to perform if this is a {@link Success}.
   * @param failureAction The non-null action to perform if this is a {@link Failure}.
   * @throws NullPointerException if either {@code successAction} or {@code failureAction} is null.
   */
  default void match(Consumer<? super T> successAction, Consumer<? super Throwable> failureAction) {
    Validation.function().requireFunction(successAction, "successAction", TRY_CLASS, MATCH);
    Validation.function().requireFunction(failureAction, "failureAction", TRY_CLASS, MATCH);

    switch (this) {
      case Success<T>(var value) -> {
        try {
          successAction.accept(value);
        } catch (Throwable t) {
          System.err.println("Exception in Try.Success successAction: " + t.getMessage());
        }
      }
      case Failure<T>(var cause) -> {
        try {
          failureAction.accept(cause);
        } catch (Throwable t) {
          System.err.println("Exception in Try.Failure failureAction: " + t.getMessage());
        }
      }
    }
  }

  /**
   * Represents a successful computation within a {@link Try}. It holds the resulting value of type
   * {@code T}, which can be {@code null}. This is a {@link Record} for conciseness and
   * immutability.
   *
   * @param <T> The type of the successful value.
   * @param value The successful value, which can be {@code null}.
   */
  record Success<T>(@Nullable T value) implements Try<T> {
    @Override
    public boolean isSuccess() {
      return true;
    }

    @Override
    public boolean isFailure() {
      return false;
    }

    @Override
    public @Nullable T get() {
      return value;
    }

    @Override
    public @Nullable T orElse(@Nullable T other) {
      return value;
    }

    @Override
    public @Nullable T orElseGet(Supplier<? extends T> supplier) {
      Validation.function().requireFunction(supplier, "supplier", Try.class, OR_ELSE_GET);
      return value;
    }

    @Override
    public <U> Try<U> map(Function<? super T, ? extends U> mapper) {
      Validation.function().requireMapper(mapper, "mapper", Try.class, MAP);
      try {
        return new Success<>(mapper.apply(value));
      } catch (Throwable t) {
        return new Failure<>(t);
      }
    }

    @Override
    public <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper) {
      Validation.function().requireFlatMapper(mapper, "mapper", Try.class, FLAT_MAP);
      Try<? extends U> result;
      try {
        result = mapper.apply(value);
      } catch (Throwable t) {
        return new Failure<>(t);
      }

      Validation.function().requireNonNullResult(result, "mapper", Try.class, FLAT_MAP, TRY_CLASS);
      @SuppressWarnings("unchecked")
      Try<U> typedResult = (Try<U>) result;
      return typedResult;
    }

    @Override
    public Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction) {
      Validation.function()
          .requireFunction(recoveryFunction, "recoveryFunction", Try.class, RECOVER_FUNCTION);
      return this;
    }

    @Override
    public Try<T> recoverWith(
        Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction) {
      Validation.function()
          .requireFunction(recoveryFunction, "recoveryFunction", Try.class, RECOVER_WITH);
      return this;
    }

    @Override
    public String toString() {
      return "Success(" + value + ")";
    }
  }

  /**
   * Represents a failed computation within a {@link Try}. It holds the non-null {@link Throwable}
   * that caused the failure. This is a {@link Record} for conciseness and immutability.
   *
   * @param <T> The phantom type of the value (as there is no successful value).
   * @param cause The non-null {@link Throwable} that caused the failure.
   */
  record Failure<T>(Throwable cause) implements Try<T> {

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public boolean isFailure() {
      return true;
    }

    @Override
    public @Nullable T get() throws Throwable {
      throw cause;
    }

    @Override
    public @Nullable T orElse(@Nullable T other) {
      return other;
    }

    @Override
    public @Nullable T orElseGet(Supplier<? extends T> supplier) {
      Validation.function().requireFunction(supplier, "supplier", Try.class, OR_ELSE_GET);
      return supplier.get();
    }

    @Override
    public <U> Try<U> map(Function<? super T, ? extends U> mapper) {
      Validation.function().requireMapper(mapper, "mapper", Try.class, MAP);
      @SuppressWarnings("unchecked")
      Try<U> self = (Try<U>) this;
      return self;
    }

    @Override
    public <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper) {
      Validation.function().requireFlatMapper(mapper, "mapper", Try.class, FLAT_MAP);
      @SuppressWarnings("unchecked")
      Try<U> self = (Try<U>) this;
      return self;
    }

    @Override
    public Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction) {
      Validation.function()
          .requireFunction(recoveryFunction, "recoveryFunction", Try.class, RECOVER);
      try {
        return new Success<>(recoveryFunction.apply(cause));
      } catch (Throwable t) {
        return new Failure<>(t);
      }
    }

    @Override
    public Try<T> recoverWith(
        Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction) {
      Validation.function()
          .requireFunction(recoveryFunction, "recoveryFunction", Try.class, RECOVER_WITH);
      Try<? extends T> result;
      try {
        result = recoveryFunction.apply(cause);
      } catch (Throwable t) {
        return new Failure<>(t);
      }
      Validation.function()
          .requireNonNullResult(result, "recoveryFunction", Try.class, RECOVER_WITH, Try.class);
      @SuppressWarnings("unchecked")
      Try<T> typedResult = (Try<T>) result;
      return typedResult;
    }

    @Override
    public String toString() {
      return "Failure(" + cause + ")";
    }
  }
}
