// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TryTraverse — Foldable")
class TryFoldableTest extends TryTestBase {

  private static final Monoid<String> STRING_MONOID = Monoids.string();
  private static final Monoid<Integer> SUM_MONOID = Monoids.integerAddition();

  private Foldable<TryKind.Witness> foldable;

  @BeforeEach
  void setUpFoldable() {
    foldable = TryTraverse.INSTANCE;
  }

  @Test
  @DisplayName("Foldable contract — operations, validations & exceptions (Foldable has no laws)")
  void foldableContract() {
    // Unlike map/flatMap/traverse, Try.foldMap propagates a thrown mapper exception rather than
    // capturing it, so the EXCEPTIONS category in the generic contract holds — verify() runs it.
    TypeClassContract.<TryKind.Witness>foldable(TryTraverse.class)
        .<String>instance(foldable)
        .withKind(validKind)
        .withOperations(SUM_MONOID, validMapper)
        .verify();
  }

  @Nested
  @DisplayName("Operation Tests")
  class OperationTests {

    @Test
    @DisplayName("foldMap() on Success applies the function")
    void foldMapOnSuccessAppliesFunction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      Integer result = foldable.foldMap(SUM_MONOID, String::length, success);
      assertThat(result).isEqualTo(DEFAULT_SUCCESS_VALUE.length());
    }

    @Test
    @DisplayName("foldMap() on Failure returns the monoid identity")
    void foldMapOnFailureReturnsIdentity() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Integer result = foldable.foldMap(SUM_MONOID, String::length, failure);
      assertThat(result).isEqualTo(SUM_MONOID.empty());
    }

    @Test
    @DisplayName("foldMap() with different monoids")
    void foldMapWithDifferentMonoids() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("hello"));

      Integer sum = foldable.foldMap(SUM_MONOID, String::length, success);
      assertThat(sum).isEqualTo(5);

      String concat = foldable.foldMap(STRING_MONOID, String::toUpperCase, success);
      assertThat(concat).isEqualTo("HELLO");

      Monoid<Boolean> booleanAnd = Monoids.booleanAnd();
      Boolean predicate = foldable.foldMap(booleanAnd, s -> s.length() > 3, success);
      assertThat(predicate).isTrue();
    }
  }

  @Nested
  @DisplayName("Monoid Behaviour Tests")
  class MonoidBehaviourTests {

    @Test
    @DisplayName("foldMap() respects the monoid identity for both Success and Failure")
    void foldMapRespectsMonoidIdentity() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);

      // Mapping every element to the identity yields the identity, regardless of Success/Failure.
      assertThat(foldable.foldMap(STRING_MONOID, _ -> STRING_MONOID.empty(), success))
          .isEqualTo(STRING_MONOID.empty());
      assertThat(foldable.foldMap(STRING_MONOID, _ -> STRING_MONOID.empty(), failure))
          .isEqualTo(STRING_MONOID.empty());
    }

    @Test
    @DisplayName("foldMap() with a single element is monoid-independent")
    void foldMapSingleElementIsMonoidIndependent() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success("12345"));
      Function<String, Integer> mapper = s -> s.length() * 3;

      // With a single element neither monoid combines anything, so both equal the mapped value.
      Integer add = foldable.foldMap(SUM_MONOID, mapper, success);
      Integer mult = foldable.foldMap(Monoids.integerMultiplication(), mapper, success);

      assertThat(add).isEqualTo(15);
      assertThat(add).isEqualTo(mult);
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("foldMap() propagates a function exception on Success")
    void foldMapPropagatesExceptionFromFunction() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      RuntimeException boom = new RuntimeException("foldMap boom");
      Function<String, Integer> throwing =
          _ -> {
            throw boom;
          };

      assertThatThrownBy(() -> foldable.foldMap(SUM_MONOID, throwing, success)).isSameAs(boom);
    }

    @Test
    @DisplayName("foldMap() does not call the function on Failure")
    void foldMapDoesNotCallFunctionOnFailure() {
      Kind<TryKind.Witness, String> failure = failureKind(DEFAULT_TEST_EXCEPTION);
      Function<String, Integer> throwing =
          _ -> {
            throw new AssertionError("function must not run on a Failure");
          };

      assertThatCode(() -> foldable.foldMap(SUM_MONOID, throwing, failure))
          .doesNotThrowAnyException();
      assertThat(foldable.foldMap(SUM_MONOID, throwing, failure)).isEqualTo(SUM_MONOID.empty());
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  @SuppressWarnings({"DataFlowIssue", "ConstantValue"}) // Success may hold a null value
  class NullHandlingTests {

    @Test
    @DisplayName("foldMap() handles a null value inside Success")
    void foldMapHandlesNullValueInSuccess() {
      Kind<TryKind.Witness, String> successNull = TRY.widen(Try.success(null));
      String result = foldable.foldMap(STRING_MONOID, s -> s == null ? "NULL" : s, successNull);
      assertThat(result).isEqualTo("NULL");
    }

    @Test
    @DisplayName("foldMap() returns null when the function returns null on Success")
    void foldMapReturnsNullWhenFunctionReturnsNull() {
      Kind<TryKind.Witness, String> success = TRY.widen(Try.success(DEFAULT_SUCCESS_VALUE));
      // With a single element there is no combine, so foldMap simply returns the mapped null.
      String result = foldable.foldMap(STRING_MONOID, _ -> null, success);
      assertThat(result).isNull();
    }
  }
}
