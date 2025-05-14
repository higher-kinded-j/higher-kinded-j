package org.higherkindedj.hkt.trymonad;

import static java.util.Objects.requireNonNull;

import org.higherkindedj.hkt.either.Either;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
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
 * <li>Converting exception-throwing APIs into pure functional computations.
 * <li>Sequencing operations where any step might fail, without deeply nested try-catch blocks.
 * <li>Providing a clear path for recovery from failures.
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

  /**
   * Executes a {@link Supplier} that produces a value of type {@code T} and wraps the outcome in a
   * {@code Try}. If the supplier executes successfully, its result (which can be {@code null}) is
   * wrapped in a {@link Success}. If the supplier throws any {@link Throwable} (including {@link
   * Error}s and checked/unchecked exceptions), it is caught and wrapped in a {@link Failure}.
   *
   * <p>This is the most common way to create a {@code Try} instance from potentially failable code.
   *
   * @param supplier The non-null computation (supplier) to execute. The supplier itself may return
   * {@code null}.
   * @param <T> The type of the result produced by the supplier.
   * @return A non-null {@link Success} instance containing the supplier's result if execution is
   * normal, or a non-null {@link Failure} instance containing the {@link Throwable} if an
   * exception occurs.
   * @throws NullPointerException if {@code supplier} is null.
   */
  static <T> @NonNull Try<T> of(@NonNull Supplier<? extends T> supplier) {
    requireNonNull(supplier, "Supplier cannot be null");
    try {
      // The result from supplier.get() can be null, and Success can hold a null value.
      return new Success<>(supplier.get());
    } catch (Throwable t) {
      // Catch all Throwables, including Errors, as per typical Try semantics.
      return new Failure<>(t); // Failure requires a non-null Throwable.
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
  static <T> @NonNull Try<T> success(@Nullable T value) {
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
  static <T> @NonNull Try<T> failure(@NonNull Throwable throwable) {
    requireNonNull(throwable, "Throwable for Failure cannot be null");
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
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> success = Try.success(10);
   * int v1 = success.orElse(0); // v1 is 10
   *
   * Try<Integer> failure = Try.failure(new RuntimeException("boom"));
   * int v2 = failure.orElse(0); // v2 is 0
   * }</pre>
   *
   * @param other The alternative value to return if this is a {@link Failure}. Can be {@code null}
   * if {@code T} is a nullable type.
   * @return The successful value if this is a {@link Success}, otherwise {@code other}. The
   * nullability of the result depends on {@code T} and the nullability of {@code other}.
   */
  @Nullable T orElse(@Nullable T other);

  /**
   * Retrieves the successful value if this is a {@link Success}. If this is a {@link Failure}, it
   * returns the value provided by the {@code supplier}. The supplier is only evaluated if this is a
   * {@link Failure}.
   *
   * @param supplier The non-null {@link Supplier} that provides an alternative value if this is a
   * {@link Failure}. The supplier may return {@code null} if {@code T} is nullable.
   * @return The successful value if this is a {@link Success}, otherwise the result of {@code
   * supplier.get()}. The nullability depends on {@code T} and the result of the supplier.
   * @throws NullPointerException if {@code supplier} is null (but only if this is a {@link Failure}
   * and the supplier needs to be invoked by the concrete implementation).
   */
  @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier);

  /**
   * Applies one of two functions depending on whether this is a {@link Success} or a {@link
   * Failure}. This allows for a complete handling of both outcomes, transforming them into a single
   * result of type {@code U}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try<Integer> computation = Try.of(() -> 10 / 0); // Failure
   * String message = computation.fold(
   * value -> "Result: " + value,
   * error -> "Error: " + error.getClass().getSimpleName()
   * );
   * // message will be "Error: ArithmeticException"
   * }</pre>
   *
   * @param successMapper The non-null function to apply if this is a {@link Success}. It accepts
   * the value of type {@code T} and returns a {@code U}.
   * @param failureMapper The non-null function to apply if this is a {@link Failure}. It accepts
   * the {@link Throwable} and returns a {@code U}.
   * @param <U> The target type to which both outcomes are mapped.
   * @return The result of applying the appropriate mapping function. The result's nullability
   * depends on the nullability of the results from the mappers.
   * @throws NullPointerException if either {@code successMapper} or {@code failureMapper} is null.
   */
  default <U> U fold(
      @NonNull Function<? super T, ? extends U> successMapper,
      @NonNull Function<? super Throwable, ? extends U> failureMapper) {
    requireNonNull(successMapper, "successMapper cannot be null");
    requireNonNull(failureMapper, "failureMapper cannot be null");

    return switch (this) {
      case Success<T>(var value) -> successMapper.apply(value);
      case Failure<T>(var cause) -> failureMapper.apply(cause);
    };
  }

  /**
   * Converts this {@code Try} to an {@link Either}.
   * If this is a {@link Success}, returns an {@code Either.Right} containing the success value.
   * If this is a {@link Failure}, applies the {@code failureToLeftMapper} function to the {@link Throwable}
   * and returns an {@code Either.Left} containing the result.
   *
   * <p>This method is useful for integrating {@code Try}-based computations into an {@code Either}-based
   * error handling flow, allowing for a specific mapping of exceptions to a chosen left type.
   *
   * <p>Example:
   * <pre>{@code
   * Try<Integer> successfulParse = Try.of(() -> Integer.parseInt("123"));
   * // successfulParse.toEither(Throwable::getMessage) will be Right(123)
   *
   * Try<Integer> failedParse = Try.of(() -> Integer.parseInt("abc"));
   * // failedParse.toEither(ex -> "Parse Error: " + ex.getMessage()) will be Left("Parse Error: For input string: \"abc\"")
   * }</pre>
   *
   * @param failureToLeftMapper A non-null function that maps the {@link Throwable} of a {@link Failure}
   * to a value of type {@code L}, which will be the left type of the resulting {@code Either}.
   * This function must not return {@code null}.
   * @param <L> The type for the left side of the resulting {@code Either} (representing the error).
   * @return An {@code Either<L, T>} representing the outcome of this {@code Try}.
   * @throws NullPointerException if {@code failureToLeftMapper} is null.
   */
  default <L> @NonNull Either<L, T> toEither(
      @NonNull Function<? super Throwable, ? extends L> failureToLeftMapper) {
    requireNonNull(failureToLeftMapper, "failureToLeftMapper cannot be null");
    return switch (this) {
      case Success<T>(var value) -> Either.<L, T>right(value);
      case Failure<T>(var cause) -> {
        L leftValue = failureToLeftMapper.apply(cause);
        requireNonNull(leftValue, "failureToLeftMapper returned null, which is not allowed for the left value of Either.");
        yield Either.<L, T>left(leftValue);
      }
    };
  }


  /**
   * If this is a {@link Success}, applies the given mapping function to its value. If the mapping
   * function itself throws a {@link Throwable}, the result is a new {@link Failure} containing that
   * {@link Throwable}. If this is a {@link Failure}, it returns the original {@link Failure}
   * instance unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try.success(5).map(i -> i * 2); // Success(10)
   * Try.success("text").map(s -> s.charAt(10)); // Will be Failure(StringIndexOutOfBoundsException)
   * Try.failure(new Exception("err")).map(x -> x); // Failure(Exception("err"))
   * }</pre>
   *
   * @param mapper The non-null function to apply to the successful value.
   * @param <U> The type of the value in the resulting {@link Try} if mapping is successful.
   * @return A new non-null {@code Try<U>} resulting from applying the mapper, or a {@link Failure}.
   * @throws NullPointerException if {@code mapper} is null (checked by implementations).
   */
  @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper);

  /**
   * If this is a {@link Success}, applies the given {@code Try}-bearing function to its value and
   * returns the resulting {@code Try}. If the mapping function itself throws a {@link Throwable},
   * the result is a new {@link Failure} containing that {@link Throwable}. If this is a {@link
   * Failure}, it returns the original {@link Failure} instance unchanged.
   *
   * <p>This is the monadic bind operation, essential for sequencing failable computations.
   *
   * @param mapper The non-null function to apply to the successful value. This function must return
   * a non-null {@code Try<? extends U>}.
   * @param <U> The type parameter of the {@code Try} returned by the mapper.
   * @return The non-null {@code Try<U>} result from applying {@code mapper}, or a {@link Failure}.
   * @throws NullPointerException if {@code mapper} is null, or if {@code mapper} returns null
   * (checked by implementations).
   */
  @NonNull <U> Try<U> flatMap(
      @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper);

  /**
   * If this is a {@link Failure}, applies the given recovery function to the {@link Throwable}. If
   * the recovery function successfully produces a value of type {@code T}, a {@link Success}
   * containing this value is returned. If the recovery function itself throws a {@link Throwable},
   * a new {@link Failure} containing this new {@link Throwable} is returned. If this is a {@link
   * Success}, it returns the original {@link Success} instance unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Try.failure(new NumberFormatException()).recover(ex -> 0); // Success(0)
   * Try.failure(new RuntimeException()).recover(ex -> { throw new IllegalStateException(ex); }); // Failure(IllegalStateException)
   * Try.success(10).recover(ex -> 0); // Success(10)
   * }</pre>
   *
   * @param recoveryFunction The non-null function to apply to the {@link Throwable} in case of a
   * {@link Failure}. It should produce a value of type {@code T}.
   * @return A non-null {@code Try<T>} which is either the original {@link Success}, a new {@link
   * Success} from recovery, or a new {@link Failure} if recovery also failed.
   * @throws NullPointerException if {@code recoveryFunction} is null (checked by implementations).
   */
  @NonNull Try<T> recover(@NonNull Function<? super Throwable, ? extends T> recoveryFunction);

  /**
   * If this is a {@link Failure}, applies the given {@code Try}-bearing recovery function to the
   * {@link Throwable}. This allows for a recovery path that itself might result in a {@link
   * Success} or a {@link Failure}. If the recovery function throws a {@link Throwable}, or returns
   * a {@code null} {@code Try}, the result is a new {@link Failure}. If this is a {@link Success},
   * it returns the original {@link Success} instance unchanged.
   *
   * @param recoveryFunction The non-null function to apply to the {@link Throwable}. This function
   * must return a non-null {@code Try<? extends T>}.
   * @return A non-null {@code Try<T>} which is either the original {@link Success}, the {@code Try}
   * returned by the {@code recoveryFunction}, or a new {@link Failure} if recovery also failed
   * or returned null.
   * @throws NullPointerException if {@code recoveryFunction} is null, or if it returns null
   * (checked by implementations).
   */
  @NonNull Try<T> recoverWith(
      @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>> recoveryFunction);

  /**
   * Performs one of two actions depending on whether this is a {@link Success} or a {@link
   * Failure}, using pattern matching. This is primarily for side effects.
   *
   * <p>Exceptions thrown by the consumer actions are caught and printed to {@code System.err} by
   * this default implementation. Subclasses might handle this differently. Consider using {@link
   * #fold(Function, Function)} for transformations.
   *
   * @param successAction The non-null action to perform if this is a {@link Success}.
   * @param failureAction The non-null action to perform if this is a {@link Failure}.
   * @throws NullPointerException if either {@code successAction} or {@code failureAction} is null.
   */
  default void match(
      @NonNull Consumer<? super T> successAction,
      @NonNull Consumer<? super Throwable> failureAction) {
    requireNonNull(successAction, "successAction cannot be null");
    requireNonNull(failureAction, "failureAction cannot be null");

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

    /**
     * {@inheritDoc}
     *
     * @return The successful value (can be {@code null}).
     */
    @Override
    public @Nullable T get() {
      return value;
    }

    @Override
    public @Nullable T orElse(@Nullable T other) {
      return value;
    }

    @Override
    public @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier) {
      requireNonNull(supplier, "supplier cannot be null");
      return value;
    }

    @Override
    public @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper cannot be null");
      try {
        return new Success<>(mapper.apply(value));
      } catch (Throwable t) {
        return new Failure<>(t); // Non-null Throwable required for Failure.
      }
    }

    @Override
    public @NonNull <U> Try<U> flatMap(
        @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper) {
      requireNonNull(mapper, "mapper cannot be null");
      Try<? extends U> result;
      try {
        result = mapper.apply(value);
      } catch (Throwable t) {
        return new Failure<>(t);
      }

      requireNonNull(result, "flatMap mapper returned a null Try instance, which is not allowed.");
      @SuppressWarnings("unchecked") // Safe due to type constraints and HKT needs.
      Try<U> typedResult = (Try<U>) result;
      return typedResult;
    }

    @Override
    public @NonNull Try<T> recover(
        @NonNull Function<? super Throwable, ? extends T> recoveryFunction) {
      requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this;
    }

    @Override
    public @NonNull Try<T> recoverWith(
        @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>>
            recoveryFunction) {
      requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this;
    }
  }

  /**
   * Represents a failed computation within a {@link Try}. It holds the non-null {@link Throwable}
   * that caused the failure. This is a {@link Record} for conciseness and immutability.
   *
   * @param <T> The phantom type of the value (as there is no successful value).
   * @param cause The non-null {@link Throwable} that caused the failure.
   */
  record Failure<T>(@NonNull Throwable cause) implements Try<T> {

    @Override
    public boolean isSuccess() {
      return false;
    }

    @Override
    public boolean isFailure() {
      return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws Throwable The original exception that caused this {@link Failure}.
     */
    @Override
    public @Nullable T get() throws Throwable {
      throw cause;
    }

    @Override
    public @Nullable T orElse(@Nullable T other) {
      return other;
    }

    @Override
    public @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier) {
      requireNonNull(supplier, "supplier cannot be null for orElseGet");
      return supplier.get();
    }

    @Override
    public @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper) {
      requireNonNull(mapper, "mapper cannot be null");
      // Map does nothing on Failure, just propagates the Failure with the new type U.
      @SuppressWarnings(
          "unchecked") // Safe cast as Failure propagates its cause irrespective of T/U.
      Try<U> self = (Try<U>) this;
      return self;
    }

    @Override
    public @NonNull <U> Try<U> flatMap(
        @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper) {
      requireNonNull(mapper, "mapper cannot be null");
      @SuppressWarnings("unchecked")
      Try<U> self = (Try<U>) this;
      return self;
    }

    @Override
    public @NonNull Try<T> recover(
        @NonNull Function<? super Throwable, ? extends T> recoveryFunction) {
      requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      try {
        // The result of recoveryFunction.apply(cause) can be null; Success<T> can hold null.
        return new Success<>(recoveryFunction.apply(cause));
      } catch (Throwable t) {
        return new Failure<>(t);
      }
    }

    @Override
    public @NonNull Try<T> recoverWith(
        @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>>
            recoveryFunction) {
      requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      Try<? extends T> result;
      try {
        result = recoveryFunction.apply(cause);
      } catch (Throwable t) {
        return new Failure<>(t);
      }
      // Defensive null check for the Try instance returned by the recovery function.
      requireNonNull(
          result, "recoverWith function returned a null Try instance, which is not allowed.");
      @SuppressWarnings("unchecked")
      Try<T> typedResult = (Try<T>) result;
      return typedResult;
    }
  }
}