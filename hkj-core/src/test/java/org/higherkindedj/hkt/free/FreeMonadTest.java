// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FreeMonad Complete Test Suite")
class FreeMonadTest extends FreeTestBase {

  private FreeMonad<IdentityKind.Witness> monad;

  @BeforeEach
  void setUpMonad() {
    monad = new FreeMonad<>();
    validateMonadFixtures();
  }

  @Nested
  @DisplayName("Complete Monad Test Suite")
  class CompleteMonadTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      // Note: We skip exception propagation tests (.testExceptions()) because
      // Free monad is lazy - it builds program structures without executing them.
      // Exceptions are only thrown during interpretation (foldMap), not during
      // program construction (map/flatMap), which is the correct behavior for
      // lazy evaluation.

      // Test operations
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();

      // Test validations
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(FreeFunctor.class)
          .withApFrom(FreeMonad.class)
          .withFlatMapFrom(FreeMonad.class)
          .testValidations();

      // Test laws
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(FreeMonadTest.class);

      if (result.hasErrors()) {
        result.printReport();
        throw new AssertionError("Test structure validation failed");
      }
    }
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("pure() creates a Pure Free monad")
    void pureCreatesPureFree() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> result = monad.of(42);

      Free<IdentityKind.Witness, Integer> free = narrowToFree(result);
      assertThat(free).isInstanceOf(Free.Pure.class);

      Integer value = runFree(free);
      assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("map() transforms the result value")
    void mapTransformsResultValue() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, String> result =
          monad.map(validMapper, validKind);

      String value = runFree(narrowToFree(result));
      assertThat(value).isEqualTo("mapped:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("flatMap() sequences computations")
    void flatMapSequencesComputations() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, String> result =
          monad.flatMap(validFlatMapper, validKind);

      Free<IdentityKind.Witness, String> free = narrowToFree(result);
      assertThat(free).isInstanceOf(Free.FlatMapped.class);

      String value = runFree(free);
      assertThat(value).isEqualTo("flatMapped:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("ap() applies wrapped function to wrapped value")
    void apAppliesFunctionToValue() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, String> result =
          monad.ap(validFunctionKind, validKind);

      String value = runFree(narrowToFree(result));
      assertThat(value).isEqualTo("function:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("map2() combines two Free values")
    void map2CombinesTwoValues() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, String> result =
          monad.map2(validKind, validKind2, validCombiningFunction);

      String value = runFree(narrowToFree(result));
      assertThat(value).isEqualTo("combined:" + DEFAULT_VALUE + "," + ALTERNATIVE_VALUE);
    }
  }

  @Nested
  @DisplayName("Stack Safety Tests")
  class StackSafetyTests {

    @Test
    @DisplayName("Deep flatMap chaining is stack-safe")
    void deepFlatMapChainingIsStackSafe() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> start = monad.of(0);

      // Create a very deep chain of flatMaps
      Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        final int increment = 1;
        result = monad.flatMap(x -> monad.of(x + increment), result);
      }

      // This should not cause StackOverflowError
      Integer value = runFree(narrowToFree(result));
      assertThat(value).isEqualTo(10000);
    }

    @Test
    @DisplayName("Deep map chaining is stack-safe")
    void deepMapChainingIsStackSafe() {
      Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> start = monad.of(0);

      // Create a very deep chain of maps
      Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = monad.map(x -> x + 1, result);
      }

      // This should not cause StackOverflowError
      Integer value = runFree(narrowToFree(result));
      assertThat(value).isEqualTo(10000);
    }
  }

  @Nested
  @DisplayName("Free Monad Specific Tests")
  class FreeMonadSpecificTests {

    @Test
    @DisplayName("liftF creates a suspended computation")
    void liftFCreatesSuspendedComputation() {
      Free<IdentityKind.Witness, Integer> free =
          Free.liftF(
              org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY.widen(
                  new org.higherkindedj.hkt.free.test.Identity<>(42)),
              org.higherkindedj.hkt.free.test.IdentityMonad.INSTANCE);

      assertThat(free).isInstanceOf(Free.Suspend.class);

      Integer value = runFree(free);
      assertThat(value).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMap interprets Free program")
    void foldMapInterpretsProgram() {
      // Create a simple Free program
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(10)
              .flatMap(x -> Free.pure(x * 2))
              .flatMap(x -> Free.pure(x + 5));

      Integer result = runFree(program);
      assertThat(result).isEqualTo(25); // (10 * 2) + 5
    }

    @Test
    @DisplayName("Multiple flatMaps create FlatMapped structure")
    void multipleFlatMapsCreateFlatMappedStructure() {
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(1)
              .flatMap(x -> Free.pure(x + 1))
              .flatMap(x -> Free.pure(x + 1));

      // The outer structure should be FlatMapped
      assertThat(program).isInstanceOf(Free.FlatMapped.class);

      Integer result = runFree(program);
      assertThat(result).isEqualTo(3);
    }

    @Test
    @DisplayName("Exceptions are propagated during interpretation, not construction")
    void exceptionsPropagateDuringInterpretation() {
      RuntimeException testException = new RuntimeException("Test exception");

      // Building the program with a throwing function should NOT throw
      Free<IdentityKind.Witness, String> program =
          Free.<IdentityKind.Witness, Integer>pure(42)
              .map(
                  i -> {
                    throw testException;
                  });

      // The program is built successfully - Free is lazy
      assertThat(program).isInstanceOf(Free.FlatMapped.class);

      // The exception is only thrown during interpretation (foldMap)
      assertThatThrownBy(() -> runFree(program))
          .isSameAs(testException)
          .hasMessage("Test exception");
    }

    @Test
    @DisplayName("suspend creates a Suspend structure")
    void suspendCreatesSuspendStructure() {
      Kind<IdentityKind.Witness, Free<IdentityKind.Witness, Integer>> wrapped =
          org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY.widen(
              new org.higherkindedj.hkt.free.test.Identity<>(Free.pure(100)));

      Free<IdentityKind.Witness, Integer> free = Free.suspend(wrapped);

      assertThat(free).isInstanceOf(Free.Suspend.class);

      Integer result = runFree(free);
      assertThat(result).isEqualTo(100);
    }

    @Test
    @DisplayName("Complex nested flatMap program")
    void complexNestedFlatMapProgram() {
      // Create a more complex program to cover more branches in foldMap
      Free<IdentityKind.Witness, Integer> program =
          Free.<IdentityKind.Witness, Integer>pure(1)
              .flatMap(
                  x ->
                      Free.<IdentityKind.Witness, Integer>pure(x + 1)
                          .flatMap(
                              y ->
                                  Free.<IdentityKind.Witness, Integer>pure(y + 1)
                                      .flatMap(
                                          z ->
                                              Free.<IdentityKind.Witness, Integer>pure(z + 1)
                                                  .flatMap(
                                                      w ->
                                                          Free.<IdentityKind.Witness, Integer>pure(
                                                              w + 1)))));

      Integer result = runFree(program);
      assertThat(result).isEqualTo(5);
    }

    @Test
    @DisplayName("Nested Suspend and FlatMapped combinations")
    void nestedSuspendAndFlatMapped() {
      // Create a Suspend containing a FlatMapped Free
      Kind<IdentityKind.Witness, Free<IdentityKind.Witness, Integer>> wrapped =
          org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY.widen(
              new org.higherkindedj.hkt.free.test.Identity<>(
                  Free.<IdentityKind.Witness, Integer>pure(5)
                      .flatMap(x -> Free.<IdentityKind.Witness, Integer>pure(x * 2))));

      Free<IdentityKind.Witness, Integer> suspend = Free.suspend(wrapped);
      Free<IdentityKind.Witness, Integer> program =
          suspend.flatMap(x -> Free.<IdentityKind.Witness, Integer>pure(x + 3));

      Integer result = runFree(program);
      assertThat(result).isEqualTo(13); // (5 * 2) + 3
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(FreeFunctor.class)
          .withApFrom(FreeMonad.class)
          .withFlatMapFrom(FreeMonad.class)
          .testValidations();
    }

    // Exception propagation tests are not applicable to Free monad because it's lazy.
    // Free monad builds program structures without executing functions, so exceptions
    // are only thrown during interpretation (foldMap), not during construction.
    // This is the correct and expected behavior for lazy evaluation.
    //
    // @Test
    // @DisplayName("Test exception propagation only")
    // void testExceptionPropagationOnly() {
    //     TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
    //         .<Integer>instance(monad)
    //         .<String>withKind(validKind)
    //         .withMonadOperations(
    //             validKind2, validMapper, validFlatMapper, validFunctionKind,
    // validCombiningFunction
    //         )
    //         .testExceptions();
    // }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<FreeKind.Witness<IdentityKind.Witness>>monad(FreeMonad.class)
          .<Integer>instance(monad)
          .<String>withKind(validKind)
          .withMonadOperations(
              validKind2, validMapper, validFlatMapper, validFunctionKind, validCombiningFunction)
          .withLawsTesting(testValue, testFunction, chainFunction, equalityChecker)
          .testLaws();
    }
  }
}
