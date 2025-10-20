// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.test.validation.TestPatternValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherTraverse Complete Test Suite")
class EitherTraverseTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

  private static final String ERROR_VALUE = "TestError";
  private static final Integer SUCCESS_VALUE = 42;

  private Traverse<EitherKind.Witness<String>> traverse;
  private Applicative<MaybeKind.Witness> maybeApplicative;
  private Kind<EitherKind.Witness<String>, Integer> rightKind;
  private Kind<EitherKind.Witness<String>, Integer> leftKind;
  private Function<Integer, Kind<MaybeKind.Witness, String>> validTraverseFunction;
  private Monoid<String> validMonoid;
  private Function<Integer, String> validFoldMapFunction;
  private BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> maybeEqualityChecker;

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
    return EITHER.widen(Either.right(SUCCESS_VALUE));
  }

  @Override
  protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
    return EITHER.widen(Either.right(24));
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return TestFunctions.INT_TO_STRING;
  }

  @Override
  protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> EITHER.narrow(k1).equals(EITHER.narrow(k2));
  }

  @BeforeEach
  void setUpTraverse() {
    traverse = EitherTraverse.instance();
    maybeApplicative = MaybeMonad.INSTANCE;
    rightKind = createValidKind();
    leftKind = EITHER.widen(Either.left(ERROR_VALUE));
    validTraverseFunction = i -> MAYBE.widen(Maybe.just("Traversed:" + i));
    validMonoid = Monoids.string();
    validFoldMapFunction = TestFunctions.INT_TO_STRING;
    maybeEqualityChecker = (k1, k2) -> MAYBE.narrow(k1).equals(MAYBE.narrow(k2));

    validateRequiredFixtures();
  }

  @Nested
  @DisplayName("Complete Traverse Test Suite")
  class CompleteTraverseTestSuite {

    @Test
    @DisplayName("Run complete Traverse test pattern")
    void runCompleteTraverseTestPattern() {
      TypeClassTest.<EitherKind.Witness<String>>traverse(EitherTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(rightKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, validTraverseFunction) // G inferred here
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .withEqualityChecker(maybeEqualityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Validate test structure follows standards")
    void validateTestStructure() {
      TestPatternValidator.ValidationResult result =
          TestPatternValidator.validateAndReport(EitherTraverseTest.class);

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
    @DisplayName("traverse() on Right with successful function")
    void traverseRightSuccessful() {
      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("Traversed:" + i));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, rightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("Traversed:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() on Right with failing function")
    void traverseRightFailing() {
      Function<Integer, Kind<MaybeKind.Witness, String>> failingFunc =
          i -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, failingFunc, rightKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("traverse() on Left preserves Left in applicative context")
    void traverseLeftPreservesError() {
      Function<Integer, Kind<MaybeKind.Witness, String>> traverseFunc =
          i -> MAYBE.widen(Maybe.just("Traversed:" + i));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, leftKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, String> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Right applies function")
    void foldMapOnRightAppliesFunction() {
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = traverse.foldMap(stringMonoid, foldFunction, rightKind);

      assertThat(result).isEqualTo("Value:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("foldMap() on Left returns monoid empty")
    void foldMapOnLeftReturnsEmpty() {
      Monoid<String> stringMonoid = Monoids.string();
      Function<Integer, String> foldFunction = i -> "Value:" + i;

      String result = traverse.foldMap(stringMonoid, foldFunction, leftKind);

      assertThat(result).isEqualTo(stringMonoid.empty());
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>traverse(EitherTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(rightKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, validTraverseFunction) // G inferred here
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>traverse(EitherTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(rightKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, validTraverseFunction) // G inferred here
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<EitherKind.Witness<String>>traverse(EitherTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(rightKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, validTraverseFunction) // G inferred here
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .testExceptions();
    }

    @Test
    @DisplayName("Test laws only")
    void testLawsOnly() {
      TypeClassTest.<EitherKind.Witness<String>>traverse(EitherTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(rightKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, validTraverseFunction) // G inferred here
          .withFoldableOperations(validMonoid, validFoldMapFunction)
          .withEqualityChecker(maybeEqualityChecker)
          .testLaws();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("traverse() with null values in Right")
    void traverseWithNullValuesInRight() {
      Kind<EitherKind.Witness<String>, Integer> rightNull = EITHER.widen(Either.right(null));

      Function<Integer, Kind<MaybeKind.Witness, String>> nullSafeTraverse =
          i -> MAYBE.widen(Maybe.just(i == null ? "null" : i.toString()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, nullSafeTraverse, rightNull);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("null");
    }

    @Test
    @DisplayName("traverse() with conditional function")
    void traverseWithConditionalFunction() {
      Function<Integer, Kind<MaybeKind.Witness, String>> conditionalFunc =
          i -> i > 50 ? MAYBE.widen(Maybe.just(i.toString())) : MAYBE.widen(Maybe.nothing());

      // Should fail because 42 <= 50
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> failResult =
          traverse.traverse(maybeApplicative, conditionalFunc, rightKind);
      assertThat(MAYBE.narrow(failResult).isNothing()).isTrue();

      // Should succeed with value > 50
      Kind<EitherKind.Witness<String>, Integer> bigRight = EITHER.widen(Either.right(100));
      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> successResult =
          traverse.traverse(maybeApplicative, conditionalFunc, bigRight);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(successResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight()).isEqualTo("100");
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      // Integer addition
      Monoid<Integer> intAddition = Monoids.integerAddition();
      Function<Integer, Integer> doubleFunc = i -> i * 2;
      Integer intResult = traverse.foldMap(intAddition, doubleFunc, rightKind);
      assertThat(intResult).isEqualTo(SUCCESS_VALUE * 2);

      // Boolean AND
      Monoid<Boolean> andMonoid = Monoids.booleanAnd();
      Function<Integer, Boolean> isPositive = i -> i > 0;
      Boolean andResult = traverse.foldMap(andMonoid, isPositive, rightKind);
      assertThat(andResult).isTrue();
    }

    @Test
    @DisplayName("sequenceA() turns Right<Just<A>> into Just<Right<A>>")
    void sequenceRightJustToJustRight() {
      Kind<MaybeKind.Witness, Integer> maybeKind = MAYBE.widen(Maybe.just(SUCCESS_VALUE));
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> input =
          EITHER.widen(Either.right(maybeKind));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, input);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(SUCCESS_VALUE);
    }

    @Test
    @DisplayName("sequenceA() preserves Left values")
    void sequenceLeftPreservesError() {
      Kind<EitherKind.Witness<String>, Kind<MaybeKind.Witness, Integer>> leftInput =
          EITHER.widen(Either.left(ERROR_VALUE));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, Integer>> result =
          traverse.sequenceA(maybeApplicative, leftInput);

      Maybe<Kind<EitherKind.Witness<String>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();

      Either<String, Integer> either = EITHER.narrow(maybe.get());
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(ERROR_VALUE);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    @DisplayName("traverse() efficient with Left values")
    void traverseEfficientWithLeftValues() {
      if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
        // Left values should not traverse, so even expensive functions are safe
        Function<Integer, Kind<MaybeKind.Witness, String>> expensiveFunc =
            i -> MAYBE.widen(Maybe.just("expensive:" + i));

        Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
            traverse.traverse(maybeApplicative, expensiveFunc, leftKind);

        // Should complete quickly without calling expensive function
        Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
        assertThat(EITHER.<String, String>narrow(maybe.get()).getLeft()).isEqualTo(ERROR_VALUE);
      }
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("traverse() integrates with map")
    void traverseIntegratesWithMap() {
      Kind<EitherKind.Witness<String>, Integer> start = rightKind;

      Function<Integer, String> mapper = i -> "mapped:" + i;
      Kind<EitherKind.Witness<String>, String> mapped = traverse.map(mapper, start);

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(s.toUpperCase()));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, mapped);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("MAPPED:" + SUCCESS_VALUE);
    }

    @Test
    @DisplayName("traverse() integrates with foldMap")
    void traverseIntegratesWithFoldMap() {
      Monoid<String> stringMonoid = Monoids.string();

      // First fold, then traverse the result
      String folded = traverse.foldMap(stringMonoid, i -> "fold:" + i, rightKind);

      Kind<EitherKind.Witness<String>, String> foldedKind = EITHER.widen(Either.right(folded));

      Function<String, Kind<MaybeKind.Witness, String>> traverseFunc =
          s -> MAYBE.widen(Maybe.just("traversed:" + s));

      Kind<MaybeKind.Witness, Kind<EitherKind.Witness<String>, String>> result =
          traverse.traverse(maybeApplicative, traverseFunc, foldedKind);

      Maybe<Kind<EitherKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(EITHER.<String, String>narrow(maybe.get()).getRight())
          .isEqualTo("traversed:fold:" + SUCCESS_VALUE);
    }
  }
}
