// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Custom AssertJ assertions for {@link Optional} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Optional} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
 *
 * Kind<OptionalKind.Witness, Integer> optional = OPTIONAL.widen(Optional.of(42));
 * assertThatOptionalKind(optional)
 *     .isPresent()
 *     .contains(42);
 *
 * Kind<OptionalKind.Witness, String> empty = OPTIONAL.widen(Optional.empty());
 * assertThatOptionalKind(empty).isEmpty();
 *
 * // Chaining with custom assertions
 * assertThatOptionalKind(optional)
 *     .isPresent()
 *     .satisfies(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <T> The type of value held by the Optional
 */
public class OptionalKindAssert<T>
    extends AbstractAssert<OptionalKindAssert<T>, Kind<OptionalKind.Witness, T>> {

  /**
   * Creates a new {@code OptionalKindAssert} instance.
   *
   * <p>This is the entry point for all Optional assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.assertions.OptionalKindAssert.assertThatOptionalKind;
   * }</pre>
   *
   * @param <T> The type of value held by the Optional
   * @param actual The Optional Kind instance to make assertions on
   * @return A new OptionalKindAssert instance
   */
  public static <T> OptionalKindAssert<T> assertThatOptionalKind(
      Kind<OptionalKind.Witness, T> actual) {
    return new OptionalKindAssert<>(actual);
  }

  protected OptionalKindAssert(Kind<OptionalKind.Witness, T> actual) {
    super(actual, OptionalKindAssert.class);
  }

  /** Verifies that the actual Optional is present (contains a value). */
  public OptionalKindAssert<T> isPresent() {
    isNotNull();
    Assertions.assertThat(OPTIONAL.narrow(actual))
        .withFailMessage("Expected Optional to be present but was empty")
        .isPresent();
    return this;
  }

  /** Verifies that the actual Optional is empty. */
  public OptionalKindAssert<T> isEmpty() {
    isNotNull();
    Optional<T> optional = OPTIONAL.narrow(actual);
    Assertions.assertThat(optional)
        .withFailMessage(
            "Expected Optional to be empty but contained value: <%s>",
            optional.isPresent() ? optional.get() : null)
        .isEmpty();
    return this;
  }

  /** Verifies that the actual Optional is present and contains the expected value. */
  public OptionalKindAssert<T> contains(T expected) {
    isPresent();
    T actualValue = OPTIONAL.narrow(actual).get();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Optional to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the actual Optional is present and the value satisfies the requirements. */
  public OptionalKindAssert<T> satisfies(Consumer<? super T> requirements) {
    isPresent();
    requirements.accept(OPTIONAL.narrow(actual).get());
    return this;
  }

  /** Verifies that the actual Optional is present and the value matches the predicate. */
  public OptionalKindAssert<T> valueMatches(Predicate<? super T> predicate) {
    isPresent();
    T value = OPTIONAL.narrow(actual).get();
    Assertions.assertThat(predicate.test(value))
        .withFailMessage(
            "Expected Optional value to match predicate but it did not. Value: <%s>", value)
        .isTrue();
    return this;
  }

  /** Verifies that the actual Optional is present and contains a non-null value. */
  public OptionalKindAssert<T> containsNonNull() {
    isPresent();
    Assertions.assertThat(OPTIONAL.narrow(actual).get())
        .withFailMessage("Expected Optional to contain non-null value but contained null")
        .isNotNull();
    return this;
  }

  /**
   * Verifies that the actual Optional is present and the value is an instance of the given type.
   */
  public OptionalKindAssert<T> containsInstanceOf(Class<?> type) {
    isPresent();
    T value = OPTIONAL.narrow(actual).get();
    Assertions.assertThat(value)
        .withFailMessage(
            "Expected Optional to contain instance of <%s> but contained <%s> of type <%s>",
            type.getName(), value, value.getClass().getName())
        .isInstanceOf(type);
    return this;
  }

  /** Verifies that the actual Optional is present or empty as specified. */
  public OptionalKindAssert<T> hasPresentValue(boolean shouldBePresent) {
    isNotNull();
    Optional<T> optional = OPTIONAL.narrow(actual);
    Assertions.assertThat(optional.isPresent())
        .withFailMessage(
            shouldBePresent
                ? "Expected Optional to be present but was empty"
                : "Expected Optional to be empty but contained value: <%s>",
            optional.isPresent() ? optional.get() : null)
        .isEqualTo(shouldBePresent);
    return this;
  }
}
