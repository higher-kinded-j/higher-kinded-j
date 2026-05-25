// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;

/**
 * Custom AssertJ assertions for {@link Try} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods for testing {@code Try} values in their {@code Kind}
 * representation, narrowing internally so callers don't have to. A bare {@code Try} can also be
 * passed via the {@link #assertThatTry(Try)} overload.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
 *
 * Kind<TryKind.Witness, Integer> kind = TRY.widen(Try.success(42));
 * assertThatTry(kind).isSuccess().hasValue(42);
 *
 * Try<Integer> failure = Try.failure(new RuntimeException("Error"));
 * assertThatTry(failure).isFailure();
 * }</pre>
 *
 * @param <T> The type of the value held by the Try
 */
public class TryAssert<T> extends AbstractAssert<TryAssert<T>, Kind<TryKind.Witness, T>> {

  /** Entry point accepting a {@code Kind<TryKind.Witness, T>}. */
  public static <T> TryAssert<T> assertThatTry(Kind<TryKind.Witness, T> actual) {
    return new TryAssert<>(actual);
  }

  /**
   * Entry point accepting a bare {@code Try<T>}. Most-specific overload — preserves source
   * compatibility for callers passing an unwrapped {@code Try}.
   */
  public static <T> TryAssert<T> assertThatTry(Try<T> actual) {
    return new TryAssert<>(TRY.widen(actual));
  }

  protected TryAssert(Kind<TryKind.Witness, T> actual) {
    super(actual, TryAssert.class);
  }

  /** Verifies the actual {@code Try} is a Success. */
  public TryAssert<T> isSuccess() {
    isNotNull();
    Assertions.assertThat(TRY.narrow(actual).isSuccess())
        .withFailMessage("Expected Try to be Success but was Failure")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code Try} is a Failure. */
  public TryAssert<T> isFailure() {
    isNotNull();
    Try<T> t = TRY.narrow(actual);
    Assertions.assertThat(t.isFailure())
        .withFailMessage(
            () -> "Expected Try to be Failure but was Success with value: <" + successValue() + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code Try} is a Success and contains {@code expected}. */
  public TryAssert<T> hasValue(T expected) {
    isSuccess();
    T actualValue = successValue();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Try.Success to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies the actual {@code Try} is a Success and the contained value satisfies {@code
   * requirements}.
   */
  public TryAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    isSuccess();
    requirements.accept(successValue());
    return this;
  }

  /** Verifies the actual {@code Try} is a Success containing a non-null value. */
  public TryAssert<T> hasValueNonNull() {
    isSuccess();
    Assertions.assertThat(successValue()).as("Try.Success value").isNotNull();
    return this;
  }

  /** Verifies the actual {@code Try} is a Failure with the exact {@code expected} exception. */
  public TryAssert<T> hasException(Throwable expected) {
    isFailure();
    Throwable actualException = failureCause();
    Assertions.assertThat(actualException)
        .withFailMessage(
            "Expected Try.Failure to contain exception <%s> but contained <%s>",
            expected, actualException)
        .isSameAs(expected);
    return this;
  }

  /** Verifies the actual {@code Try} is a Failure with an exception of {@code expectedType}. */
  public TryAssert<T> hasExceptionOfType(Class<? extends Throwable> expectedType) {
    isFailure();
    Throwable actualException = failureCause();
    Assertions.assertThat(actualException)
        .withFailMessage(
            "Expected Try.Failure to contain exception of type <%s> but contained <%s>",
            expectedType.getName(), actualException.getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /**
   * Verifies the actual {@code Try} is a Failure and the exception satisfies {@code requirements}.
   */
  public TryAssert<T> hasExceptionSatisfying(Consumer<? super Throwable> requirements) {
    isFailure();
    requirements.accept(failureCause());
    return this;
  }

  private T successValue() {
    return ((Try.Success<T>) TRY.narrow(actual)).value();
  }

  private Throwable failureCause() {
    return ((Try.Failure<T>) TRY.narrow(actual)).cause();
  }
}
