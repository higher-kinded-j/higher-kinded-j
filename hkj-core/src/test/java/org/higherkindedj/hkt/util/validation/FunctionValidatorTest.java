// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.test.assertions.FunctionAssertions.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FunctionValidator")
class FunctionValidatorTest {
  private static final class TestClass {}

  private static final Function<String, Integer> TEST_MAPPER = String::length;

  @Nested
  @DisplayName("requireMapper with class-based context")
  class RequireMapperWithClass {
    @Test
    @DisplayName("should return non-null mapper")
    void shouldReturnNonNullMapper() {
      var result = Validation.function().requireMapper(TEST_MAPPER, "f", TestClass.class, MAP);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when mapper is null")
    void shouldThrowWhenMapperIsNull() {
      assertMapperNull(
          () -> Validation.function().requireMapper(null, "f", TestClass.class, MAP),
          "f",
          TestClass.class,
          MAP);
    }

    @Test
    @DisplayName("should include function name in error message")
    void shouldIncludeFunctionNameInErrorMessage() {
      assertMapperNull(
          () -> Validation.function().requireMapper(null, "myMapper", TestClass.class, MAP),
          "myMapper",
          TestClass.class,
          MAP);
    }
  }

  @Nested
  @DisplayName("requireFlatMapper with class-based context")
  class RequireFlatMapperWithClass {
    @Test
    @DisplayName("should return non-null flat mapper")
    void shouldReturnNonNullFlatMapper() {
      var result =
          Validation.function().requireFlatMapper(TEST_MAPPER, "f", TestClass.class, FLAT_MAP);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when flat mapper is null")
    void shouldThrowWhenFlatMapperIsNull() {
      assertFlatMapperNull(
          () -> Validation.function().requireFlatMapper(null, "f", TestClass.class, FLAT_MAP),
          "f",
          TestClass.class,
          FLAT_MAP);
    }
  }

  @Nested
  @DisplayName("requireApplicative with class-based context")
  class RequireApplicativeWithClass {
    private static final Object TEST_APPLICATIVE = new Object();

    @Test
    @DisplayName("should return non-null applicative")
    void shouldReturnNonNullApplicative() {
      var result =
          Validation.function()
              .requireApplicative(TEST_APPLICATIVE, "applicative", TestClass.class, TRAVERSE);

      assertThat(result).isEqualTo(TEST_APPLICATIVE);
    }

    @Test
    @DisplayName("should throw NullPointerException when applicative is null")
    void shouldThrowWhenApplicativeIsNull() {
      assertApplicativeNull(
          () ->
              Validation.function()
                  .requireApplicative(null, "applicative", TestClass.class, TRAVERSE),
          "applicative",
          TestClass.class,
          TRAVERSE);
    }
  }

  @Nested
  @DisplayName("requireMonoid with class-based context")
  class RequireMonoidWithClass {
    private static final Object TEST_MONOID = new Object();

    @Test
    @DisplayName("should return non-null monoid")
    void shouldReturnNonNullMonoid() {
      var result =
          Validation.function().requireMonoid(TEST_MONOID, "monoid", TestClass.class, FOLD_MAP);

      assertThat(result).isEqualTo(TEST_MONOID);
    }

    @Test
    @DisplayName("should throw NullPointerException when monoid is null")
    void shouldThrowWhenMonoidIsNull() {
      assertMonoidNull(
          () -> Validation.function().requireMonoid(null, "monoid", TestClass.class, FOLD_MAP),
          "monoid",
          TestClass.class,
          FOLD_MAP);
    }
  }

  @Nested
  @DisplayName("requireFunction with class-based context")
  class RequireFunctionWithClass {
    @Test
    @DisplayName("should return non-null function")
    void shouldReturnNonNullFunction() {
      var result =
          Validation.function()
              .requireFunction(TEST_MAPPER, "runStateTFn", TestClass.class, CONSTRUCTION);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when function is null")
    void shouldThrowWhenFunctionIsNull() {
      assertFunctionNull(
          () ->
              Validation.function()
                  .requireFunction(null, "runStateTFn", TestClass.class, CONSTRUCTION),
          "runStateTFn",
          TestClass.class,
          CONSTRUCTION);
    }
  }

  @Nested
  @DisplayName("requireNonNullResult")
  class RequireNonNullResult {
    @Test
    @DisplayName("should return non-null result")
    void shouldReturnNonNullResult() {
      var result = "test-result";
      var validated =
          Validation.function().requireNonNullResult(result, "f", TestClass.class, FLAT_MAP);

      assertThat(validated).isEqualTo(result);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when result is null")
    void shouldThrowWhenResultIsNull() {
      org.assertj.core.api.Assertions.assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  Validation.function().requireNonNullResult(null, "f", TestClass.class, FLAT_MAP))
          .withMessage("Function f in TestClass.flatMap returned null, which is not allowed");
    }

    @Test
    @DisplayName("should include target type in error message when provided")
    void shouldIncludeTargetTypeInErrorMessage() {
      org.assertj.core.api.Assertions.assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  Validation.function()
                      .requireNonNullResult(null, "f", TestClass.class, FLAT_MAP, String.class))
          .withMessage(
              "Function f in TestClass.flatMap returned null when String expected, which is not"
                  + " allowed");
    }
  }

  @Nested
  @DisplayName("requireHandler")
  class RequireHandler {
    private static final Function<Exception, String> TEST_HANDLER = Throwable::getMessage;

    @Test
    @DisplayName("should return non-null handler")
    void shouldReturnNonNullHandler() {
      var result =
          Validation.function().requireHandler(TEST_HANDLER, TestClass.class, HANDLE_ERROR_WITH);

      assertThat(result).isEqualTo(TEST_HANDLER);
    }

    @Test
    @DisplayName("should throw NullPointerException when handler is null")
    void shouldThrowWhenHandlerIsNull() {
      assertHandlerNull(
          () -> Validation.function().requireHandler(null, TestClass.class, HANDLE_ERROR_WITH),
          TestClass.class,
          HANDLE_ERROR_WITH);
    }
  }
}
