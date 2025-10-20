// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.validation.FunctionValidator.FUNCTION_VALIDATOR;
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
      var result = FUNCTION_VALIDATOR.requireMapper(TEST_MAPPER, "f", TestClass.class, MAP);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when mapper is null")
    void shouldThrowWhenMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireMapper(null, "f", TestClass.class, MAP))
          .withMessage("Function f for TestClass.map cannot be null");
    }

    @Test
    @DisplayName("should include function name in error message")
    void shouldIncludeFunctionNameInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireMapper(null, "myMapper", TestClass.class, MAP))
          .withMessageContaining("myMapper");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireMapper(TEST_MAPPER, "f", null, MAP))
          .withMessage("contextClass cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when operation is null")
    void shouldThrowWhenOperationIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireMapper(TEST_MAPPER, "f", TestClass.class, null))
          .withMessage("operation cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when function name is null")
    void shouldThrowWhenFunctionNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireMapper(TEST_MAPPER, null, TestClass.class, MAP))
          .withMessage("functionName cannot be null");
    }
  }

  @Nested
  @DisplayName("requireMapper with operation context")
  class RequireMapperWithOperation {

    @Test
    @DisplayName("should return non-null mapper")
    void shouldReturnNonNullMapper() {
      var result = FUNCTION_VALIDATOR.requireMapper(TEST_MAPPER, "f", "customOp");

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when mapper is null")
    void shouldThrowWhenMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireMapper(null, "f", "customOp"))
          .withMessage("Function f for customOp cannot be null");
    }
  }

  @Nested
  @DisplayName("requireFlatMapper with class-based context")
  class RequireFlatMapperWithClass {

    @Test
    @DisplayName("should return non-null flat mapper")
    void shouldReturnNonNullFlatMapper() {
      var result =
          FUNCTION_VALIDATOR.requireFlatMapper(TEST_MAPPER, "f", TestClass.class, FLAT_MAP);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when flat mapper is null")
    void shouldThrowWhenFlatMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireFlatMapper(null, "f", TestClass.class, FLAT_MAP))
          .withMessage("Function f for TestClass.flatMap cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireFlatMapper(TEST_MAPPER, "f", null, FLAT_MAP))
          .withMessage("contextClass cannot be null");
    }
  }

  @Nested
  @DisplayName("requireFlatMapper with operation context")
  class RequireFlatMapperWithOperation {

    @Test
    @DisplayName("should return non-null flat mapper")
    void shouldReturnNonNullFlatMapper() {
      var result = FUNCTION_VALIDATOR.requireFlatMapper(TEST_MAPPER, "f", "customFlatMap");

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when flat mapper is null")
    void shouldThrowWhenFlatMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireFlatMapper(null, "f", "customFlatMap"))
          .withMessage("Function f for customFlatMap cannot be null");
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
          FUNCTION_VALIDATOR.requireApplicative(
              TEST_APPLICATIVE, "applicative", TestClass.class, TRAVERSE);

      assertThat(result).isEqualTo(TEST_APPLICATIVE);
    }

    @Test
    @DisplayName("should throw NullPointerException when applicative is null")
    void shouldThrowWhenApplicativeIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireApplicative(
                      null, "applicative", TestClass.class, TRAVERSE))
          .withMessage("Function applicative for TestClass.traverse cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when applicative name is null")
    void shouldThrowWhenApplicativeNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireApplicative(
                      TEST_APPLICATIVE, null, TestClass.class, TRAVERSE))
          .withMessage("applicativeName cannot be null");
    }
  }

  @Nested
  @DisplayName("requireApplicative with operation context")
  class RequireApplicativeWithOperation {

    private static final Object TEST_APPLICATIVE = new Object();

    @Test
    @DisplayName("should return non-null applicative")
    void shouldReturnNonNullApplicative() {
      var result =
          FUNCTION_VALIDATOR.requireApplicative(TEST_APPLICATIVE, "applicative", "customTraverse");

      assertThat(result).isEqualTo(TEST_APPLICATIVE);
    }

    @Test
    @DisplayName("should throw NullPointerException when applicative is null")
    void shouldThrowWhenApplicativeIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireApplicative(null, "applicative", "customTraverse"))
          .withMessage("Function applicative for customTraverse cannot be null");
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
          FUNCTION_VALIDATOR.requireMonoid(TEST_MONOID, "monoid", TestClass.class, FOLD_MAP);

      assertThat(result).isEqualTo(TEST_MONOID);
    }

    @Test
    @DisplayName("should throw NullPointerException when monoid is null")
    void shouldThrowWhenMonoidIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireMonoid(null, "monoid", TestClass.class, FOLD_MAP))
          .withMessage("Function monoid for TestClass.foldMap cannot be null");
    }
  }

  @Nested
  @DisplayName("requireMonoid with operation context")
  class RequireMonoidWithOperation {

    private static final Object TEST_MONOID = new Object();

    @Test
    @DisplayName("should return non-null monoid")
    void shouldReturnNonNullMonoid() {
      var result = FUNCTION_VALIDATOR.requireMonoid(TEST_MONOID, "monoid", "customFoldMap");

      assertThat(result).isEqualTo(TEST_MONOID);
    }

    @Test
    @DisplayName("should throw NullPointerException when monoid is null")
    void shouldThrowWhenMonoidIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireMonoid(null, "monoid", "customFoldMap"))
          .withMessage("Function monoid for customFoldMap cannot be null");
    }
  }

  @Nested
  @DisplayName("requireFunction with class-based context")
  class RequireFunctionWithClass {

    @Test
    @DisplayName("should return non-null function")
    void shouldReturnNonNullFunction() {
      var result =
          FUNCTION_VALIDATOR.requireFunction(
              TEST_MAPPER, "runStateTFn", TestClass.class, CONSTRUCTION);

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when function is null")
    void shouldThrowWhenFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireFunction(
                      null, "runStateTFn", TestClass.class, CONSTRUCTION))
          .withMessage("Function runStateTFn for TestClass.construction cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireFunction(
                      TEST_MAPPER, "runStateTFn", null, CONSTRUCTION))
          .withMessage("contextClass cannot be null");
    }
  }

  @Nested
  @DisplayName("requireFunction with operation context")
  class RequireFunctionWithOperation {

    @Test
    @DisplayName("should return non-null function")
    void shouldReturnNonNullFunction() {
      var result = FUNCTION_VALIDATOR.requireFunction(TEST_MAPPER, "fn", "customOperation");

      assertThat(result).isEqualTo(TEST_MAPPER);
    }

    @Test
    @DisplayName("should throw NullPointerException when function is null")
    void shouldThrowWhenFunctionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireFunction(null, "fn", "customOperation"))
          .withMessage("Function fn for customOperation cannot be null");
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
          FUNCTION_VALIDATOR.requireNonNullResult(result, "f", TestClass.class, FLAT_MAP);

      assertThat(validated).isEqualTo(result);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when result is null")
    void shouldThrowWhenResultIsNull() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireNonNullResult(null, "f", TestClass.class, FLAT_MAP))
          .withMessage("Function f in TestClass.flatMap returned null, which is not allowed");
    }

    @Test
    @DisplayName("should include target type in error message when provided")
    void shouldIncludeTargetTypeInErrorMessage() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireNonNullResult(
                      null, "f", TestClass.class, FLAT_MAP, String.class))
          .withMessage(
              "Function f in TestClass.flatMap returned null when String expected, which is not"
                  + " allowed");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> FUNCTION_VALIDATOR.requireNonNullResult("result", "f", null, FLAT_MAP))
          .withMessage("contextClass cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when operation is null")
    void shouldThrowWhenOperationIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireNonNullResult("result", "f", TestClass.class, null))
          .withMessage("operation cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when function name is null")
    void shouldThrowWhenFunctionNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  FUNCTION_VALIDATOR.requireNonNullResult(
                      "result", null, TestClass.class, FLAT_MAP))
          .withMessage("functionName cannot be null");
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
          FUNCTION_VALIDATOR.requireHandler(TEST_HANDLER, TestClass.class, HANDLE_ERROR_WITH);

      assertThat(result).isEqualTo(TEST_HANDLER);
    }

    @Test
    @DisplayName("should throw NullPointerException when handler is null")
    void shouldThrowWhenHandlerIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireHandler(null, TestClass.class, HANDLE_ERROR_WITH))
          .withMessage("Function handler for TestClass.handleErrorWith cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> FUNCTION_VALIDATOR.requireHandler(TEST_HANDLER, null, HANDLE_ERROR_WITH))
          .withMessage("contextClass cannot be null");
    }
  }

  @Nested
  @DisplayName("FunctionContext")
  class FunctionContextTest {

    @Test
    @DisplayName("should create mapper context")
    void shouldCreateMapperContext() {
      var context = FunctionValidator.FunctionContext.mapper("f", "map");

      assertThat(context.functionName()).isEqualTo("f");
      assertThat(context.operation()).isEqualTo("map");
    }

    @Test
    @DisplayName("should create flat mapper context")
    void shouldCreateFlatMapperContext() {
      var context = FunctionValidator.FunctionContext.flatMapper("f", "flatMap");

      assertThat(context.functionName()).isEqualTo("f");
      assertThat(context.operation()).isEqualTo("flatMap");
    }

    @Test
    @DisplayName("should create applicative context")
    void shouldCreateApplicativeContext() {
      var context = FunctionValidator.FunctionContext.applicative("app", "traverse");

      assertThat(context.functionName()).isEqualTo("app");
      assertThat(context.operation()).isEqualTo("traverse");
    }

    @Test
    @DisplayName("should generate correct null parameter message")
    void shouldGenerateCorrectNullParameterMessage() {
      var context = new FunctionValidator.FunctionContext("myFunction", "myOperation");

      assertThat(context.nullParameterMessage())
          .isEqualTo("Function myFunction for myOperation cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when function name is null")
    void shouldThrowWhenFunctionNameIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new FunctionValidator.FunctionContext(null, "operation"))
          .withMessage("functionName cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when operation is null")
    void shouldThrowWhenOperationIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new FunctionValidator.FunctionContext("function", null))
          .withMessage("operation cannot be null");
    }
  }
}
