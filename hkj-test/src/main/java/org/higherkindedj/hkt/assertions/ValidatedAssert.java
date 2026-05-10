// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.validated.Invalid;
import org.higherkindedj.hkt.validated.Valid;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Fluent assertion utilities for {@link Validated} types. Provides a convenient API for testing
 * Validated instances in unit tests.
 *
 * @param <E> The error type
 * @param <A> The success value type
 */
public class ValidatedAssert<E, A> extends AbstractAssert<ValidatedAssert<E, A>, Validated<E, A>> {

  /**
   * Creates a new ValidatedAssert for the given Validated instance.
   *
   * @param validated the Validated instance to assert on
   * @param <E> the error type
   * @param <A> the value type
   * @return a new ValidatedAssert instance
   */
  public static <E, A> ValidatedAssert<E, A> assertThatValidated(Validated<E, A> validated) {
    return new ValidatedAssert<>(validated);
  }

  /**
   * Creates a new ValidatedAssert with a custom description.
   *
   * <p>The description is forwarded to {@link AbstractAssert#as(String, Object...)} and prefixes
   * the failure message in the standard AssertJ format.
   *
   * @param validated the Validated instance to assert on
   * @param description a description for this assertion
   * @param <E> the error type
   * @param <A> the value type
   * @return a new ValidatedAssert instance
   */
  public static <E, A> ValidatedAssert<E, A> assertThatValidated(
      Validated<E, A> validated, String description) {
    return new ValidatedAssert<E, A>(validated).as(description);
  }

  protected ValidatedAssert(Validated<E, A> validated) {
    super(validated, ValidatedAssert.class);
  }

  /** Asserts that the Validated is Valid. */
  public ValidatedAssert<E, A> isValid() {
    isNotNull();
    Assertions.assertThat(actual.isValid())
        .withFailMessage(
            () -> "Expected Validated to be Valid but was Invalid with error: " + invalidError())
        .isTrue();
    return this;
  }

  /** Asserts that the Validated is Invalid. */
  public ValidatedAssert<E, A> isInvalid() {
    isNotNull();
    Assertions.assertThat(actual.isInvalid())
        .withFailMessage(
            () -> "Expected Validated to be Invalid but was Valid with value: " + validValue())
        .isTrue();
    return this;
  }

  /** Asserts that the Validated is Valid and contains the expected value. */
  public ValidatedAssert<E, A> hasValue(A expected) {
    isValid();
    A actualValue = validValue();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Validated to contain value <%s> but was <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the Validated is Invalid and contains the expected error. */
  public ValidatedAssert<E, A> hasError(E expected) {
    isInvalid();
    E actualError = invalidError();
    Assertions.assertThat(actualError)
        .withFailMessage(
            "Expected Validated to contain error <%s> but was <%s>", expected, actualError)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the Validated is Valid and the value satisfies the given predicate. */
  public ValidatedAssert<E, A> hasValueSatisfying(
      Predicate<A> predicate, String predicateDescription) {
    isValid();
    A actualValue = validValue();
    Assertions.assertThat(predicate.test(actualValue))
        .withFailMessage(
            "Expected Validated value to satisfy: %s, but value was: %s",
            predicateDescription, actualValue)
        .isTrue();
    return this;
  }

  /** Asserts that the Validated is Invalid and the error satisfies the given predicate. */
  public ValidatedAssert<E, A> hasErrorSatisfying(
      Predicate<E> predicate, String predicateDescription) {
    isInvalid();
    E actualError = invalidError();
    Assertions.assertThat(predicate.test(actualError))
        .withFailMessage(
            "Expected Validated error to satisfy: %s, but error was: %s",
            predicateDescription, actualError)
        .isTrue();
    return this;
  }

  /** Asserts that the Validated is Valid and the value is an instance of the expected class. */
  public ValidatedAssert<E, A> hasValueOfType(Class<?> expectedClass) {
    isValid();
    Assertions.assertThat(validValue())
        .withFailMessage("Expected Validated value to be instance of %s", expectedClass.getName())
        .isInstanceOf(expectedClass);
    return this;
  }

  /** Asserts that the Validated is Invalid and the error is an instance of the expected class. */
  public ValidatedAssert<E, A> hasErrorOfType(Class<?> expectedClass) {
    isInvalid();
    Assertions.assertThat(invalidError())
        .withFailMessage("Expected Validated error to be instance of %s", expectedClass.getName())
        .isInstanceOf(expectedClass);
    return this;
  }

  /** Asserts that the Validated equals the expected Validated. */
  public ValidatedAssert<E, A> isEqualTo(Validated<E, A> expected) {
    Assertions.assertThat(actual)
        .withFailMessage("Expected Validated to equal %s but was %s", expected, actual)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the Validated does not equal the given Validated. */
  public ValidatedAssert<E, A> isNotEqualTo(Validated<E, A> other) {
    Assertions.assertThat(actual)
        .withFailMessage("Expected Validated to not equal %s but was %s", other, actual)
        .isNotEqualTo(other);
    return this;
  }

  /** Direct accessor for the Valid value. Caller must have verified the Validated is Valid. */
  private A validValue() {
    return ((Valid<E, A>) actual).value();
  }

  /** Direct accessor for the Invalid error. Caller must have verified the Validated is Invalid. */
  private E invalidError() {
    return ((Invalid<E, A>) actual).error();
  }
}
