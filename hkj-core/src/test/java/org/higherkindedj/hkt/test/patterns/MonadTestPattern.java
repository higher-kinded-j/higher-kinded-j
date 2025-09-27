// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestData;

/**
 * Complete test pattern for Monad implementations.
 *
 * <p>Provides a comprehensive test suite that can be run with a single method call, including:
 *
 * <ul>
 *   <li>Basic operation validation
 *   <li>Null parameter validation
 *   <li>Exception propagation testing
 *   <li>Monad law verification
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Test
 * void completeMonadTest() {
 *     MonadTestPattern.runComplete(
 *         monad,
 *         validKind,
 *         testValue,
 *         validMapper,
 *         validFlatMapper,
 *         validFunctionKind,
 *         testFunction,
 *         chainFunction,
 *         equalityChecker
 *     );
 * }
 * }</pre>
 */
public final class MonadTestPattern {

  private MonadTestPattern() {
    throw new AssertionError("MonadTestPattern is a utility class");
  }

  /** Runs complete monad test suite. */
  public static <F> void runComplete(
      Monad<F> monad,
      Kind<F, Integer> validKind,
      Integer testValue,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind,
      Function<Integer, Kind<F, String>> testFunction,
      Function<String, Kind<F, String>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testBasicOperations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);
    testNullValidations(monad, validKind, validMapper, validFlatMapper, validFunctionKind);
    testExceptionPropagation(monad, validKind);
    testMonadLaws(monad, validKind, testValue, testFunction, chainFunction, equalityChecker);
  }

  /** Tests basic monad operations work correctly. */
  public static <F> void testBasicOperations(
      Monad<F> monad,
      Kind<F, Integer> validInput,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind) {

    assertThat(monad.map(validMapper, validInput))
        .as("map should return non-null result")
        .isNotNull();

    assertThat(monad.flatMap(validFlatMapper, validInput))
        .as("flatMap should return non-null result")
        .isNotNull();

    assertThat(monad.ap(validFunctionKind, validInput))
        .as("ap should return non-null result")
        .isNotNull();
  }

  /** Tests all null parameter validations. */
  public static <F> void testNullValidations(
      Monad<F> monad,
      Kind<F, Integer> validKind,
      Function<Integer, String> validMapper,
      Function<Integer, Kind<F, String>> validFlatMapper,
      Kind<F, Function<Integer, String>> validFunctionKind) {

    ValidationTestBuilder.create()
        // Map
        .assertMapperNull(() -> monad.map(null, validKind), "map")
        .assertKindNull(() -> monad.map(validMapper, null), "map")
        // FlatMap
        .assertFlatMapperNull(() -> monad.flatMap(null, validKind), "flatMap")
        .assertKindNull(() -> monad.flatMap(validFlatMapper, null), "flatMap")
        // Ap
        .assertKindNull(() -> monad.ap(null, validKind), "ap_function")
        .assertKindNull(() -> monad.ap(validFunctionKind, null), "ap_argument")
        .execute();
  }

  /** Tests exception propagation through monad operations. */
  public static <F> void testExceptionPropagation(Monad<F> monad, Kind<F, Integer> validInput) {

    RuntimeException testException = TestData.createTestException("monad test");

    // Test map exception propagation
    Function<Integer, String> throwingMapper =
        i -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.map(throwingMapper, validInput))
        .as("map should propagate function exceptions")
        .isSameAs(testException);

    // Test flatMap exception propagation
    Function<Integer, Kind<F, String>> throwingFlatMapper =
        i -> {
          throw testException;
        };
    assertThatThrownBy(() -> monad.flatMap(throwingFlatMapper, validInput))
        .as("flatMap should propagate function exceptions")
        .isSameAs(testException);
  }

  /** Tests all three monad laws with error validation. */
  public static <F> void testMonadLaws(
      Monad<F> monad,
      Kind<F, Integer> testKind,
      Integer testValue,
      Function<Integer, Kind<F, String>> testFunction,
      Function<String, Kind<F, String>> chainFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    testLeftIdentity(monad, testValue, testFunction, equalityChecker);
    testRightIdentity(monad, testKind, equalityChecker);
    testAssociativity(monad, testKind, testFunction, chainFunction, equalityChecker);
  }

  /** Tests left identity law: flatMap(of(a), f) == f(a) */
  public static <F> void testLeftIdentity(
      Monad<F> monad,
      Integer testValue,
      Function<Integer, Kind<F, String>> testFunction,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Kind<F, Integer> ofValue = monad.of(testValue);
    Kind<F, String> leftSide = monad.flatMap(testFunction, ofValue);
    Kind<F, String> rightSide = testFunction.apply(testValue);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Left identity law: flatMap(of(a), f) == f(a)")
        .isTrue();
  }

  /** Tests right identity law: flatMap(m, of) == m */
  public static <F> void testRightIdentity(
      Monad<F> monad,
      Kind<F, Integer> testKind,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Function<Integer, Kind<F, Integer>> ofFunc = monad::of;
    Kind<F, Integer> leftSide = monad.flatMap(ofFunc, testKind);

    assertThat(equalityChecker.test(leftSide, testKind))
        .as("Right identity law: flatMap(m, of) == m")
        .isTrue();
  }

  /** Tests associativity law: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g)) */
  public static <F> void testAssociativity(
      Monad<F> monad,
      Kind<F, Integer> testKind,
      Function<Integer, Kind<F, String>> f,
      Function<String, Kind<F, String>> g,
      BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {

    Kind<F, String> innerFlatMap = monad.flatMap(f, testKind);
    Kind<F, String> leftSide = monad.flatMap(g, innerFlatMap);

    Function<Integer, Kind<F, String>> rightSideFunc = a -> monad.flatMap(g, f.apply(a));
    Kind<F, String> rightSide = monad.flatMap(rightSideFunc, testKind);

    assertThat(equalityChecker.test(leftSide, rightSide))
        .as("Associativity law: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
        .isTrue();
  }
}
