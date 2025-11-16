// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.Kind;

/**
 * Custom AssertJ assertions for {@link Id} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Id} instances
 * within the Kind abstraction, making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.id.IdAssert.assertThatId;
 *
 * Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
 * assertThatId(id)
 *     .hasValue(42);
 *
 * // Chaining with custom assertions
 * assertThatId(id).satisfies(value -> {
 *     assertThat(value).isGreaterThan(40);
 * });
 * }</pre>
 *
 * @param <T> The type of value held by the Id
 */
public class IdAssert<T> extends AbstractAssert<IdAssert<T>, Kind<IdKind.Witness, T>> {

  /**
   * Creates a new {@code IdAssert} instance.
   *
   * <p>This is the entry point for all Id assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.id.IdAssert.assertThatId;
   * }</pre>
   *
   * @param <T> The type of value held by the Id
   * @param actual The Id Kind instance to make assertions on
   * @return A new IdAssert instance
   */
  public static <T> IdAssert<T> assertThatId(Kind<IdKind.Witness, T> actual) {
    return new IdAssert<>(actual);
  }

  protected IdAssert(Kind<IdKind.Witness, T> actual) {
    super(actual, IdAssert.class);
  }

  /**
   * Verifies that the actual {@code Id} contains the expected value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
   * assertThatId(id).hasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the value doesn't match
   */
  public IdAssert<T> hasValue(T expected) {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    T actualValue = id.value();
    if (!Objects.equals(actualValue, expected)) {
      failWithMessage("Expected Id to contain <%s> but contained <%s>", expected, actualValue);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Id} contains a value satisfying the given requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
   * assertThatId(id).satisfies(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the requirements are not satisfied
   */
  public IdAssert<T> satisfies(Consumer<? super T> requirements) {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    requirements.accept(id.value());
    return this;
  }

  /**
   * Verifies that the actual {@code Id} contains a value matching the given predicate.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
   * assertThatId(id).valueMatches(n -> n > 40);
   * }</pre>
   *
   * @param predicate The predicate to test
   * @return This assertion object for method chaining
   * @throws AssertionError if the value doesn't match the predicate
   */
  public IdAssert<T> valueMatches(Predicate<? super T> predicate) {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    T value = id.value();
    if (!predicate.test(value)) {
      failWithMessage("Expected Id value to match predicate but it did not. Value: <%s>", value);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Id} contains a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, String> id = ID.widen(Id.of("test"));
   * assertThatId(id).hasNonNullValue();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is null
   */
  public IdAssert<T> hasNonNullValue() {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    T value = id.value();
    if (value == null) {
      failWithMessage("Expected Id to contain non-null value but contained null");
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Id} contains a null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, String> id = ID.widen(Id.of(null));
   * assertThatId(id).hasNullValue();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is not null
   */
  public IdAssert<T> hasNullValue() {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    T value = id.value();
    if (value != null) {
      failWithMessage("Expected Id to contain null but contained <%s>", value);
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Id} contains a value that is an instance of the given type.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, Number> id = ID.widen(Id.of(42));
   * assertThatId(id).hasValueOfType(Integer.class);
   * }</pre>
   *
   * @param type The expected type
   * @return This assertion object for method chaining
   * @throws AssertionError if the value is not of the expected type
   */
  public IdAssert<T> hasValueOfType(Class<?> type) {
    isNotNull();
    Id<T> id = ID.narrow(actual);
    T value = id.value();
    if (value != null && !type.isInstance(value)) {
      failWithMessage(
          "Expected Id to contain value of type <%s> but contained <%s> of type <%s>",
          type.getName(), value, value.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the actual {@code Id} is equal to another Id Kind based on value equality.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Kind<IdKind.Witness, Integer> id1 = ID.widen(Id.of(42));
   * Kind<IdKind.Witness, Integer> id2 = ID.widen(Id.of(42));
   * assertThatId(id1).isEqualToId(id2);
   * }</pre>
   *
   * @param other The other Id Kind to compare with
   * @return This assertion object for method chaining
   * @throws AssertionError if the Ids are not equal
   */
  public IdAssert<T> isEqualToId(Kind<IdKind.Witness, T> other) {
    isNotNull();
    if (other == null) {
      failWithMessage("Expected Id to be equal to another Id but the other was null");
      return this;
    }

    Id<T> thisId = ID.narrow(actual);
    Id<T> otherId = ID.narrow(other);

    if (!Objects.equals(thisId.value(), otherId.value())) {
      failWithMessage(
          "Expected Id to be equal to <%s> but was <%s>", otherId.value(), thisId.value());
    }
    return this;
  }
}
