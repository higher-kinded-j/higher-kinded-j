// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;

/**
 * Custom AssertJ assertions for {@link Optional} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Optional} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.optional.OptionalAssert.assertThatOptional;
 *
 * Kind<OptionalKind.Witness, Integer> optional = OPTIONAL.widen(Optional.of(42));
 * assertThatOptional(optional)
 *     .isPresent()
 *     .contains(42);
 *
 * Kind<OptionalKind.Witness, String> empty = OPTIONAL.widen(Optional.empty());
 * assertThatOptional(empty).isEmpty();
 *
 * // Chaining with custom assertions
 * assertThatOptional(optional)
 *     .isPresent()
 *     .satisfies(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <T> The type of value held by the Optional
 */
public class OptionalAssert<T>
    extends AbstractAssert<OptionalAssert<T>, Kind<OptionalKind.Witness, T>> {

  /**
   * Creates a new {@code OptionalAssert} instance.
   *
   * <p>This is the entry point for all Optional assertions. Import statically for best
   * readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.optional.OptionalAssert.assertThatOptional;
   * }</pre>
   *
   * @param <T> The type of value held by the Optional
   * @param actual The Optional Kind instance to make assertions on
   * @return A new OptionalAssert instance
   */
  public static <T> OptionalAssert<T> assertThatOptional(Kind<OptionalKind.Witness, T> actual) {
    return new OptionalAssert<>(actual);
  }

  protected OptionalAssert(Kind<OptionalKind.Witness, T> actual) {
    super(actual, OptionalAssert.class);
  }

  /**
   * Verifies that the actual {@code Optional} is present (contains a value).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, String> optional = OPTIONAL.widen(Optional.of("test"));
   * assertThatOptional(optional).isPresent();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Optional is null or empty
   */
  public OptionalAssert<T> isPresent() {
    isNotNull();
    Optional<T> optional = OPTIONAL.narrow(actual);
    if (optional.isEmpty()) {
      failWithMessage("Expected Optional to be present but was empty");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is empty.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, String> optional = OPTIONAL.widen(Optional.empty());
   * assertThatOptional(optional).isEmpty();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Optional is null or present
   */
  public OptionalAssert<T> isEmpty() {
    isNotNull();
    Optional<T> optional = OPTIONAL.narrow(actual);
    if (optional.isPresent()) {
      failWithMessage(
          "Expected Optional to be empty but contained value: <%s>", optional.get());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present and contains the expected value.
   *
   * <p>This method first verifies that the Optional is present, then checks the contained value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, Integer> optional = OPTIONAL.widen(Optional.of(42));
   * assertThatOptional(optional).contains(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Optional is empty or the value doesn't match
   */
  public OptionalAssert<T> contains(T expected) {
    isPresent();
    Optional<T> optional = OPTIONAL.narrow(actual);
    T actualValue = optional.get();
    if (!Objects.equals(actualValue, expected)) {
      failWithMessage(
          "Expected Optional to contain <%s> but contained <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present and the contained value satisfies the
   * given requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, Integer> optional = OPTIONAL.widen(Optional.of(42));
   * assertThatOptional(optional).satisfies(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Optional is empty or the requirements are not satisfied
   */
  public OptionalAssert<T> satisfies(Consumer<? super T> requirements) {
    isPresent();
    Optional<T> optional = OPTIONAL.narrow(actual);
    requirements.accept(optional.get());
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present and the contained value matches the given
   * predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, Integer> optional = OPTIONAL.widen(Optional.of(42));
   * assertThatOptional(optional).valueMatches(n -> n > 40);
   * }</pre>
   *
   * @param predicate The predicate to test
   * @return This assertion object for method chaining
   * @throws AssertionError if the Optional is empty or the value doesn't match the predicate
   */
  public OptionalAssert<T> valueMatches(Predicate<? super T> predicate) {
    isPresent();
    Optional<T> optional = OPTIONAL.narrow(actual);
    T value = optional.get();
    if (!predicate.test(value)) {
      failWithMessage(
          "Expected Optional value to match predicate but it did not. Value: <%s>", value);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present containing a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, String> optional = OPTIONAL.widen(Optional.of("test"));
   * assertThatOptional(optional).containsNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Optional is empty or contains null
   */
  public OptionalAssert<T> containsNonNull() {
    isPresent();
    Optional<T> optional = OPTIONAL.narrow(actual);
    T value = optional.get();
    if (value == null) {
      failWithMessage("Expected Optional to contain non-null value but contained null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present and the contained value is an instance of
   * the given type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, Number> optional = OPTIONAL.widen(Optional.of(42));
   * assertThatOptional(optional).containsInstanceOf(Integer.class);
   * }</pre>
   *
   * @param type The expected type
   * @return This assertion object for method chaining
   * @throws AssertionError if the Optional is empty or the value is not of the expected type
   */
  public OptionalAssert<T> containsInstanceOf(Class<?> type) {
    isPresent();
    Optional<T> optional = OPTIONAL.narrow(actual);
    T value = optional.get();
    if (value != null && !type.isInstance(value)) {
      failWithMessage(
          "Expected Optional to contain instance of <%s> but contained <%s> of type <%s>",
          type.getName(), value, value.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Optional} is present or empty as specified.
   *
   * <p>This is useful when the expected state is determined dynamically.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<OptionalKind.Witness, String> optional = OPTIONAL.widen(Optional.of("test"));
   * assertThatOptional(optional).hasPresentValue(true);
   *
   * Kind<OptionalKind.Witness, String> empty = OPTIONAL.widen(Optional.empty());
   * assertThatOptional(empty).hasPresentValue(false);
   * }</pre>
   *
   * @param shouldBePresent Whether the Optional should be present
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual state doesn't match the expected state
   */
  public OptionalAssert<T> hasPresentValue(boolean shouldBePresent) {
    isNotNull();
    Optional<T> optional = OPTIONAL.narrow(actual);
    if (shouldBePresent) {
      if (optional.isEmpty()) {
        failWithMessage("Expected Optional to be present but was empty");
      }
    } else {
      if (optional.isPresent()) {
        failWithMessage(
            "Expected Optional to be empty but contained value: <%s>", optional.get());
      }
    }
    return this;
  }
}
