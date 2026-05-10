// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;

/**
 * Custom AssertJ assertions for {@link Id} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Id} instances
 * within the Kind abstraction.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.IdAssert.assertThatId;
 *
 * Kind<IdKind.Witness, Integer> id = ID.widen(Id.of(42));
 * assertThatId(id).hasValue(42);
 * }</pre>
 *
 * @param <T> The type of value held by the Id
 */
public class IdAssert<T> extends AbstractAssert<IdAssert<T>, Kind<IdKind.Witness, T>> {

  /** Entry point. */
  public static <T> IdAssert<T> assertThatId(Kind<IdKind.Witness, T> actual) {
    return new IdAssert<>(actual);
  }

  protected IdAssert(Kind<IdKind.Witness, T> actual) {
    super(actual, IdAssert.class);
  }

  /** Verifies that the actual Id contains the expected value. */
  public IdAssert<T> hasValue(T expected) {
    isNotNull();
    T actualValue = ID.narrow(actual).value();
    Assertions.assertThat(actualValue)
        .withFailMessage("Expected Id to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the actual Id contains a value satisfying the given requirements. */
  public IdAssert<T> satisfies(Consumer<? super T> requirements) {
    isNotNull();
    requirements.accept(ID.narrow(actual).value());
    return this;
  }

  /** Verifies that the actual Id contains a value matching the given predicate. */
  public IdAssert<T> valueMatches(Predicate<? super T> predicate) {
    isNotNull();
    T value = ID.narrow(actual).value();
    Assertions.assertThat(predicate.test(value))
        .withFailMessage("Expected Id value to match predicate but it did not. Value: <%s>", value)
        .isTrue();
    return this;
  }

  /** Verifies that the actual Id contains a non-null value. */
  public IdAssert<T> hasNonNullValue() {
    isNotNull();
    Assertions.assertThat(ID.narrow(actual).value())
        .withFailMessage("Expected Id to contain non-null value but contained null")
        .isNotNull();
    return this;
  }

  /** Verifies that the actual Id contains a null value. */
  public IdAssert<T> hasNullValue() {
    isNotNull();
    T value = ID.narrow(actual).value();
    Assertions.assertThat(value)
        .withFailMessage("Expected Id to contain null but contained <%s>", value)
        .isNull();
    return this;
  }

  /** Verifies that the actual Id contains a value that is an instance of the given type. */
  public IdAssert<T> hasValueOfType(Class<?> type) {
    isNotNull();
    T value = ID.narrow(actual).value();
    Assertions.assertThat(value)
        .withFailMessage(
            "Expected Id to contain value of type <%s> but contained %s",
            type.getName(),
            value == null
                ? "null"
                : String.format("<%s> of type <%s>", value, value.getClass().getName()))
        .isInstanceOf(type);
    return this;
  }

  /** Verifies that the actual Id is equal to another Id Kind based on value equality. */
  public IdAssert<T> isEqualToId(Kind<IdKind.Witness, T> other) {
    isNotNull();
    Assertions.assertThat(other)
        .withFailMessage("Expected Id to be equal to another Id but the other was null")
        .isNotNull();
    T thisValue = ID.narrow(actual).value();
    T otherValue = ID.narrow(other).value();
    Assertions.assertThat(thisValue)
        .withFailMessage("Expected Id to be equal to <%s> but was <%s>", otherValue, thisValue)
        .isEqualTo(otherValue);
    return this;
  }
}
