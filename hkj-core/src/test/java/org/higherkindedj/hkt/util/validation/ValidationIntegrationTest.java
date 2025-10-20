// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.test.assertions.FunctionAssertions.*;
import static org.higherkindedj.hkt.test.assertions.KindAssertions.*;
import static org.higherkindedj.hkt.test.assertions.TypeClassAssertions.*;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validation Integration Tests")
class ValidationIntegrationTest {
  private static final class StateT {}

  @Nested
  @DisplayName("Monad operation validation workflow")
  class MonadOperationValidation {
    @Test
    @DisplayName("should validate complete map operation")
    void shouldValidateCompleteMapOperation() {
      // Given
      var monad = EitherMonad.<String>instance();
      var kind = monad.of("test");
      Function<String, Integer> mapper = String::length;

      // When - validate all components
      var validatedKind = Validation.kind().requireNonNull(kind, StateT.class, MAP);
      var validatedMapper = Validation.function().requireMapper(mapper, "f", StateT.class, MAP);

      // Then
      assertThat(validatedKind).isNotNull();
      assertThat(validatedMapper).isNotNull();

      // Verify operation works
      var result = monad.map(validatedMapper, validatedKind);
      var either = EITHER.<String, Integer>narrow(result);
      assertThat(either.getRight()).isEqualTo(4);
    }

    @Test
    @DisplayName("should validate complete flatMap operation")
    void shouldValidateCompleteFlatMapOperation() {
      // Given
      var monad = EitherMonad.<String>instance();
      var kind = monad.of("test");
      Function<String, Kind<EitherKind.Witness<String>, Integer>> flatMapper =
          s -> EITHER.widen(Either.right(s.length()));

      // When - validate all components
      var validatedKind = Validation.kind().requireNonNull(kind, StateT.class, FLAT_MAP);
      var validatedFlatMapper =
          Validation.function().requireFlatMapper(flatMapper, "f", StateT.class, FLAT_MAP);

      // Then
      assertThat(validatedKind).isNotNull();
      assertThat(validatedFlatMapper).isNotNull();

      // Verify operation works
      var result = monad.flatMap(validatedFlatMapper, validatedKind);
      var validatedResult =
          Validation.function()
              .requireNonNullResult(result, "f", StateT.class, FLAT_MAP, Kind.class);
      var either = EITHER.<String, Integer>narrow(validatedResult);
      assertThat(either.getRight()).isEqualTo(4);
    }

    @Test
    @DisplayName("should detect null result from flatMap")
    void shouldDetectNullResultFromFlatMap() {
      // Given
      var monad = EitherMonad.<String>instance();
      var kind = monad.of("test");
      Function<String, Kind<EitherKind.Witness<String>, Integer>> badFlatMapper = s -> null;

      // When
      var result = monad.flatMap(badFlatMapper, kind);

      // Then
      org.assertj.core.api.Assertions.assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> Validation.function().requireNonNullResult(result, "f", StateT.class, FLAT_MAP))
          .withMessageContaining("returned null");
    }
  }

  @Nested
  @DisplayName("Applicative validation workflow")
  class ApplicativeValidationWorkflow {
    @Test
    @DisplayName("should validate complete ap operation")
    void shouldValidateCompleteApOperation() {
      // Given
      var applicative = EitherMonad.<String>instance();
      Kind<EitherKind.Witness<String>, Function<String, Integer>> ff =
          EITHER.widen(Either.right(String::length));
      Kind<EitherKind.Witness<String>, String> fa = EITHER.widen(Either.right("test"));

      // When - validate all components
      var validatedFf = Validation.kind().requireNonNull(ff, StateT.class, AP, "function");
      var validatedFa = Validation.kind().requireNonNull(fa, StateT.class, AP, "argument");

      // Then
      assertThat(validatedFf).isNotNull();
      assertThat(validatedFa).isNotNull();

      // Verify operation works
      var result = applicative.ap(validatedFf, validatedFa);
      var either = EITHER.<String, Integer>narrow(result);
      assertThat(either.getRight()).isEqualTo(4);
    }

    @Test
    @DisplayName("should distinguish between function and argument in ap")
    void shouldDistinguishBetweenFunctionAndArgument() {
      // When / Then - function parameter
      assertApFunctionKindNull(
          () -> Validation.kind().requireNonNull(null, StateT.class, AP, "function"), StateT.class);

      // When / Then - argument parameter
      assertApArgumentKindNull(
          () -> Validation.kind().requireNonNull(null, StateT.class, AP, "argument"), StateT.class);
    }

    @Test
    @DisplayName("should validate all applicative operations")
    void shouldValidateAllApplicativeOperations() {
      // Given
      var applicative = EitherMonad.<String>instance();
      var validKind = EITHER.widen(Either.<String, Integer>right(42));
      var validKind2 = EITHER.widen(Either.<String, Integer>right(24));
      Function<Integer, String> validMapper = Object::toString;
      var validFunctionKind =
          EITHER.widen(Either.<String, Function<Integer, String>>right(validMapper));
      BiFunction<Integer, Integer, String> validCombiningFunction =
          (a, b) -> a + "," + b;

      // When / Then
      assertAllApplicativeOperations(
          applicative,
          Either.class,
          validKind,
          validKind2,
          validMapper,
          validFunctionKind,
          validCombiningFunction);
    }
  }

  @Nested
  @DisplayName("Monad error validation workflow")
  class MonadErrorValidationWorkflow {
    @Test
    @DisplayName("should validate all monad error operations")
    void shouldValidateAllMonadErrorOperations() {
      // Given
      var monadError = EitherMonad.<String>instance();
      var validKind = EITHER.widen(Either.<String, Integer>right(42));
      Function<Integer, String> validMapper = Object::toString;
      Function<Integer, Kind<EitherKind.Witness<String>, String>> validFlatMapper =
          i -> EITHER.widen(Either.right(i.toString()));
      var validFunctionKind =
          EITHER.widen(Either.<String, Function<Integer, String>>right(validMapper));
      Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler =
          err -> EITHER.widen(Either.right(-1));
      var validFallback = EITHER.widen(Either.<String, Integer>right(-999));

      // When / Then
      assertAllMonadErrorOperations(
          monadError,
          Either.class,
          validKind,
          validMapper,
          validFlatMapper,
          validFunctionKind,
          validHandler,
          validFallback);
    }

    @Test
    @DisplayName("should validate handleErrorWith operation")
    void shouldValidateHandleErrorWithOperation() {
      // Given
      var monadError = EitherMonad.<String>instance();
      var validKind = EITHER.widen(Either.<String, Integer>left("error"));
      Function<String, Kind<EitherKind.Witness<String>, Integer>> validHandler =
          err -> EITHER.widen(Either.right(-1));

      // When / Then - validate Kind parameter
      assertMonadErrorHandleErrorWithKindNull(
          () -> monadError.handleErrorWith(null, validHandler), StateT.class);

      // When / Then - validate handler parameter
      assertMonadErrorHandleErrorWithHandlerNull(
          () -> monadError.handleErrorWith(validKind, null), StateT.class);
    }
  }

  @Nested
  @DisplayName("Core type validation workflow")
  class CoreTypeValidationWorkflow {
    @Test
    @DisplayName("should validate Either construction")
    void shouldValidateEitherConstruction() {
      // Given
      var leftValue = "error";
      var rightValue = 42;

      // When - validate left value
      var validatedLeft = Validation.coreType().requireValue(leftValue, Either.class, LEFT);
      assertThat(validatedLeft).isEqualTo(leftValue);

      // When - validate right value
      var validatedRight = Validation.coreType().requireValue(rightValue, Either.class, RIGHT);
      assertThat(validatedRight).isEqualTo(rightValue);
    }

    @Test
    @DisplayName("should validate error values")
    void shouldValidateErrorValues() {
      // Given
      var error = new RuntimeException("test error");

      // When
      var validatedError = Validation.coreType().requireError(error, Either.class, LEFT);

      // Then
      assertThat(validatedError).isEqualTo(error);
    }
  }

  @Nested
  @DisplayName("Combined validation scenarios")
  class CombinedValidationScenarios {
    @Test
    @DisplayName("should validate entire monad transformer stack")
    void shouldValidateEntireMonadTransformerStack() {
      // Given - building with EitherMonad
      var outerMonad = EitherMonad.<String>instance();
      Function<String, Kind<EitherKind.Witness<String>, Integer>> runFn =
          s -> EITHER.widen(Either.right(s.length()));
      var initialState = "initial";
      Function<Integer, Kind<EitherKind.Witness<String>, String>> mapper =
          i -> EITHER.widen(Either.right(String.valueOf(i)));

      // When - validate all components in order
      var validatedOuterMonad =
          Validation.transformer().requireOuterMonad(outerMonad, StateT.class, CONSTRUCTION);

      var validatedRunFn =
          Validation.function().requireFunction(runFn, "runStateTFn", StateT.class, CONSTRUCTION);

      var validatedInitialState =
          Validation.transformer()
              .requireTransformerComponent(
                  initialState, "initial state", StateT.class, CONSTRUCTION);

      var validatedMapper = Validation.function().requireMapper(mapper, "f", StateT.class, MAP);

      // Then - all validations pass
      assertThat(validatedOuterMonad).isNotNull();
      assertThat(validatedRunFn).isNotNull();
      assertThat(validatedInitialState).isNotNull();
      assertThat(validatedMapper).isNotNull();

      // Verify the stack works
      var result = validatedRunFn.apply(validatedInitialState);
      var either = EITHER.<String, Integer>narrow(result);
      assertThat(either.getRight()).isEqualTo(7); // "initial".length()
    }

    @Test
    @DisplayName("should validate operations across different validator types")
    void shouldValidateOperationsAcrossDifferentValidatorTypes() {
      // Given
      var monad = EitherMonad.<String>instance();
      var kind = monad.of("test");
      Function<String, Integer> mapper = String::length;
      var value = 42;

      // When - use all validator types together
      var validatedKind = Validation.kind().requireNonNull(kind, StateT.class, MAP);
      var validatedMapper = Validation.function().requireMapper(mapper, "f", StateT.class, MAP);
      var validatedValue = Validation.coreType().requireValue(value, StateT.class, OF);
      var validatedMonad =
          Validation.transformer().requireOuterMonad(monad, StateT.class, CONSTRUCTION);

      // Then
      assertThat(validatedKind).isNotNull();
      assertThat(validatedMapper).isNotNull();
      assertThat(validatedValue).isEqualTo(42);
      assertThat(validatedMonad).isNotNull();
    }
  }

  @Nested
  @DisplayName("Validation facade integration")
  class ValidationFacadeIntegration {
    @Test
    @DisplayName("should access all validators through Validation facade")
    void shouldAccessAllValidatorsThroughValidationFacade() {
      // When
      var coreType = Validation.coreType();
      var function = Validation.function();
      var kind = Validation.kind();
      var transformer = Validation.transformer();

      // Then
      assertThat(coreType).isNotNull();
      assertThat(function).isNotNull();
      assertThat(kind).isNotNull();
      assertThat(transformer).isNotNull();

      // Verify they're the correct singleton instances
      assertThat(coreType).isSameAs(Validation.coreType());
      assertThat(function).isSameAs(Validation.function());
      assertThat(kind).isSameAs(Validation.kind());
      assertThat(transformer).isSameAs(Validation.transformer());
    }

    @Test
    @DisplayName("should use Validation facade in realistic scenario")
    void shouldUseValidationFacadeInRealisticScenario() {
      // Given - simulating a real monad transformer operation
      var monad = EitherMonad.<String>instance();
      var kind = monad.of("hello");
      Function<String, Kind<EitherKind.Witness<String>, Integer>> flatMapper =
          s -> EITHER.widen(Either.right(s.length()));

      // When - validate using the facade
      Validation.kind().requireNonNull(kind, StateT.class, FLAT_MAP, "source");
      Validation.function().requireFlatMapper(flatMapper, "f", StateT.class, FLAT_MAP);
      Validation.transformer().requireOuterMonad(monad, StateT.class, CONSTRUCTION);

      var result = monad.flatMap(flatMapper, kind);

      Validation.function().requireNonNullResult(result, "f", StateT.class, FLAT_MAP);

      // Then
      var either = EITHER.<String, Integer>narrow(result);
      assertThat(either.getRight()).isEqualTo(5);
    }
  }
}
