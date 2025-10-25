// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryFoldable Complete Test Suite")
class TryFoldableTest extends TryTestBase {

  private static final Monoid<String> STRING_MONOID = Monoids.string();
  private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

  private Foldable<TryKind.Witness> foldable;

  @BeforeEach
  void setUpFoldable() {
    foldable = TryTraverse.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Foldable Test Suite")
  class CompleteFoldableTestSuite {

    @Test
    @DisplayName("Run complete Foldable test pattern")
    void runCompleteFoldableTestPattern() {
      TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
          .<String>instance(foldable)
          .withKind(validKind)
          .withOperations(SUM_MONOID, validMapper)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Foldable Operation Tests")
  class FoldableOperationTests {

    @Test
    @DisplayName("foldMap on Success should apply function and return result")
    void foldMapOnSuccessShouldApplyFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));

      Integer result = foldable.foldMap(SUM_MONOID, String::length, successKind);

      assertThat(result).isEqualTo(DEFAULT_SUCCESS_VALUE.length());
    }

    @Test
    @DisplayName("foldMap on Failure should return monoid identity")
    void foldMapOnFailureShouldReturnIdentity() {
      Kind<TryKind.Witness, String> failureKind =
          TRY.widen(Try.failure(new RuntimeException("Test failure")));

      Integer result = foldable.foldMap(SUM_MONOID, String::length, failureKind);

      assertThat(result).isEqualTo(0); // SUM_MONOID identity is 0
    }

    @Test
    @DisplayName("foldMap with null value in Success should work correctly")
    void foldMapWithNullValueShouldWork() {
      Kind<TryKind.Witness, String> successNullKind = TRY.widen(Try.success(null));

      String result = foldable.foldMap(STRING_MONOID, s -> "null", successNullKind);

      assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("foldMap with custom monoid should use monoid operations")
    void foldMapWithCustomMonoidShouldUseMonoidOperations() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("Hello"));

      Integer result = foldable.foldMap(SUM_MONOID, s -> s.length() * 2, successKind);

      assertThat(result).isEqualTo(10); // "Hello".length() * 2 = 10
    }
  }

  @Nested
  @DisplayName("Individual Foldable Components")
  class IndividualFoldableComponents {

    @Test
    @DisplayName("Test foldMap operations only")
    void testOperationsOnly() {
      TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
          .<String>instance(foldable)
          .withKind(validKind)
          .withOperations(SUM_MONOID, validMapper)
          .testOperations();
    }

    @Test
    @DisplayName("Test foldMap validations only")
    void testValidationsOnly() {
      TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
          .<String>instance(foldable)
          .withKind(validKind)
          .withOperations(SUM_MONOID, validMapper)
          .testValidations();
    }

    @Test
    @DisplayName("Test exception propagation only")
    void testExceptionPropagationOnly() {
      TypeClassTest.<TryKind.Witness>foldable(TryTraverse.class)
          .<String>instance(foldable)
          .withKind(validKind)
          .withOperations(SUM_MONOID, validMapper)
          .testExceptions();
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("foldMap preserves Success/Failure distinction")
    void foldMapPreservesDistinction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      Kind<TryKind.Witness, String> failure = TRY.widen(Try.failure(new RuntimeException("Error")));

      Integer successResult = foldable.foldMap(SUM_MONOID, String::length, success);
      Integer failureResult = foldable.foldMap(SUM_MONOID, String::length, failure);

      assertThat(successResult).isNotEqualTo(failureResult);
      assertThat(successResult).isEqualTo(DEFAULT_SUCCESS_VALUE.length());
      assertThat(failureResult).isEqualTo(0); // monoid identity
    }

    @Test
    @DisplayName("foldMap with function returning empty should work")
    void foldMapWithEmptyReturningFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));

      String result = foldable.foldMap(STRING_MONOID, s -> "", successKind);

      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("Multiple foldMap operations should be independent")
    void multipleFoldMapOperationsAreIndependent() {
      Kind<TryKind.Witness, String> kind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));

      Integer result1 = foldable.foldMap(SUM_MONOID, String::length, kind);
      Integer result2 = foldable.foldMap(SUM_MONOID, s -> s.length() * 2, kind);

      assertThat(result1).isEqualTo(DEFAULT_SUCCESS_VALUE.length());
      assertThat(result2).isEqualTo(DEFAULT_SUCCESS_VALUE.length() * 2);
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("foldMap should propagate function exceptions on Success")
    void foldMapShouldPropagateExceptionsFromFunction() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      RuntimeException testException = new RuntimeException("Test exception in foldMap");

      Function<String, Integer> throwingFunction =
          s -> {
            throw testException;
          };

      assertThatThrownBy(() -> foldable.foldMap(SUM_MONOID, throwingFunction, successKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("foldMap should not call function on Failure")
    void foldMapShouldNotCallFunctionOnFailure() {
      Kind<TryKind.Witness, String> failureKind =
          TRY.widen(Try.failure(new RuntimeException("Original failure")));
      RuntimeException testException = new RuntimeException("Function should not be called");

      Function<String, Integer> throwingFunction =
          s -> {
            throw testException;
          };

      // Should not throw because function should not be called
      assertThatCode(() -> foldable.foldMap(SUM_MONOID, throwingFunction, failureKind))
          .doesNotThrowAnyException();

      Integer result = foldable.foldMap(SUM_MONOID, throwingFunction, failureKind);
      assertThat(result).isEqualTo(0); // monoid identity
    }
  }

  @Nested
  @DisplayName("Monoid Behaviour Tests")
  class MonoidBehaviourTests {

    @Test
    @DisplayName("foldMap with identity should behave correctly")
    void foldMapWithIdentity() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      Kind<TryKind.Witness, String> failureKind =
          TRY.widen(Try.failure(new RuntimeException("Error")));

      String successResult =
          foldable.foldMap(STRING_MONOID, s -> STRING_MONOID.empty(), successKind);
      String failureResult =
          foldable.foldMap(STRING_MONOID, s -> STRING_MONOID.empty(), failureKind);

      assertThat(successResult).isEqualTo(STRING_MONOID.empty());
      assertThat(failureResult).isEqualTo(STRING_MONOID.empty());
    }

    @Test
    @DisplayName("foldMap respects monoid combine operation")
    void foldMapRespectsMonoidCombine() {
      // For Success, result should be the mapped value
      // For Failure, result should be identity
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("12345"));

      // Using SUM_MONOID: empty() = 0, combine(a,b) = a+b
      Integer result = foldable.foldMap(SUM_MONOID, s -> s.length() * 3, successKind);

      assertThat(result).isEqualTo(15); // 5 * 3 = 15
    }

    @Test
    @DisplayName("foldMap with string concatenation monoid")
    void foldMapWithStringConcatenation() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("test"));

      String result = foldable.foldMap(STRING_MONOID, s -> s.toUpperCase(), successKind);

      assertThat(result).isEqualTo("TEST");
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {

    @Test
    @DisplayName("foldMap should handle null value in Success")
    void foldMapShouldHandleNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));

      String result = foldable.foldMap(STRING_MONOID, s -> s == null ? "NULL" : s, successNull);

      assertThat(result).isEqualTo("NULL");
    }

    @Test
    @DisplayName("foldMap should return null when function returns null on Success")
    void foldMapShouldHandleFunctionReturningNull() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));

      // When the function returns null for a single element, foldMap just returns that null
      // (no combine operation happens with a single element)
      String result = foldable.foldMap(STRING_MONOID, s -> null, successKind);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Type Safety Tests")
  class TypeSafetyTests {

    @Test
    @DisplayName("foldMap should work with different monoid types")
    void foldMapShouldWorkWithDifferentMonoidTypes() {
      Kind<TryKind.Witness, String> successKind = TRY.widen(Try.success("hello"));

      // Test with Integer monoid
      Integer intResult = foldable.foldMap(SUM_MONOID, String::length, successKind);
      assertThat(intResult).isEqualTo(5);

      // Test with String monoid
      String strResult = foldable.foldMap(STRING_MONOID, s -> s.toUpperCase(), successKind);
      assertThat(strResult).isEqualTo("HELLO");

      // Test with Boolean monoid
      Monoid<Boolean> booleanAnd = Monoids.booleanAnd();
      Boolean boolResult = foldable.foldMap(booleanAnd, s -> s.length() > 3, successKind);
      assertThat(boolResult).isTrue();
    }
  }
}
