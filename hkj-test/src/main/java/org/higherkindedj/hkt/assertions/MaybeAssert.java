// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * Custom AssertJ assertions for {@link Maybe} instances wrapped in {@link Kind}.
 *
 * <p>Provides fluent assertion methods for testing {@code Maybe} values in their {@code Kind}
 * representation, narrowing internally so callers don't have to. A bare {@code Maybe} can also be
 * passed since {@code Maybe} implements {@code MaybeKind} (which is a {@code Kind}).
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
 *
 * Kind<MaybeKind.Witness, Integer> kind = MAYBE.widen(Maybe.just(42));
 * assertThatMaybe(kind).isJust().hasValue(42);
 *
 * Maybe<Integer> nothing = Maybe.nothing();
 * assertThatMaybe(nothing).isNothing();
 * }</pre>
 *
 * @param <T> The type of the value held by the Maybe
 */
public class MaybeAssert<T> extends AbstractAssert<MaybeAssert<T>, Kind<MaybeKind.Witness, T>> {

  /** Entry point accepting a {@code Kind<MaybeKind.Witness, T>}. */
  public static <T> MaybeAssert<T> assertThatMaybe(Kind<MaybeKind.Witness, T> actual) {
    return new MaybeAssert<>(actual);
  }

  /**
   * Entry point accepting a bare {@code Maybe<T>}. Most-specific overload — preserves source
   * compatibility for callers passing an unwrapped {@code Maybe}.
   */
  public static <T> MaybeAssert<T> assertThatMaybe(Maybe<T> actual) {
    return new MaybeAssert<>(MAYBE.widen(actual));
  }

  protected MaybeAssert(Kind<MaybeKind.Witness, T> actual) {
    super(actual, MaybeAssert.class);
  }

  /** Verifies that the actual {@code Maybe} is a Just. */
  public MaybeAssert<T> isJust() {
    isNotNull();
    Assertions.assertThat(MAYBE.narrow(actual).isJust())
        .withFailMessage("Expected Maybe to be Just but was Nothing")
        .isTrue();
    return this;
  }

  /** Verifies that the actual {@code Maybe} is a Nothing. */
  public MaybeAssert<T> isNothing() {
    isNotNull();
    Maybe<T> m = MAYBE.narrow(actual);
    Assertions.assertThat(m.isNothing())
        .withFailMessage(
            () -> "Expected Maybe to be Nothing but was Just with value: <" + m.get() + ">")
        .isTrue();
    return this;
  }

  /** Verifies that the actual {@code Maybe} is a Just and contains the expected value. */
  public MaybeAssert<T> hasValue(T expected) {
    isJust();
    T actualValue = MAYBE.narrow(actual).get();
    Assertions.assertThat(actualValue)
        .withFailMessage(
            "Expected Maybe.Just to contain <%s> but contained <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies that the actual {@code Maybe} is a Just and the contained value satisfies the given
   * requirements.
   */
  public MaybeAssert<T> hasValueSatisfying(Consumer<? super T> requirements) {
    isJust();
    requirements.accept(MAYBE.narrow(actual).get());
    return this;
  }

  /** Verifies that the actual {@code Maybe} is a Just containing a non-null value. */
  public MaybeAssert<T> hasValueNonNull() {
    isJust();
    Assertions.assertThat(MAYBE.narrow(actual).get()).as("Maybe.Just value").isNotNull();
    return this;
  }
}
