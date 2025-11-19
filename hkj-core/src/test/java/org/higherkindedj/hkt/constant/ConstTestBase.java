// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;

/**
 * Base class for Const type class tests.
 *
 * <p>Provides common fixture creation, standardized test constants, and helper methods for all
 * Const type class tests.
 *
 * <h2>Test Constants</h2>
 *
 * <p>Standard test values for Const testing:
 *
 * <ul>
 *   <li>{@link #DEFAULT_VALUE} - The primary accumulated value (10)
 *   <li>{@link #ALTERNATIVE_VALUE} - A secondary accumulated value (20)
 * </ul>
 *
 * <h2>Monoid Instances</h2>
 *
 * <p>Common monoid instances for testing:
 *
 * <ul>
 *   <li>{@link #sumMonoid} - Addition monoid for integers (identity: 0)
 *   <li>{@link #productMonoid} - Multiplication monoid for integers (identity: 1)
 *   <li>{@link #stringMonoid} - Concatenation monoid for strings (identity: "")
 * </ul>
 */
abstract class ConstTestBase
    extends TypeClassTestBase<ConstKind.Witness<Integer>, String, Integer> {

  // ============================================================================
  // Test Constants
  // ============================================================================

  /** Default accumulated value for Const instances in tests. */
  protected static final Integer DEFAULT_VALUE = 10;

  /** Alternative accumulated value for testing with multiple values. */
  protected static final Integer ALTERNATIVE_VALUE = 20;

  // ============================================================================
  // Monoid Instances
  // ============================================================================

  /** Sum monoid for integers: (0, +) */
  protected final Monoid<Integer> sumMonoid =
      new Monoid<Integer>() {
        @Override
        public Integer empty() {
          return 0;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a + b;
        }
      };

  /** Product monoid for integers: (1, *) */
  protected final Monoid<Integer> productMonoid =
      new Monoid<Integer>() {
        @Override
        public Integer empty() {
          return 1;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a * b;
        }
      };

  /** String concatenation monoid: ("", ++) */
  protected final Monoid<String> stringMonoid =
      new Monoid<String>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  // ============================================================================
  // Fixture Creation Methods
  // ============================================================================

  /**
   * Creates the primary valid Kind for testing.
   *
   * @return A Const Kind containing the default value with String phantom type
   */
  @Override
  protected Kind<ConstKind.Witness<Integer>, String> createValidKind() {
    return CONST.widen(new Const<>(DEFAULT_VALUE));
  }

  /**
   * Creates a secondary valid Kind for testing.
   *
   * @return A Const Kind containing the alternative value with String phantom type
   */
  @Override
  protected Kind<ConstKind.Witness<Integer>, String> createValidKind2() {
    return CONST.widen(new Const<>(ALTERNATIVE_VALUE));
  }

  /**
   * Creates a valid mapper function from String to Integer.
   *
   * <p>This mapper is never actually applied in Const, but is used for type signatures.
   *
   * @return A mapper that converts String to Integer (String::length)
   */
  @Override
  protected Function<String, Integer> createValidMapper() {
    return String::length;
  }

  /**
   * Creates a second mapper function from Integer to String.
   *
   * @return A mapper that converts Integer to String
   */
  @Override
  protected Function<Integer, String> createSecondMapper() {
    return i -> "Value:" + i;
  }

  /**
   * Creates a flat-mapper function from String to Kind.
   *
   * <p>Note: This is never actually applied in Const Applicative.
   *
   * @return A flat-mapper that wraps the string length in Const
   */
  @Override
  protected Function<String, Kind<ConstKind.Witness<Integer>, Integer>> createValidFlatMapper() {
    return s -> CONST.widen(new Const<>(s.length()));
  }

  /**
   * Creates a function Kind for applicative testing.
   *
   * <p>The function itself is phantom in Const, but the accumulated value is used.
   *
   * @return A Const Kind containing an accumulated value with Function phantom type
   */
  @Override
  protected Kind<ConstKind.Witness<Integer>, Function<String, Integer>> createValidFunctionKind() {
    return CONST.widen(new Const<>(5));
  }

  /**
   * Creates a combining function for map2 testing.
   *
   * <p>This function is never applied in Const, but is required for type signatures.
   *
   * @return A BiFunction that combines two Strings into Integer
   */
  @Override
  protected BiFunction<String, String, Integer> createValidCombiningFunction() {
    return (s1, s2) -> s1.length() + s2.length();
  }

  /**
   * Creates a test value for law testing.
   *
   * @return A test string value
   */
  @Override
  protected String createTestValue() {
    return "test";
  }

  /**
   * Creates an equality checker for Kind comparison.
   *
   * <p>For Const, two Kinds are equal if their accumulated values are equal (ignoring phantom
   * types).
   *
   * @return A BiPredicate that compares Const accumulated values
   */
  @Override
  protected java.util.function.BiPredicate<
          Kind<ConstKind.Witness<Integer>, ?>, Kind<ConstKind.Witness<Integer>, ?>>
      createEqualityChecker() {
    return (k1, k2) -> {
      Const<Integer, ?> const1 = CONST.narrow(k1);
      Const<Integer, ?> const2 = CONST.narrow(k2);
      return java.util.Objects.equals(const1.value(), const2.value());
    };
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Narrows a Kind to its concrete Const type.
   *
   * @param kind The Kind to narrow
   * @param <A> The phantom type parameter
   * @return The narrowed Const
   */
  protected <A> Const<Integer, A> narrowToConst(Kind<ConstKind.Witness<Integer>, A> kind) {
    return CONST.narrow(kind);
  }

  /**
   * Creates a Const Kind with a specific accumulated value.
   *
   * @param value The accumulated value
   * @param <A> The phantom type parameter
   * @return A Const Kind
   */
  protected <A> Kind<ConstKind.Witness<Integer>, A> constKind(Integer value) {
    return CONST.widen(new Const<>(value));
  }

  /**
   * Creates a Const Kind with the default value.
   *
   * @param <A> The phantom type parameter
   * @return A Const Kind with the default accumulated value
   */
  protected <A> Kind<ConstKind.Witness<Integer>, A> defaultConstKind() {
    return constKind(DEFAULT_VALUE);
  }
}
