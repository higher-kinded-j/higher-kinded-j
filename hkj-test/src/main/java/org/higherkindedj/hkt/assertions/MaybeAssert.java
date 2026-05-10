// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Custom AssertJ assertions for {@link Maybe} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code Maybe} instances,
 * making test code more readable and providing better error messages.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;
 *
 * Maybe<Integer> just = Maybe.just(42);
 * assertThatMaybe(just)
 *     .isJust()
 *     .hasValue(42);
 *
 * Maybe<Integer> nothing = Maybe.nothing();
 * assertThatMaybe(nothing).isNothing();
 *
 * // Chaining with null-safe assertions
 * assertThatMaybe(just)
 *     .isJust()
 *     .hasValueSatisfying(value -> {
 *         assertThat(value).isGreaterThan(40);
 *     });
 * }</pre>
 *
 * @param <T> The type of the value held by the Maybe
 */
public class MaybeAssert<T> extends AbstractAssert<MaybeAssert<T>, Maybe<T>> {

  /**
   * Creates a new {@code MaybeAssert} instance.
   *
   * <p>This is the entry point for all Maybe assertions. Import statically for best readability:
   *
   * <pre>{@code
   * import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;
   * }</pre>
   *
   * @param <T> The type of the value held by the Maybe
   * @param actual The Maybe instance to make assertions on
   * @return A new MaybeAssert instance
   */
  public static <T> MaybeAssert<T> assertThatMaybe(Maybe<T> actual) {
    return new MaybeAssert<>(actual);
  }

  protected MaybeAssert(Maybe<T> actual) {
    super(actual, MaybeAssert.class);
  }

  /**
   * Verifies that the actual {@code Maybe} is a {@link Just} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> maybe = Maybe.just(42);
   * assertThatMaybe(maybe).isJust();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Maybe is null or is Nothing
   */
  public MaybeAssert<T> isJust() {
    isNotNull();
    Assertions.assertThat(actual.isJust())
        .withFailMessage("Expected Maybe to be Just but was Nothing")
        .isTrue();
    return this;
  }

  /**
   * Verifies that the actual {@code Maybe} is a {@link Nothing} instance.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> maybe = Maybe.nothing();
   * assertThatMaybe(maybe).isNothing();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the actual Maybe is null or is Just
   */
  public MaybeAssert<T> isNothing() {
    isNotNull();
    Assertions.assertThat(actual.isNothing())
        .withFailMessage(
            () -> "Expected Maybe to be Nothing but was Just with value: <" + actual.get() + ">")
        .isTrue();
    return this;
  }

  /**
   * Verifies that the actual {@code Maybe} is a Just and contains the expected value.
   *
   * <p>This method first verifies that the Maybe is a Just, then checks the contained value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> maybe = Maybe.just(42);
   * assertThatMaybe(maybe).hasValue(42);
   * }</pre>
   *
   * @param expected The expected value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Maybe is Nothing or the value doesn't match
   */
  public MaybeAssert<T> hasValue(T expected) {
    isJust();
    T actualValue = actual.get();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Maybe.Just to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies that the actual {@code Maybe} is a Just and the contained value satisfies the given
   * requirements.
   *
   * <p>This is useful for complex assertions on the value without extracting it first.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> maybe = Maybe.just(42);
   * assertThatMaybe(maybe).hasValueSatisfying(value -> {
   *     assertThat(value).isGreaterThan(40);
   *     assertThat(value).isLessThan(50);
   * });
   * }</pre>
   *
   * @param requirements The requirements to verify on the value
   * @return This assertion object for method chaining
   * @throws AssertionError if the Maybe is Nothing or the requirements are not satisfied
   */
  public MaybeAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    isJust();
    requirements.accept(actual.get());
    return this;
  }

  /**
   * Verifies that the actual {@code Maybe} is a Just containing a non-null value.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Maybe<Integer> maybe = Maybe.just(42);
   * assertThatMaybe(maybe).hasValueNonNull();
   * }</pre>
   *
   * @return This assertion object for method chaining
   * @throws AssertionError if the Maybe is Nothing or the value is null
   */
  public MaybeAssert<T> hasValueNonNull() {
    isJust();
    Assertions.assertThat(actual.get()).as("Maybe.Just value").isNotNull();
    return this;
  }
}
