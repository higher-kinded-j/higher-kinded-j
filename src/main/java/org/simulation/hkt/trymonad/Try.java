// src/main/java/org/simulation/hkt/trymonad/Try.java
package org.simulation.hkt.trymonad;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Represents a computation that may either result in a value of type T
 * or fail with a Throwable. Similar to Scala's Try.
 *
 * @param <T> the type of the successful value
 */
public sealed interface Try<T> permits Try.Success, Try.Failure {

  /**
   * Executes the given Supplier and returns the result wrapped in a Success,
   * or catches any Throwable and returns it wrapped in a Failure.
   *
   * @param supplier The computation to execute.
   * @param <T>      The type of the result.
   * @return Success(result) if supplier completes normally, Failure(throwable) otherwise.
   */
  static <T> Try<T> of(Supplier<? extends T> supplier) {
    Objects.requireNonNull(supplier, "Supplier cannot be null");
    try {
      return new Success<>(supplier.get());
    } catch (Throwable t) {
      // Catch all Throwables, including Errors
      return new Failure<>(t);
    }
  }

  /**
   * Creates a Try representing a successful computation.
   * Allows null values, though often non-null is preferred.
   *
   * @param value The successful value.
   * @param <T>   The type of the value.
   * @return A Success instance holding the value.
   */
  static <T> Try<T> success(T value) {
    return new Success<>(value);
  }

  /**
   * Creates a Try representing a failed computation.
   *
   * @param throwable The Throwable that caused the failure. Must not be null.
   * @param <T>       The phantom type of the value (since it failed).
   * @return A Failure instance holding the Throwable.
   * @throws NullPointerException if throwable is null.
   */
  static <T> Try<T> failure(Throwable throwable) {
    Objects.requireNonNull(throwable, "Throwable for Failure cannot be null");
    return new Failure<>(throwable);
  }

  /** Checks if this Try represents a successful computation. */
  boolean isSuccess();

  /** Checks if this Try represents a failed computation. */
  boolean isFailure();

  /**
   * Gets the successful value. Throws the contained Throwable if this is a Failure.
   *
   * @return The successful value.
   * @throws Throwable if this is a Failure.
   */
  T get() throws Throwable;

  /**
   * Gets the successful value, or returns the 'other' value if this is a Failure.
   *
   * @param other The value to return in case of Failure.
   * @return The successful value or the alternative value.
   */
  T orElse(T other);

  /**
   * Gets the successful value, or returns the result of the supplier if this is a Failure.
   *
   * @param supplier Provides the alternative value in case of Failure.
   * @return The successful value or the supplied alternative value.
   */
  T orElseGet(Supplier<? extends T> supplier);

  /**
   * Applies one of the functions depending on whether this is a Success or Failure.
   *
   * @param successMapper Function to apply if Success.
   * @param failureMapper Function to apply if Failure.
   * @param <U>           The target type.
   * @return The result of the applied function.
   */
  <U> U fold(Function<? super T, ? extends U> successMapper, Function<? super Throwable, ? extends U> failureMapper);

  /**
   * If Success, applies the function to the value. If the function throws, returns Failure.
   * If Failure, returns the original Failure.
   *
   * @param mapper Function to apply to the successful value.
   * @param <U>    The type of the result of the mapping function.
   * @return A new Try potentially containing the mapped value or a Failure.
   */
  <U> Try<U> map(Function<? super T, ? extends U> mapper);

  /**
   * If Success, applies the Try-bearing function to the value. If the function throws, returns Failure.
   * If Failure, returns the original Failure.
   *
   * @param mapper Function to apply to the successful value, returning a Try.
   * @param <U>    The type parameter of the Try returned by the mapper.
   * @return The result of applying the function if Success, or the original Failure.
   */
  <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper);

  /**
   * If Failure, applies the function to the Throwable to potentially recover.
   * If the recovery function throws, returns Failure containing the new Throwable.
   * If Success, returns the original Success.
   *
   * @param recoveryFunction Function to apply to the Throwable in case of Failure.
   * @return A Try potentially recovered from Failure, or the original Success.
   */
  Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction);

  /**
   * If Failure, applies the Try-bearing function to the Throwable to potentially recover.
   * If the recovery function throws or returns null, returns Failure.
   * If Success, returns the original Success.
   *
   * @param recoveryFunction Function to apply to the Throwable, returning a Try.
   * @return A Try potentially recovered from Failure, or the original Success.
   */
  Try<T> recoverWith(Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction);


  /**
   * If Success, performs the action on the value. Catches exceptions from the action.
   * If Failure, performs the action on the Throwable. Catches exceptions from the action.
   *
   * @param successAction Action for Success case.
   * @param failureAction Action for Failure case.
   */
  void match(Consumer<? super T> successAction, Consumer<? super Throwable> failureAction);


  /** Represents a successful computation. */
  record Success<T>(T value) implements Try<T> {
    @Override public boolean isSuccess() { return true; }
    @Override public boolean isFailure() { return false; }
    @Override public T get() { return value; }
    @Override public T orElse(T other) { return value; }
    @Override public T orElseGet(Supplier<? extends T> supplier) { return value; }

    @Override
    public <U> U fold(Function<? super T, ? extends U> successMapper, Function<? super Throwable, ? extends U> failureMapper) {
      Objects.requireNonNull(successMapper, "successMapper cannot be null");
      return successMapper.apply(value);
    }

    @Override
    public <U> Try<U> map(Function<? super T, ? extends U> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      try {
        return new Success<>(mapper.apply(value));
      } catch (Throwable t) {
        return new Failure<>(t);
      }
    }

    @Override
    public <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      Try<? extends U> result;
      try {
        // Apply the mapper function only inside the try-catch
        result = mapper.apply(value);
      } catch (Throwable t) {
        // Catch exceptions thrown by the mapper function itself
        return new Failure<>(t);
      }
      // Check if the *result* of the mapper is null *after* the try-catch
      Objects.requireNonNull(result, "flatMap mapper returned null Try");
      @SuppressWarnings("unchecked") // Safe due to HKT simulation needs
      Try<U> typedResult = (Try<U>) result;
      return typedResult;
    }

    @Override
    public Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this; // Nothing to recover from
    }

    @Override
    public Try<T> recoverWith(Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      return this; // Nothing to recover from
    }

    @Override
    public void match(Consumer<? super T> successAction, Consumer<? super Throwable> failureAction) {
      Objects.requireNonNull(successAction, "successAction cannot be null");
      try {
        successAction.accept(value);
      } catch (Throwable t) {
        // Log or handle exception from consumer if needed
        System.err.println("Exception in Try.Success successAction: " + t.getMessage());
      }
    }

    @Override
    public String toString() { return "Success(" + value + ")"; }
  }

  /** Represents a failed computation. */
  record Failure<T>(Throwable cause) implements Try<T> {
    // Constructor ensures cause is non-null via static factory
    @Override public boolean isSuccess() { return false; }
    @Override public boolean isFailure() { return true; }
    @Override public T get() throws Throwable { throw cause; }
    @Override public T orElse(T other) { return other; }

    @Override public T orElseGet(Supplier<? extends T> supplier) {
      Objects.requireNonNull(supplier, "supplier cannot be null");
      return supplier.get();
    }

    @Override
    public <U> U fold(Function<? super T, ? extends U> successMapper, Function<? super Throwable, ? extends U> failureMapper) {
      Objects.requireNonNull(failureMapper, "failureMapper cannot be null");
      return failureMapper.apply(cause);
    }

    @Override
    public <U> Try<U> map(Function<? super T, ? extends U> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      @SuppressWarnings("unchecked") // Safe cast as Failure propagates type
      Try<U> self = (Try<U>) this;
      return self; // Map does nothing on Failure
    }

    @Override
    public <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper) {
      Objects.requireNonNull(mapper, "mapper cannot be null");
      @SuppressWarnings("unchecked") // Safe cast as Failure propagates type
      Try<U> self = (Try<U>) this;
      return self; // flatMap does nothing on Failure
    }

    @Override
    public Try<T> recover(Function<? super Throwable, ? extends T> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      try {
        return new Success<>(recoveryFunction.apply(cause));
      } catch (Throwable t) {
        return new Failure<>(t); // Recovery function itself failed
      }
    }

    @Override
    public Try<T> recoverWith(Function<? super Throwable, ? extends Try<? extends T>> recoveryFunction) {
      Objects.requireNonNull(recoveryFunction, "recoveryFunction cannot be null");
      Try<? extends T> result;
      try {
        // Apply the recovery function only inside the try-catch
        result = recoveryFunction.apply(cause);
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

    @Override
    public void match(Consumer<? super T> successAction, Consumer<? super Throwable> failureAction) {
      Objects.requireNonNull(failureAction, "failureAction cannot be null");
      try {
        failureAction.accept(cause);
      } catch (Throwable t) {
        // Log or handle exception from consumer if needed
        System.err.println("Exception in Try.Failure failureAction: " + t.getMessage());
      }
    }

    @Override
    public String toString() { return "Failure(" + cause + ")"; }
  }
}
