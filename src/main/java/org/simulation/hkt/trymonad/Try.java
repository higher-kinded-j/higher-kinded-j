package org.simulation.hkt.trymonad;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents a computation that may either result in a value of type T or fail with a Throwable.
 * Similar to Scala's Try.
 *
 * @param <T> the type of the successful value
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {

  /**
   * Executes the given Supplier and returns the result wrapped in a Success, or catches any
   * Throwable and returns it wrapped in a Failure.
   *
   * @param supplier The computation to execute. (NonNull)
   * @param <T> The type of the result.
   * @return Success(result) if supplier completes normally, Failure(throwable) otherwise. (NonNull)
   */
  static <T> @NonNull Try<T> of(@NonNull Supplier<? extends T> supplier) {
    Objects.requireNonNull(supplier, "Supplier cannot be null");
    try {
      // Supplier result can be null, Success allows null
      return new Success<>(supplier.get());
    } catch (Throwable t) {
      // Catch all Throwables, including Errors
      return new Failure<>(t); // Failure requires non-null Throwable
    }
  }

  /**
   * Creates a Try representing a successful computation. Allows null values, though often non-null
   * is preferred.
   *
   * @param value The successful value. (Nullable)
   * @param <T> The type of the value.
   * @return A Success instance holding the value. (NonNull)
   */
  static <T> @NonNull Try<T> success(@Nullable T value) {
    return new Success<>(value);
  }

  /**
   * Creates a Try representing a failed computation.
   *
   * @param throwable The Throwable that caused the failure. Must not be null. (NonNull)
   * @param <T> The phantom type of the value (since it failed).
   * @return A Failure instance holding the Throwable. (NonNull)
   * @throws NullPointerException if throwable is null.
   */
  static <T> @NonNull Try<T> failure(@NonNull Throwable throwable) {
    Objects.requireNonNull(throwable, "Throwable for Failure cannot be null");
    return new Failure<>(throwable);
  }

  // Methods returning boolean don't usually need nullness annotations
  boolean isSuccess();

  boolean isFailure();

  /**
   * Gets the successful value. Throws the contained Throwable if this is a Failure.
   *
   * @return The successful value. (Nullability depends on T)
   * @throws Throwable if this is a Failure.
   */
  @Nullable T get() throws Throwable; // Can return null if Success(null)

  /**
   * Gets the successful value, or returns the 'other' value if this is a Failure.
   *
   * @param other The value to return in case of Failure. (Nullability depends on T)
   * @return The successful value or the alternative value. (Nullability depends on T and other)
   */
  @Nullable T orElse(@Nullable T other);

  /**
   * Gets the successful value, or returns the result of the supplier if this is a Failure.
   *
   * @param supplier Provides the alternative value in case of Failure. (NonNull)
   * @return The successful value or the supplied alternative value. (Nullability depends on T and
   *     supplier result)
   */
  @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier);

  /**
   * Applies one of the functions depending on whether this is a Success or Failure, using pattern
   * matching for switch (Java 21+).
   *
   * @param successMapper Function to apply if Success (NonNull).
   * @param failureMapper Function to apply if Failure (NonNull).
   * @param <U> The target type.
   * @return The result of the applied function.
   */
  default <U> U fold(
      @NonNull Function<? super T, ? extends U> successMapper,
      @NonNull Function<? super Throwable, ? extends U> failureMapper) {
    Objects.requireNonNull(successMapper, "successMapper cannot be null");
    Objects.requireNonNull(failureMapper, "failureMapper cannot be null");

    return switch (this) {
      // Use record patterns to match and extract value/cause
      case Success<T>(var value) -> successMapper.apply(value);
      case Failure<T>(var cause) -> failureMapper.apply(cause);
    };
  }

  /**
   * If Success, applies the function to the value. If the function throws, returns Failure. If
   * Failure, returns the original Failure.
   *
   * @param mapper Function to apply to the successful value. (NonNull)
   * @param <U> The type of the result of the mapping function.
   * @return A new Try potentially containing the mapped value or a Failure. (NonNull)
   */
  @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper);

  /**
   * If Success, applies the Try-bearing function to the value. If the function throws, returns
   * Failure. If Failure, returns the original Failure.
   *
   * @param mapper Function to apply to the successful value, returning a Try. (NonNull, returns
   *     NonNull Try)
   * @param <U> The type parameter of the Try returned by the mapper.
   * @return The result of applying the function if Success, or the original Failure. (NonNull)
   */
  @NonNull <U> Try<U> flatMap(
      @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper);

  /**
   * If Failure, applies the function to the Throwable to potentially recover. If the recovery
   * function throws, returns Failure containing the new Throwable. If Success, returns the original
   * Success.
   *
   * @param recoveryFunction Function to apply to the Throwable in case of Failure. (NonNull)
   * @return A Try potentially recovered from Failure, or the original Success. (NonNull)
   */
  @NonNull Try<T> recover(@NonNull Function<? super Throwable, ? extends T> recoveryFunction);

  /**
   * If Failure, applies the Try-bearing function to the Throwable to potentially recover. If the
   * recovery function throws or returns null, returns Failure. If Success, returns the original
   * Success.
   *
   * @param recoveryFunction Function to apply to the Throwable, returning a Try. (NonNull, returns
   *     NonNull Try)
   * @return A Try potentially recovered from Failure, or the original Success. (NonNull)
   */
  @NonNull Try<T> recoverWith(
      @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>> recoveryFunction);

  /**
   * If Success, performs the action on the value. Catches exceptions from the action. If Failure,
   * performs the action on the Throwable. Catches exceptions from the action. (Using pattern
   * matching switch)
   *
   * @param successAction Action for Success case (NonNull).
   * @param failureAction Action for Failure case (NonNull).
   */
  default void match(
      @NonNull Consumer<? super T> successAction,
      @NonNull Consumer<? super Throwable> failureAction) {
    Objects.requireNonNull(successAction, "successAction cannot be null");
    Objects.requireNonNull(failureAction, "failureAction cannot be null");

    switch (this) {
      case Success<T>(var value) -> {
        try {
          successAction.accept(value);
        } catch (Throwable t) {
          // Log or handle exception from consumer if needed
          System.err.println("Exception in Try.Success successAction: " + t.getMessage());
        }
      }
      case Failure<T>(var cause) -> {
        try {
          failureAction.accept(cause);
        } catch (Throwable t) {
          // Log or handle exception from consumer if needed
          System.err.println("Exception in Try.Failure failureAction: " + t.getMessage());
        }
      }
    }
  }

  /** Represents a successful computation. */
  record Success<T>(@Nullable T value) implements Try<T> { // value is Nullable
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
    public @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier) {
      return value;
    }

    @Override
    public @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      try {
        // Result of mapper can be null, Success allows null
        return new Success<>(mapper.apply(value));
      } catch (Throwable t) {
        return new Failure<>(t); // Failure requires NonNull throwable
      }
    }

    @Override
    public @NonNull <U> Try<U> flatMap(
        @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      Try<? extends U> result;
      try {
        // Apply the mapper function only inside the try-catch
        result = mapper.apply(value); // mapper is NonNull
      } catch (Throwable t) {
        // Catch exceptions thrown by the mapper function itself
        return new Failure<>(t); // Failure requires NonNull throwable
      }
      // Check if the *result* of the mapper is null *after* the try-catch
      Objects.requireNonNull(result, "flatMap mapper returned null Try");
      @SuppressWarnings("unchecked") // Safe due to HKT simulation needs
      Try<U> typedResult = (Try<U>) result;
      return typedResult;
    }

    @Override
    public @NonNull Try<T> recover(
        @NonNull Function<? super Throwable, ? extends T> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this; // Nothing to recover from
    }

    @Override
    public @NonNull Try<T> recoverWith(
        @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>>
            recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this; // Nothing to recover from
    }
  }

  /** Represents a failed computation. */
  record Failure<T>(@NonNull Throwable cause) implements Try<T> { // cause is NonNull
    // Constructor ensures cause is non-null via static factory
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
    } // Return type irrelevant

    @Override
    public @Nullable T orElse(@Nullable T other) {
      return other;
    }

    @Override
    public @Nullable T orElseGet(@NonNull Supplier<? extends T> supplier) {
      Objects.requireNonNull(supplier, "supplier cannot be null");
      return supplier.get();
    }

    @Override
    public @NonNull <U> Try<U> map(@NonNull Function<? super T, ? extends U> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      @SuppressWarnings("unchecked") // Safe cast as Failure propagates type
      Try<U> self = (Try<U>) this;
      return self; // Map does nothing on Failure
    }

    @Override
    public @NonNull <U> Try<U> flatMap(
        @NonNull Function<? super T, ? extends @NonNull Try<? extends U>> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      @SuppressWarnings("unchecked") // Safe cast as Failure propagates type
      Try<U> self = (Try<U>) this;
      return self; // flatMap does nothing on Failure
    }

    @Override
    public @NonNull Try<T> recover(
        @NonNull Function<? super Throwable, ? extends T> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      try {
        // Result of recoveryFunction can be null, Success allows null
        return new Success<>(recoveryFunction.apply(cause));
      } catch (Throwable t) {
        return new Failure<>(t); // Recovery function itself failed
      }
    }

    @Override
    public @NonNull Try<T> recoverWith(
        @NonNull Function<? super Throwable, ? extends @NonNull Try<? extends T>>
            recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      Try<? extends T> result;
      try {
        // Apply the recovery function only inside the try-catch
        result = recoveryFunction.apply(cause); // recoveryFunction is NonNull
      } catch (Throwable t) {
        // Catch exceptions thrown by the recovery function itself
        return new Failure<>(t);
      }
      // Check if the *result* of the recovery function is null *after* the try-catch
      Objects.requireNonNull(result, "recoverWith function returned null Try");
      @SuppressWarnings("unchecked")
      Try<T> typedResult = (Try<T>) result;
      return typedResult;
    }
  }
}
