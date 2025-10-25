// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryTraverse Complete Test Suite")
class TryTraverseTest extends TryTestBase {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  private static final Monoid<String> STRING_MONOID = Monoids.string();
  private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

  private TryTraverse traverse;
  private Applicative<MaybeKind.Witness> maybeApplicative;

  @BeforeEach
  void setUpTraverse() {
    traverse = TryTraverse.INSTANCE;
    maybeApplicative = MaybeMonad.INSTANCE;
  }

  // Override to use Integer for Traverse tests
  @Override
  protected Kind<TryKind.Witness, String> createValidKind() {
    return TRY.widen(Try.success("42"));
  }

  @Override
  protected Function<String, Integer> createValidMapper() {
    return Integer::parseInt;
  }

  @Nested
  @DisplayName("Complete Traverse Test Suite")
  class CompleteTraverseTestSuite {

    @Test
    @DisplayName("Run complete Traverse test pattern")
    void runCompleteTraverseTestPattern() {
      Function<String, Kind<MaybeKind.Witness, Integer>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      // Note: We test operations and validations separately because Try's map
      // catches exceptions (converting them to Failure) rather than propagating them,
      // which differs from the standard Traverse exception propagation tests
      TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
          .<String>instance(traverse)
          .<Integer>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, traverseFunc)
          .withFoldableOperations(SUM_MONOID, validMapper)
          .testOperations();

      TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
          .<String>instance(traverse)
          .<Integer>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, traverseFunc)
          .withFoldableOperations(SUM_MONOID, validMapper)
          .testValidations();

      // Exception tests are handled separately in ExceptionHandlingTests
      // because Try has special exception handling semantics
    }
  }

  @Nested
  @DisplayName("Traverse Operation Tests")
  class TraverseOperationTests {

    @Test
    @DisplayName("traverse on Success should apply function and wrap result")
    void traverseOnSuccessShouldApplyFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();

      Try<Integer> innerTry = TRY.narrow(narrowedResult.get());
      assertThatTry(innerTry).isSuccess().hasValue(42);
    }

    @Test
    @DisplayName("traverse on Failure should wrap Failure in applicative")
    void traverseOnFailureShouldWrapFailure() {
      RuntimeException originalException = new RuntimeException("Test failure");
      Kind<TryKind.Witness, String> failureKind = TRY.widen(Try.failure(originalException));
      Function<String, Kind<MaybeKind.Witness, Integer>> func =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, failureKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();

      Try<Integer> innerTry = TRY.narrow(narrowedResult.get());
      assertThatTry(innerTry).isFailure().hasException(originalException);
    }

    @Test
    @DisplayName("traverse with null value in Success should work correctly")
    void traverseWithNullValueShouldWork() {
      Kind<TryKind.Witness, String> successNullKind = TRY.widen(Try.success(null));
      Function<String, Kind<MaybeKind.Witness, Integer>> func = s -> MAYBE.widen(Maybe.just(-1));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successNullKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();

      Try<Integer> innerTry = TRY.narrow(narrowedResult.get());
      assertThatTry(innerTry).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("traverse should preserve structure")
    void traverseShouldPreserveStructure() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successKind);

      assertThat(result).isNotNull();
      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();
    }
  }

  @Nested
  @DisplayName("Functor Operations Tests (Inherited)")
  class FunctorOperationsTests {

    @Test
    @DisplayName("map on Success should apply function")
    void mapOnSuccessShouldApplyFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));

      Kind<TryKind.Witness, Integer> result = traverse.map(Integer::parseInt, successKind);

      Try<Integer> narrowedResult = TRY.narrow(result);
      assertThatTry(narrowedResult).isSuccess().hasValue(42);
    }

    @Test
    @DisplayName("map on Failure should preserve Failure")
    void mapOnFailureShouldPreserveFailure() {
      RuntimeException originalException = new RuntimeException("Test failure");
      Kind<TryKind.Witness, String> failureKind = TRY.widen(Try.failure(originalException));

      Kind<TryKind.Witness, Integer> result = traverse.map(Integer::parseInt, failureKind);

      Try<Integer> narrowedResult = TRY.narrow(result);
      assertThatTry(narrowedResult).isFailure().hasException(originalException);
    }
  }

  @Nested
  @DisplayName("Foldable Operations Tests (Inherited)")
  class FoldableOperationsTests {

    @Test
    @DisplayName("foldMap on Success should apply function")
    void foldMapOnSuccessShouldApplyFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));

      Integer result = traverse.foldMap(SUM_MONOID, Integer::parseInt, successKind);

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("foldMap on Failure should return identity")
    void foldMapOnFailureShouldReturnIdentity() {
      Kind<TryKind.Witness, String> failureKind =
          TRY.widen(Try.failure(new RuntimeException("Test failure")));

      Integer result = traverse.foldMap(SUM_MONOID, Integer::parseInt, failureKind);

      assertThat(result).isEqualTo(0); // SUM_MONOID identity
    }
  }

  @Nested
  @DisplayName("Individual Traverse Components")
  class IndividualTraverseComponents {

    @Test
    @DisplayName("Test traverse operations only")
    void testOperationsOnly() {
      Function<String, Kind<MaybeKind.Witness, Integer>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));
      TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
          .<String>instance(traverse)
          .<Integer>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, traverseFunc)
          .withFoldableOperations(SUM_MONOID, validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test traverse validations only")
    void testValidationsOnly() {
      Function<String, Kind<MaybeKind.Witness, Integer>> traverseFunc =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      TypeClassTest.<TryKind.Witness>traverse(TryTraverse.class)
          .<String>instance(traverse)
          .<Integer>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(maybeApplicative, traverseFunc)
          .withFoldableOperations(SUM_MONOID, validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      // Note: Try has special exception handling semantics where map() catches
      // exceptions and wraps them in Failure rather than propagating them.
      // Therefore, we cannot use the standard testExceptions() pattern.
      // Exception handling is thoroughly tested in the ExceptionHandlingTests nested class instead.

      // We verify here that traverse and foldMap do propagate exceptions correctly:
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      RuntimeException testException = new RuntimeException("Test exception");

      // traverse should propagate exceptions
      Function<String, Kind<MaybeKind.Witness, Integer>> throwingTraverseFunc =
          s -> {
            throw testException;
          };
      assertThatThrownBy(
              () -> traverse.traverse(maybeApplicative, throwingTraverseFunc, successKind))
          .isSameAs(testException);

      // foldMap should propagate exceptions
      Function<String, Integer> throwingFoldMapFunc =
          s -> {
            throw testException;
          };
      assertThatThrownBy(() -> traverse.foldMap(SUM_MONOID, throwingFoldMapFunc, successKind))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("traverse preserves Success/Failure distinction")
    void traversePreservesDistinction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("42"));
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(new RuntimeException("Error")));
      Function<String, Kind<MaybeKind.Witness, Integer>> func =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> successResult =
          traverse.traverse(maybeApplicative, func, success);
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> failureResult =
          traverse.traverse(maybeApplicative, func, failure);

      Try<Integer> successTry = TRY.narrow(MAYBE.narrow(successResult).get());
      Try<Integer> failureTry = TRY.narrow(MAYBE.narrow(failureResult).get());

      assertThatTry(successTry).isSuccess();
      assertThatTry(failureTry).isFailure();
    }

    @Test
    @DisplayName("traverse with function returning Nothing should preserve structure")
    void traverseWithNothingReturningFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func = s -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Multiple traverse operations should be independent")
    void multipleTraverseOperationsAreIndependent() {
      Kind<TryKind.Witness, String> kind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func1 =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));
      Function<String, Kind<MaybeKind.Witness, Integer>> func2 = s -> MAYBE.widen(Maybe.just(99));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result1 =
          traverse.traverse(maybeApplicative, func1, kind);
      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result2 =
          traverse.traverse(maybeApplicative, func2, kind);

      Try<Integer> try1 = TRY.narrow(MAYBE.narrow(result1).get());
      Try<Integer> try2 = TRY.narrow(MAYBE.narrow(result2).get());

      assertThatTry(try1).isSuccess().hasValue(42);
      assertThatTry(try2).isSuccess().hasValue(99);
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("traverse should propagate function exceptions on Success")
    void traverseShouldPropagateExceptionsFromFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      RuntimeException testException = new RuntimeException("Test exception in traverse");

      Function<String, Kind<MaybeKind.Witness, Integer>> throwingFunction =
          s -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.traverse(maybeApplicative, throwingFunction, successKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("traverse should not call function on Failure")
    void traverseShouldNotCallFunctionOnFailure() {
      Kind<TryKind.Witness, String> failureKind =
          TRY.widen(Try.failure(new RuntimeException("Original failure")));
      RuntimeException testException = new RuntimeException("Function should not be called");

      Function<String, Kind<MaybeKind.Witness, Integer>> throwingFunction =
          s -> {
            throw testException;
          };

      // Should not throw because function should not be called
      assertThatCode(() -> traverse.traverse(maybeApplicative, throwingFunction, failureKind))
          .doesNotThrowAnyException();

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, throwingFunction, failureKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();

      Try<Integer> innerTry = TRY.narrow(narrowedResult.get());
      assertThatTry(innerTry).isFailure();
    }

    @Test
    @DisplayName("map should capture function exceptions in Failure on Success")
    void mapShouldCaptureExceptionsFromFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      RuntimeException testException = new RuntimeException("Test exception in map");

      Function<String, Integer> throwingFunction =
          s -> {
            throw testException;
          };

      Kind<TryKind.Witness, Integer> result = traverse.map(throwingFunction, successKind);
      Try<Integer> narrowedResult = TRY.narrow(result);

      assertThatTry(narrowedResult).isFailure().hasException(testException);
    }

    @Test
    @DisplayName("foldMap should propagate function exceptions on Success")
    void foldMapShouldPropagateExceptionsFromFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      RuntimeException testException = new RuntimeException("Test exception in foldMap");

      Function<String, Integer> throwingFunction =
          s -> {
            throw testException;
          };

      // foldMap will propagate the exception since it's not caught by Try
      assertThatThrownBy(() -> traverse.foldMap(SUM_MONOID, throwingFunction, successKind))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Applicative Effect Tests")
  class ApplicativeEffectTests {

    @Test
    @DisplayName("traverse respects applicative structure")
    void traverseRespectsApplicativeStructure() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func =
          s -> MAYBE.widen(Maybe.just(Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successKind);

      // Result should be wrapped in Maybe applicative context
      assertThat(result).isNotNull();
      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult).isNotNull();
      assertThat(narrowedResult.isJust()).isTrue();
    }

    @Test
    @DisplayName("traverse with failing applicative function should preserve failure")
    void traverseWithFailingApplicativeFunctionShouldPreserveFailure() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("42"));
      Function<String, Kind<MaybeKind.Witness, Integer>> func = s -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, func, successKind);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      // When the applicative function returns Nothing, the whole result is Nothing
      assertThat(narrowedResult.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {

    @Test
    @DisplayName("traverse should handle null value in Success")
    void traverseShouldHandleNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      Function<String, Kind<MaybeKind.Witness, Integer>> safeFunc =
          s -> MAYBE.widen(Maybe.just(s == null ? -1 : Integer.parseInt(s)));

      Kind<MaybeKind.Witness, Kind<TryKind.Witness, Integer>> result =
          traverse.traverse(maybeApplicative, safeFunc, successNull);

      Maybe<Kind<TryKind.Witness, Integer>> narrowedResult = MAYBE.narrow(result);
      assertThat(narrowedResult.isJust()).isTrue();

      Try<Integer> innerTry = TRY.narrow(narrowedResult.get());
      assertThatTry(innerTry).isSuccess().hasValue(-1);
    }

    @Test
    @DisplayName("foldMap should handle null value in Success")
    void foldMapShouldHandleNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      Function<String, Integer> safeFunc = s -> s == null ? 0 : Integer.parseInt(s);

      Integer result = traverse.foldMap(SUM_MONOID, safeFunc, successNull);

      assertThat(result).isEqualTo(0);
    }
  }
}
