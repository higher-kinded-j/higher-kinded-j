// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.lazy.LazyKindHelper;

/**
 * Custom AssertJ assertions for {@link Lazy} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Lazy} instances,
 * making test code more readable and providing better error messages.
 *
 * @param <T> The type of the value held by the Lazy
 */
public class LazyAssert<T> extends AbstractAssert<LazyAssert<T>, Lazy<T>> {

  /** Entry point accepting a {@code Kind<LazyKind.Witness, T>}. */
  public static <T> LazyAssert<T> assertThatLazy(Kind<LazyKind.Witness, T> actual) {
    return new LazyAssert<>(LazyKindHelper.LAZY.narrow(actual));
  }

  /** Entry point. */
  public static <T> LazyAssert<T> assertThatLazy(Lazy<T> actual) {
    return new LazyAssert<>(actual);
  }

  protected LazyAssert(Lazy<T> actual) {
    super(actual, LazyAssert.class);
  }

  /** Verifies that the actual Lazy is already evaluated. */
  public LazyAssert<T> isEvaluated() {
    isNotNull();
    Assertions.assertThat(actual.isEvaluated())
        .withFailMessage("Expected Lazy to be evaluated but was unevaluated")
        .isTrue();
    return this;
  }

  /** Verifies that the actual Lazy is not yet evaluated. */
  public LazyAssert<T> isNotEvaluated() {
    isNotNull();
    Assertions.assertThat(actual.isEvaluated())
        .withFailMessage(() -> "Expected Lazy to be unevaluated but was: <" + actual + ">")
        .isFalse();
    return this;
  }

  /** Verifies that the actual Lazy is evaluated and contains the expected value. */
  public LazyAssert<T> hasValue(T expected) throws Throwable {
    isEvaluated();
    T actualValue = actual.force();
    Assertions.assertThat(actualValue)
        .withFailMessage("Expected Lazy to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Forces evaluation and verifies it contains the expected value. */
  public LazyAssert<T> whenForcedHasValue(T expected) throws Throwable {
    isNotNull();
    T actualValue = actual.force();
    Assertions.assertThat(actualValue)
        .withFailMessage("Expected Lazy to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Forces evaluation and verifies the contained value satisfies the given requirements. */
  public LazyAssert<T> whenForcedHasValueSatisfying(Consumer<? super T> requirements)
      throws Throwable {
    isNotNull();
    requirements.accept(actual.force());
    return this;
  }

  /** Forces evaluation and verifies it contains a non-null value. */
  public LazyAssert<T> whenForcedHasValueNonNull() throws Throwable {
    isNotNull();
    Assertions.assertThat(actual.force()).as("Lazy forced value").isNotNull();
    return this;
  }

  /** Verifies that the actual Lazy is evaluated and contains a {@code null} value. */
  public LazyAssert<T> hasNullValue() throws Throwable {
    isEvaluated();
    Assertions.assertThat(actual.force())
        .withFailMessage(() -> "Expected Lazy to contain <null> but contained <" + actual + ">")
        .isNull();
    return this;
  }

  /** Verifies that forcing throws an exception of the expected type. */
  public LazyAssert<T> whenForcedThrows(Class<? extends Throwable> expectedExceptionType) {
    isNotNull();
    Assertions.assertThatThrownBy(actual::force)
        .as("Lazy.force() exception")
        .isInstanceOf(expectedExceptionType);
    return this;
  }

  /** Verifies that forcing throws an exception with the expected message. */
  public LazyAssert<T> whenForcedThrowsWithMessage(String expectedMessage) {
    isNotNull();
    Assertions.assertThatThrownBy(actual::force)
        .as("Lazy.force() exception message")
        .hasMessage(expectedMessage);
    return this;
  }

  /** Verifies that the actual Lazy is in a failed state (contains a cached exception). */
  public LazyAssert<T> hasFailed() {
    isNotNull();
    Assertions.assertThat(actual.hasFailed())
        .withFailMessage(() -> "Expected Lazy to have failed but was: <" + actual + ">")
        .isTrue();
    return this;
  }

  /** Verifies that the actual Lazy has not failed. */
  public LazyAssert<T> hasNotFailed() {
    isNotNull();
    Assertions.assertThat(actual.hasFailed())
        .withFailMessage(() -> "Expected Lazy to not have failed but was: <" + actual + ">")
        .isFalse();
    return this;
  }
}
