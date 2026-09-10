// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Invalid;
import org.higherkindedj.hkt.validated.Valid;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.jspecify.annotations.Nullable;

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
   * Creates a new ValidatedAssert from a {@code Kind<ValidatedKind.Witness<E>, A>}, narrowing
   * internally.
   *
   * @param actual the Validated value in its Kind representation
   * @param <E> the error type
   * @param <A> the value type
   * @return a new ValidatedAssert instance
   */
  public static <E, A> ValidatedAssert<E, A> assertThatValidated(
      Kind<ValidatedKind.Witness<E>, A> actual) {
    return new ValidatedAssert<>(ValidatedKindHelper.VALIDATED.narrow(actual));
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

  /**
   * Asserts that the Validated is Invalid and its accumulated {@link FieldError}s render, in that
   * order, as the given lines.
   *
   * <p>Each error is rendered by {@link FieldError#toString()}, i.e. {@code "path: message"}, or
   * just the message for an unlabelled leaf error. Order is asserted because declaration order is
   * the accumulation doctrine: {@code fields()} reports the errors in the order the fields were
   * declared.
   *
   * <pre>{@code
   * assertThatValidated(OrderMappingImpl.INSTANCE.parse(badDto))
   *     .hasFieldErrors(
   *         "id: not a UUID (expected e.g. 123e4567-e89b-12d3-a456-426614174000)",
   *         "placedOn: not an ISO-8601 date (expected e.g. 2026-07-28)");
   * }</pre>
   *
   * <p>Applies to the located-error channel only: the Invalid must carry an {@link Iterable} of
   * {@code FieldError}, which is what {@code NonEmptyList<FieldError>} is. For any other error type
   * use {@link #hasError(Object)} or {@link #hasErrorSatisfying(Predicate, String)}.
   *
   * @param expected the rendered errors, in accumulation order
   */
  public ValidatedAssert<E, A> hasFieldErrors(String... expected) {
    isInvalid();
    Assertions.assertThat(renderedFieldErrors())
        .as("Validated FieldErrors")
        .containsExactly(expected);
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

  /**
   * Direct accessor for the Invalid error, never null: {@link Invalid} rejects a null error. Caller
   * must have verified the Validated is Invalid.
   */
  private E invalidError() {
    return ((Invalid<E, A>) actual).error();
  }

  /**
   * Renders the accumulated located errors, failing if the Invalid does not carry {@link
   * FieldError}s. Caller must have verified the Validated is Invalid.
   */
  private List<String> renderedFieldErrors() {
    E error = invalidError();
    if (!(error instanceof Iterable<?> located)) {
      throw failure(
          "Cannot render FieldErrors from an error of type <%s>: <%s>. hasFieldErrors() applies to"
              + " the located-error channel (NonEmptyList<FieldError>); use hasError() or"
              + " hasErrorSatisfying() for other error types.",
          error.getClass().getSimpleName(), error);
    }
    List<String> rendered = new ArrayList<>();
    for (Object element : located) {
      if (element instanceof FieldError fieldError) {
        rendered.add(fieldError.toString());
      } else {
        throw failure(
            "Expected every accumulated error to be a FieldError but element %d was <%s> (type"
                + " <%s>). Accumulated errors: <%s>.",
            rendered.size(), element, simpleTypeName(element), error);
      }
    }
    return List.copyOf(rendered);
  }

  /**
   * The element's simple type name, or {@code "null"}. A {@code NonEmptyList} rejects null
   * elements, but {@link #hasFieldErrors} accepts any {@link Iterable}, and a plain {@code
   * Arrays.asList} can hold one.
   */
  private static String simpleTypeName(@Nullable Object element) {
    return element == null ? "null" : element.getClass().getSimpleName();
  }
}
