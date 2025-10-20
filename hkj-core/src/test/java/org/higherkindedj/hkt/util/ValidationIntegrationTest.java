// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validation Integration Tests")
class ValidationIntegrationTest {

  private static final class TestWitness {}

  private record TestKind<A>(A value) implements Kind<TestWitness, A> {}

  private static final class TestMonad implements Monad<TestWitness> {

    @Override
    public <A> Kind<TestWitness, A> of(A value) {
      return new TestKind<>(value);
    }

    @Override
    public <A, B> Kind<TestWitness, B> ap(
        Kind<TestWitness, ? extends Function<A, B>> ff, Kind<TestWitness, A> fa) {
      return null;
    }

    @Override
    public <A, B> Kind<TestWitness, B> map(
        Function<? super A, ? extends B> f, Kind<TestWitness, A> fa) {
      TestKind<A> kind = (TestKind<A>) fa;
      return new TestKind<>(f.apply(kind.value()));
    }

    @Override
    public <A, B> Kind<TestWitness, B> flatMap(
        Function<? super A, ? extends Kind<TestWitness, B>> f, Kind<TestWitness, A> ma) {
      TestKind<A> kind = (TestKind<A>) ma;
      return f.apply(kind.value());
    }
  }

  private static final class StateT {}

  @Nested
  @DisplayName("Monad operation validation workflow")
  class MonadOperationValidation {

    @Test
    @DisplayName("should validate complete map operation")
    void shouldValidateCompleteMapOperation() {
      // Given
      var monad = new TestMonad();
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
      var narrowed = Validation.kind().narrowWithTypeCheck(result, TestKind.class);
      assertThat(narrowed.value()).isEqualTo(4);
    }

    @Test
    @DisplayName("should validate complete flatMap operation")
    void shouldValidateCompleteFlatMapOperation() {
      // Given
      var monad = new TestMonad();
      var kind = monad.of("test");
      Function<String, Kind<TestWitness, Integer>> flatMapper = s -> new TestKind<>(s.length());

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
              .requireNonNullResult(result, "f", StateT.class, FLAT_MAP, TestKind.class);
      var narrowed = Validation.kind().narrowWithTypeCheck(validatedResult, TestKind.class);
      assertThat(narrowed.value()).isEqualTo(4);
    }

    @Test
    @DisplayName("should detect null result from flatMap")
    void shouldDetectNullResultFromFlatMap() {
      // Given
      var monad = new TestMonad();
      var kind = monad.of("test");
      Function<String, Kind<TestWitness, Integer>> badFlatMapper = s -> null;

      // When / Then
      var result = monad.flatMap(badFlatMapper, kind);
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> Validation.function().requireNonNullResult(result, "f", StateT.class, FLAT_MAP))
          .withMessageContaining("returned null");
    }
  }

  @Nested
  @DisplayName("Transformer construction validation workflow")
  class TransformerConstructionValidation {

    @Test
    @DisplayName("should validate transformer construction with all components")
    void shouldValidateTransformerConstructionWithAllComponents() {
      // Given
      var outerMonad = new TestMonad();
      Function<String, Kind<TestWitness, Integer>> stateFunction = s -> new TestKind<>(s.length());
      var initialState = "initial";

      // When - validate all construction components
      var validatedMonad =
          Validation.transformer().requireOuterMonad(outerMonad, StateT.class, CONSTRUCTION);
      var validatedFunction =
          Validation.function()
              .requireFunction(stateFunction, "runStateTFn", StateT.class, CONSTRUCTION);
      var validatedState =
          Validation.transformer()
              .requireTransformerComponent(
                  initialState, "initial state", StateT.class, CONSTRUCTION);

      // Then
      assertThat(validatedMonad).isNotNull();
      assertThat(validatedFunction).isNotNull();
      assertThat(validatedState).isNotNull();
    }

    @Test
    @DisplayName("should fail when outer monad is null")
    void shouldFailWhenOuterMonadIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> Validation.transformer().requireOuterMonad(null, StateT.class, CONSTRUCTION))
          .withMessageContaining("Monad")
          .withMessageContaining("StateT");
    }

    @Test
    @DisplayName("should fail when transformer component is null")
    void shouldFailWhenTransformerComponentIsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Validation.transformer()
                      .requireTransformerComponent(null, "state function", StateT.class, STATE_T))
          .withMessageContaining("state function")
          .withMessageContaining("StateT.stateT");
    }
  }

  @Nested
  @DisplayName("Applicative validation workflow")
  class ApplicativeValidationWorkflow {

    private static final class TestApplicative implements Applicative<TestWitness> {

      @Override
      public <A> Kind<TestWitness, A> of(A value) {
        return new TestKind<>(value);
      }

      @Override
      public <A, B> Kind<TestWitness, B> ap(
          Kind<TestWitness, ? extends Function<A, B>> ff, Kind<TestWitness, A> fa) {
        TestKind<? extends Function<A, B>> fKind = (TestKind<? extends Function<A, B>>) ff;
        TestKind<A> aKind = (TestKind<A>) fa;
        return new TestKind<>(fKind.value().apply(aKind.value()));
      }

      @Override
      public <A, B> Kind<TestWitness, B> map(
          Function<? super A, ? extends B> f, Kind<TestWitness, A> fa) {
        TestKind<A> kind = (TestKind<A>) fa;
        return new TestKind<>(f.apply(kind.value()));
      }
    }

    @Test
    @DisplayName("should validate complete ap operation")
    void shouldValidateCompleteApOperation() {
      // Given
      var applicative = new TestApplicative();
      Kind<TestWitness, Function<String, Integer>> ff = new TestKind<>(String::length);
      Kind<TestWitness, String> fa = new TestKind<>("test");

      // When - validate all components
      var validatedFf = Validation.kind().requireNonNull(ff, StateT.class, AP, "function");
      var validatedFa = Validation.kind().requireNonNull(fa, StateT.class, AP, "argument");

      // Then
      assertThat(validatedFf).isNotNull();
      assertThat(validatedFa).isNotNull();

      // Verify operation works
      var result = applicative.ap(validatedFf, validatedFa);
      var narrowed = Validation.kind().narrowWithTypeCheck(result, TestKind.class);
      assertThat(narrowed.value()).isEqualTo(4);
    }

    @Test
    @DisplayName("should distinguish between function and argument in ap")
    void shouldDistinguishBetweenFunctionAndArgument() {
      // When / Then - function parameter
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.kind().requireNonNull(null, StateT.class, AP, "function"))
          .withMessage("Kind for StateT.ap (function) cannot be null");

      // When / Then - argument parameter
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.kind().requireNonNull(null, StateT.class, AP, "argument"))
          .withMessage("Kind for StateT.ap (argument) cannot be null");
    }

    @Test
    @DisplayName("should validate traverse operation with applicative")
    void shouldValidateTraverseOperationWithApplicative() {
      // Given
      var applicative = new TestApplicative();
      Function<String, Kind<TestWitness, Integer>> traverseFn = s -> new TestKind<>(s.length());

      // When - validate components
      var validatedApplicative =
          Validation.function()
              .requireApplicative(applicative, "applicative", StateT.class, TRAVERSE);
      var validatedFn =
          Validation.function().requireMapper(traverseFn, "f", StateT.class, TRAVERSE);

      // Then
      assertThat(validatedApplicative).isNotNull();
      assertThat(validatedFn).isNotNull();
    }
  }

  @Nested
  @DisplayName("Core type validation workflow")
  class CoreTypeValidationWorkflow {

    private static final class Either {}

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

    @Test
    @DisplayName("should validate with custom value names")
    void shouldValidateWithCustomValueNames() {
      // Given
      var value = "test";

      // When
      var validated = Validation.coreType().requireValue(value, "customName", Either.class, RIGHT);

      // Then
      assertThat(validated).isEqualTo(value);
    }
  }

  @Nested
  @DisplayName("Combined validation scenarios")
  class CombinedValidationScenarios {

    @Test
    @DisplayName("should validate entire monad transformer stack")
    void shouldValidateEntireMonadTransformerStack() {
      // Given - building a monad transformer
      var outerMonad = new TestMonad();
      Function<String, Kind<TestWitness, Integer>> runFn = s -> new TestKind<>(s.length());
      var initialState = "initial";
      Function<Integer, Kind<TestWitness, String>> mapper = i -> new TestKind<>(String.valueOf(i));

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
      var narrowed = Validation.kind().narrowWithTypeCheck(result, TestKind.class);
      assertThat(narrowed.value()).isEqualTo(7); // "initial".length()
    }

    @Test
    @DisplayName("should provide clear error messages throughout validation chain")
    void shouldProvideClearErrorMessagesThroughoutValidationChain() {
      // Scenario 1: Null outer monad
      assertThatNullPointerException()
          .isThrownBy(
              () -> Validation.transformer().requireOuterMonad(null, StateT.class, CONSTRUCTION))
          .withMessageContaining("Transformer Monad")
          .withMessageContaining("StateT construction");

      // Scenario 2: Null function
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Validation.function()
                      .requireFunction(null, "runStateTFn", StateT.class, CONSTRUCTION))
          .withMessageContaining("Function runStateTFn")
          .withMessageContaining("StateT.construction");

      // Scenario 3: Null Kind
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.kind().requireNonNull(null, StateT.class, MAP))
          .withMessageContaining("Kind")
          .withMessageContaining("StateT.map");

      // Scenario 4: Null result from function
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () -> Validation.function().requireNonNullResult(null, "f", StateT.class, FLAT_MAP))
          .withMessageContaining("Function f")
          .withMessageContaining("StateT.flatMap")
          .withMessageContaining("returned null");
    }

    @Test
    @DisplayName("should validate operations across different validator types")
    void shouldValidateOperationsAcrossDifferentValidatorTypes() {
      // Given
      var monad = new TestMonad();
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
      var monad = new TestMonad();
      var kind = monad.of("hello");
      Function<String, Kind<TestWitness, Integer>> flatMapper = s -> new TestKind<>(s.length());

      // When - validate using the facade
      Validation.kind().requireNonNull(kind, StateT.class, FLAT_MAP, "source");
      Validation.function().requireFlatMapper(flatMapper, "f", StateT.class, FLAT_MAP);
      Validation.transformer().requireOuterMonad(monad, StateT.class, CONSTRUCTION);

      var result = monad.flatMap(flatMapper, kind);

      Validation.function().requireNonNullResult(result, "f", StateT.class, FLAT_MAP);

      // Then
      var narrowed = Validation.kind().narrowWithTypeCheck(result, TestKind.class);
      assertThat(narrowed.value()).isEqualTo(5);
    }
  }
}
