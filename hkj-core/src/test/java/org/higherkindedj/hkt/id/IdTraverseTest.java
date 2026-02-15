// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.id;

import static org.assertj.core.api.Assertions.*;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link IdTraverse}.
 *
 * <p>The Identity monad's Traverse is trivial since it always contains exactly one value. These
 * tests verify that the traverse operations correctly apply effectful functions to the single value
 * and that foldMap correctly returns the mapped value.
 */
@DisplayName("IdTraverse Complete Test Suite")
class IdTraverseTest extends IdTestBase {

  private Traverse<IdKind.Witness> traverse;
  private Applicative<ValidatedKind.Witness<String>> validatedApplicative;
  private Kind<IdKind.Witness, Integer> testIdKind;
  private Function<Integer, Kind<ValidatedKind.Witness<String>, String>> validTraverseFunction;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;
  private BiPredicate<
          Kind<ValidatedKind.Witness<String>, ?>, Kind<ValidatedKind.Witness<String>, ?>>
      validatedEqualityChecker;

  @BeforeEach
  void setUpTraverse() {
    traverse = IdTraverse.INSTANCE;
    validatedApplicative = ValidatedMonad.instance(Monoids.string());
    testIdKind = idOf(DEFAULT_VALUE);
    validTraverseFunction =
        i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("Traversed:" + i));
    validMonoid = Monoids.string();
    validFoldMapFunction = Object::toString;
    validatedEqualityChecker =
        (k1, k2) ->
            ValidatedKindHelper.VALIDATED
                .narrow(k1)
                .equals(ValidatedKindHelper.VALIDATED.narrow(k2));

    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Traverse Test Suite")
  class CompleteTraverseTestSuite {

    @Test
    @DisplayName("Run complete Traverse test pattern")
    void runCompleteTraverseTestPattern() {
      TypeClassTest.<IdKind.Witness>traverse(IdTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(testIdKind)
          .withOperations(validMapper)
          .withApplicative(validatedApplicative, validTraverseFunction)
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .withEqualityChecker(validatedEqualityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(IdTraverseTest.class);

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
    @DisplayName("traverse() applies effectful function to the single value")
    void traverseAppliesEffectfulFunction() {
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
          i -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("Traversed:" + i));

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> result =
          traverse.traverse(validatedApplicative, traverseFunc, testIdKind);

      Validated<String, Kind<IdKind.Witness, String>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();

      Id<String> id = narrowToId(validated.get());
      assertThat(id.value()).isEqualTo("Traversed:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("traverse() propagates failure from effectful function")
    void traversePropagatesFailure() {
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> failingFunc =
          i -> ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Validation failed"));

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> result =
          traverse.traverse(validatedApplicative, failingFunc, testIdKind);

      Validated<String, Kind<IdKind.Witness, String>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("foldMap() applies function to the single value")
    void foldMapAppliesFunction() {
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = traverse.foldMap(stringMonoid, foldFunction, testIdKind);

      assertThat(result).isEqualTo("Value:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("map() applies function to the value")
    void mapAppliesFunctionToValue() {
      Kind<IdKind.Witness, String> result = traverse.map(validMapper, testIdKind);

      Id<String> id = narrowToId(result);
      assertThat(id.value()).isEqualTo(DEFAULT_VALUE.toString());
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<IdKind.Witness>traverse(IdTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(testIdKind)
          .withOperations(validMapper)
          .withApplicative(validatedApplicative, validTraverseFunction)
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<IdKind.Witness>traverse(IdTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(testIdKind)
          .withOperations(validMapper)
          .withApplicative(validatedApplicative, validTraverseFunction)
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<IdKind.Witness>traverse(IdTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(testIdKind)
          .withOperations(validMapper)
          .withApplicative(validatedApplicative, validTraverseFunction)
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<IdKind.Witness>traverse(IdTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(testIdKind)
          .withOperations(validMapper)
          .withApplicative(validatedApplicative, validTraverseFunction)
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .withEqualityChecker(validatedEqualityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("traverse() with conditional function")
    void traverseWithConditionalFunction() {
      Function<Integer, Kind<ValidatedKind.Witness<String>, String>> conditionalFunc =
          i ->
              i > 50
                  ? ValidatedKindHelper.VALIDATED.widen(Validated.valid(i.toString()))
                  : ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Value too small"));

      // DEFAULT_VALUE (42) <= 50, should fail
      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> failResult =
          traverse.traverse(validatedApplicative, conditionalFunc, testIdKind);
      Validated<String, Kind<IdKind.Witness, String>> failValidated =
          ValidatedKindHelper.VALIDATED.narrow(failResult);
      assertThat(failValidated.isInvalid()).isTrue();
      assertThat(failValidated.getError()).isEqualTo("Value too small");

      // Value > 50 should succeed
      Kind<IdKind.Witness, Integer> bigId = idOf(100);
      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> successResult =
          traverse.traverse(validatedApplicative, conditionalFunc, bigId);

      Validated<String, Kind<IdKind.Witness, String>> successValidated =
          ValidatedKindHelper.VALIDATED.narrow(successResult);
      assertThat(successValidated.isValid()).isTrue();
      assertThat(narrowToId(successValidated.get()).value()).isEqualTo("100");
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      // Integer addition
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Integer intResult = traverse.foldMap(intAddition, doubleFunc, testIdKind);
      assertThat(intResult).isEqualTo(DEFAULT_VALUE * 2);

      // Boolean AND
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Function<Integer, Boolean> isPositive = i -> i > 0;
      Boolean andResult = traverse.foldMap(andMonoid, isPositive, testIdKind);
      assertThat(andResult).isTrue();
    }

    @Test
    @DisplayName("sequenceA() turns Id<Valid<A>> into Valid<Id<A>>")
    void sequenceIdValidToValidId() {
      Kind<ValidatedKind.Witness<String>, Integer> validatedKind =
          ValidatedKindHelper.VALIDATED.widen(Validated.valid(DEFAULT_VALUE));
      Kind<IdKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input =
          idOf(validatedKind);

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, Integer>> result =
          traverse.sequenceA(validatedApplicative, input);

      Validated<String, Kind<IdKind.Witness, Integer>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();

      Id<Integer> id = narrowToId(validated.get());
      assertThat(id.value()).isEqualTo(DEFAULT_VALUE);
    }

    @Test
    @DisplayName("sequenceA() turns Id<Invalid<E>> into Invalid<E>")
    void sequenceIdInvalidToInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalidKind =
          ValidatedKindHelper.VALIDATED.widen(Validated.invalid("Error"));
      Kind<IdKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> input = idOf(invalidKind);

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, Integer>> result =
          traverse.sequenceA(validatedApplicative, input);

      Validated<String, Kind<IdKind.Witness, Integer>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("Error");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("traverse() integrates with map")
    void traverseIntegratesWithMap() {
      Kind<IdKind.Witness, Integer> start = testIdKind;

      Function<Integer, String> mapper = i -> "mapped:" + i;
      Kind<IdKind.Witness, String> mapped = traverse.map(mapper, start);

      Function<String, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
          s -> ValidatedKindHelper.VALIDATED.widen(Validated.valid(s.toUpperCase()));

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> result =
          traverse.traverse(validatedApplicative, traverseFunc, mapped);

      Validated<String, Kind<IdKind.Witness, String>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(narrowToId(validated.get()).value()).isEqualTo("MAPPED:" + DEFAULT_VALUE);
    }

    @Test
    @DisplayName("traverse() integrates with foldMap")
    void traverseIntegratesWithFoldMap() {
      Monoid<String> stringMonoid = Monoids.string();

      // First fold, then traverse the result
      String folded = traverse.foldMap(stringMonoid, i -> "fold:" + i, testIdKind);

      Kind<IdKind.Witness, String> foldedKind = idOf(folded);

      Function<String, Kind<ValidatedKind.Witness<String>, String>> traverseFunc =
          s -> ValidatedKindHelper.VALIDATED.widen(Validated.valid("traversed:" + s));

      Kind<ValidatedKind.Witness<String>, Kind<IdKind.Witness, String>> result =
          traverse.traverse(validatedApplicative, traverseFunc, foldedKind);

      Validated<String, Kind<IdKind.Witness, String>> validated =
          ValidatedKindHelper.VALIDATED.narrow(result);
      assertThat(narrowToId(validated.get()).value()).isEqualTo("traversed:fold:" + DEFAULT_VALUE);
    }
  }

  @Nested
  @DisplayName("Exactly-One Semantics Tests")
  class ExactlyOneSemanticsTests {

    @Test
    @DisplayName("Id always contains exactly one value")
    void idAlwaysContainsExactlyOneValue() {
      // Id never has "empty" case like Maybe's Nothing
      // Every traverse/foldMap always processes exactly one element

      Monoid<Integer> countingMonoid =
          new Monoid<>() {
            @Override
            public Integer empty() {
              return 0;
            }

            @Override
            public Integer combine(Integer a, Integer b) {
              return a + b;
            }
          };

      // Count elements (always 1 for Id)
      Integer count = traverse.foldMap(countingMonoid, i -> 1, testIdKind);
      assertThat(count).isEqualTo(1);

      // Same for any Id value
      Kind<IdKind.Witness, String> stringId = idOf("hello");
      Integer stringCount = traverse.foldMap(countingMonoid, s -> 1, stringId);
      assertThat(stringCount).isEqualTo(1);
    }
  }
}
