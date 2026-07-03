// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Arrays;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.validated.FieldError;

/**
 * Custom AssertJ assertions for {@link FieldError}, the located error type of the {@code fields()}
 * accumulating assembly.
 *
 * <p>Provides fluent assertions on the composable path ({@link #hasPath(String)} for the rendered
 * dot-joined form, {@link #hasSegments(String...)} for the raw segments, {@link #isUnlabelled()}
 * for leaf errors) and on the message.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.FieldErrorAssert.assertThatFieldError;
 *
 * assertThatFieldError(FieldError.of("not a postcode").at("zip").at("address"))
 *     .hasPath("address.zip")
 *     .hasSegments("address", "zip")
 *     .hasMessage("not a postcode");
 * }</pre>
 */
public class FieldErrorAssert extends AbstractAssert<FieldErrorAssert, FieldError> {

  /** Entry point for a {@link FieldError}. */
  public static FieldErrorAssert assertThatFieldError(FieldError actual) {
    return new FieldErrorAssert(actual);
  }

  protected FieldErrorAssert(FieldError actual) {
    super(actual, FieldErrorAssert.class);
  }

  /** Verifies the rendered dot-joined path (for example {@code "address.zip"}). */
  public FieldErrorAssert hasPath(String expected) {
    isNotNull();
    Assertions.assertThat(actual.pathString())
        .withFailMessage(
            "Expected path <%s> but was <%s>. FieldError: <%s>",
            expected, actual.pathString(), actual)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the raw path segments, outermost first. */
  public FieldErrorAssert hasSegments(String... expected) {
    isNotNull();
    Assertions.assertThat(actual.path())
        .withFailMessage(
            "Expected path segments <%s> but was <%s>. FieldError: <%s>",
            Arrays.toString(expected), actual.path(), actual)
        .containsExactly(expected);
    return this;
  }

  /** Verifies the error carries no path, i.e. it is an unlabelled leaf error. */
  public FieldErrorAssert isUnlabelled() {
    isNotNull();
    Assertions.assertThat(actual.path())
        .withFailMessage(
            "Expected an unlabelled FieldError (empty path) but the path was <%s>. FieldError: <%s>",
            actual.pathString(), actual)
        .isEmpty();
    return this;
  }

  /** Verifies the message equals the expected value. */
  public FieldErrorAssert hasMessage(String expected) {
    isNotNull();
    Assertions.assertThat(actual.message())
        .withFailMessage(
            "Expected message <%s> but was <%s>. FieldError: <%s>",
            expected, actual.message(), actual)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the message contains the given fragment. */
  public FieldErrorAssert hasMessageContaining(String fragment) {
    isNotNull();
    Assertions.assertThat(actual.message())
        .withFailMessage(
            "Expected message containing <%s> but was <%s>. FieldError: <%s>",
            fragment, actual.message(), actual)
        .contains(fragment);
    return this;
  }
}
