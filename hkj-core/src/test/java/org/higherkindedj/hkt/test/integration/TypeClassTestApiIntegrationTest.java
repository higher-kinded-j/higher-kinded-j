// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.integration;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherFunctor;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for TypeClassTest fluent API.
 *
 * <p>Tests the complete fluent API workflow for all type classes, ensuring proper progressive
 * disclosure and method chaining behaviour.
 *
 * <p>Note: These tests focus on API flow and skip validations because the new API doesn't yet
 * support flexible validation contexts (map() is in EitherFunctor but we're testing EitherMonad).
 * Validation behavior is thoroughly tested in the individual type class tests (EitherFunctorTest,
 * EitherMonadTest, etc.).
 */
@DisplayName("TypeClassTest Fluent API Integration Tests")
class TypeClassTestApiIntegrationTest {
  private EitherMonad<String> monad;
  private EitherFunctor<String> functor;
  private Kind<EitherKind.Witness<String>, Integer> validKind;
  private Kind<EitherKind.Witness<String>, Integer> validKind2;
  private Function<Integer, String> validMapper;
  private Function<Integer, Kind<EitherKind.Witness<String>, String>> validFlatMapper;
  private Kind<EitherKind.Witness<String>, Function<Integer, String>> validFunctionKind;
  private BiFunction<Integer, Integer, String> validCombiningFunction;
  private BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      equalityChecker;

  @BeforeEach
  void setUp() {
    monad = EitherMonad.instance();
    functor = EitherFunctor.instance();
    validKind = EITHER.widen(Either.right(42));
    validKind2 = EITHER.widen(Either.right(24));
    validMapper = TestFunctions.INT_TO_STRING;
    validFlatMapper = i -> EITHER.widen(Either.right("flat:" + i));
    validFunctionKind = EITHER.widen(Either.right(TestFunctions.INT_TO_STRING));
    validCombiningFunction = (a, b) -> "Result:" + a + "," + b;
    equalityChecker = (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @Nested
  @DisplayName("Functor API Tests")
  class FunctorApiTests {

    @Test
    @DisplayName("Complete fluent workflow executes successfully")
    void completeFunctorWorkflow() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .withEqualityChecker(equalityChecker)
                      .testAll())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Operations-only test executes successfully")
    void operationsOnlyTest() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .testOperations())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Selective test execution works")
    void selectiveTestExecution() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .selectTests()
                      .onlyOperations()
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Custom equality checker configuration works")
    void customEqualityChecker() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .withEqualityChecker(equalityChecker)
                      .testAll())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Skip operations works")
    void skipOperations() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .withEqualityChecker(equalityChecker)
                      .selectTests()
                      .skipOperations()
                      .test())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Applicative API Tests")
  class ApplicativeApiTests {

    @Test
    @DisplayName("Complete fluent workflow executes successfully")
    void completeApplicativeWorkflow() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withOperations(
                          validKind2, validMapper, validFunctionKind, validCombiningFunction)
                      .selectTests()
                      .skipValidations() // Skip due to inheritance context issue
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Operations only executes successfully")
    void operationsOnly() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withOperations(
                          validKind2, validMapper, validFunctionKind, validCombiningFunction)
                      .selectTests()
                      .onlyOperations()
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("With laws testing executes successfully")
    void withLawsTesting() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withOperations(
                          validKind2, validMapper, validFunctionKind, validCombiningFunction)
                      .withLawsTesting(42, validMapper, equalityChecker)
                      .selectTests()
                      .skipValidations() // Skip due to inheritance context issue
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Selective test execution works")
    void selectiveTestExecution() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withOperations(
                          validKind2, validMapper, validFunctionKind, validCombiningFunction)
                      .selectTests()
                      .onlyOperations()
                      .test())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Monad API Tests")
  class MonadApiTests {

    @Test
    @DisplayName("Complete fluent workflow executes successfully")
    void completeMonadWorkflow() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(
                          validKind2,
                          validMapper,
                          validFlatMapper,
                          validFunctionKind,
                          validCombiningFunction)
                      .selectTests()
                      .skipValidations() // Skip due to inheritance context issue
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("With laws testing executes successfully")
    void withLawsTesting() {
      Function<Integer, Kind<EitherKind.Witness<String>, String>> testFunction =
          i -> EITHER.widen(Either.right("test:" + i));
      Function<String, Kind<EitherKind.Witness<String>, String>> chainFunction =
          s -> EITHER.widen(Either.right(s + "!"));

      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(
                          validKind2,
                          validMapper,
                          validFlatMapper,
                          validFunctionKind,
                          validCombiningFunction)
                      .withLawsTesting(42, testFunction, chainFunction, equalityChecker)
                      .selectTests()
                      .skipValidations() // Skip due to inheritance context issue
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Operations only test works")
    void operationsOnly() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(
                          validKind2,
                          validMapper,
                          validFlatMapper,
                          validFunctionKind,
                          validCombiningFunction)
                      .testOperations())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Selective test execution with skip works")
    void selectiveWithSkip() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(
                          validKind2,
                          validMapper,
                          validFlatMapper,
                          validFunctionKind,
                          validCombiningFunction)
                      .configureValidation()
                      .useInheritanceValidation()
                      .withMapFrom(EitherFunctor.class)
                      .withApFrom(EitherMonad.class)
                      .withFlatMapFrom(EitherMonad.class)
                      .selectTests()
                      .skipOperations()
                      .test())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("MonadError API Tests")
  class MonadErrorApiTests {

    private Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler;
    private Kind<EitherKind.Witness<String>, Integer> validFallback;

    @BeforeEach
    void setUpMonadError() {
      validHandler = err -> monad.of(-1);
      validFallback = monad.of(-999);
    }

    @Test
    @DisplayName("Complete fluent workflow executes successfully")
    void completeMonadErrorWorkflow() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
                      .withErrorHandling(validHandler, validFallback)
                      .selectTests()
                      .skipValidations() // Skip due to inheritance context issue
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("With validation configuration executes successfully")
    void withValidationConfiguration() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
                      .withErrorHandling(validHandler, validFallback)
                      .configureValidation()
                      .useInheritanceValidation()
                      .withMapFrom(EitherFunctor.class)
                      .withApFrom(EitherMonad.class)
                      .withFlatMapFrom(EitherMonad.class)
                      .withHandleErrorWithFrom(EitherMonad.class)
                      .testAll())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Selective test execution works")
    void selectiveTestExecution() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
                      .withErrorHandling(validHandler, validFallback)
                      .selectTests()
                      .onlyOperations()
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Operations only test works")
    void operationsOnly() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>, String>monadError(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(validMapper, validFlatMapper, validFunctionKind)
                      .withErrorHandling(validHandler, validFallback)
                      .testOperations())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("API Error Handling Tests")
  class ApiErrorHandlingTests {

    @Test
    @DisplayName("Null instance throws clear error")
    void nullInstance() {
      assertThatThrownBy(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(null)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .testAll())
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Testing laws without configuration throws clear error")
    void lawsWithoutConfiguration() {
      assertThatThrownBy(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withMonadOperations(
                          validKind2,
                          validMapper,
                          validFlatMapper,
                          validFunctionKind,
                          validCombiningFunction)
                      .testAll())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("law");
    }
  }

  @Nested
  @DisplayName("Progressive Disclosure Tests")
  class ProgressiveDisclosureTests {

    @Test
    @DisplayName("Each stage returns appropriate next stage")
    void stageProgression() {
      var stage1 = TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class);
      assertThat(stage1).isNotNull();

      var stage2 = stage1.<Integer>instance(functor);
      assertThat(stage2).isNotNull();

      var stage3 = stage2.<String>withKind(validKind);
      assertThat(stage3).isNotNull();

      var stage4 = stage3.withMapper(validMapper);
      assertThat(stage4).isNotNull();

      // Need to configure equality checker for testAll() to work with laws
      assertThatCode(() -> stage4.withEqualityChecker(equalityChecker).testAll())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Method Chaining Tests")
  class MethodChainingTests {

    @Test
    @DisplayName("Complex chaining with multiple configurations works")
    void complexChaining() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .withSecondMapper(Object::toString)
                      .withEqualityChecker(equalityChecker)
                      .selectTests()
                      .skipLaws()
                      .and()
                      .testOperations())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Selective execution with multiple skips works")
    void multipleSkips() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                      .<Integer>instance(functor)
                      .<String>withKind(validKind)
                      .withMapper(validMapper)
                      .selectTests()
                      .skipOperations()
                      .skipLaws()
                      .test())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Positive selection with only methods works")
    void positiveSelection() {
      assertThatCode(
              () ->
                  TypeClassTest.<EitherKind.Witness<String>>applicative(EitherMonad.class)
                      .<Integer>instance(monad)
                      .<String>withKind(validKind)
                      .withOperations(
                          validKind2, validMapper, validFunctionKind, validCombiningFunction)
                      .selectTests()
                      .onlyOperations()
                      .test())
          .doesNotThrowAnyException();
    }
  }
}
