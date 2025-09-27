// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * Applicative-specific assertion shortcuts.
 *
 * <p>Provides convenient methods for Applicative Functor operation assertions by composing from
 * {@link FunctionAssertions} and {@link KindAssertions}. This class focuses on applicative-specific
 * operations like {@code ap} and {@code mapN} methods.
 *
 * <p>All assertions align with the production validation framework and use the same context-based
 * error messages as {@link org.higherkindedj.hkt.util.validation.FunctionValidator} and {@link
 * org.higherkindedj.hkt.util.validation.KindValidator}.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Ap Operation:</h3>
 *
 * <pre>{@code
 * // Function Kind parameter
 * assertApFunctionKindNull(() -> applicative.ap(null, validKind));
 *
 * // Argument Kind parameter
 * assertApArgumentKindNull(() -> applicative.ap(validFunctionKind, null));
 * }</pre>
 *
 * <h3>Map2 Operation:</h3>
 *
 * <pre>{@code
 * // First Kind parameter
 * assertMap2FirstKindNull(() -> applicative.map2(null, validKind2, validFunction));
 *
 * // Second Kind parameter
 * assertMap2SecondKindNull(() -> applicative.map2(validKind1, null, validFunction));
 *
 * // Combining function
 * assertMap2FunctionNull(() -> applicative.map2(validKind1, validKind2, null));
 * }</pre>
 *
 * <h3>Map3 Operation:</h3>
 *
 * <pre>{@code
 * assertMap3FirstKindNull(() -> applicative.map3(null, k2, k3, f));
 * assertMap3SecondKindNull(() -> applicative.map3(k1, null, k3, f));
 * assertMap3ThirdKindNull(() -> applicative.map3(k1, k2, null, f));
 * assertMap3FunctionNull(() -> applicative.map3(k1, k2, k3, null));
 * }</pre>
 *
 * <h3>Complete Validation:</h3>
 *
 * <pre>{@code
 * // Test all map2 validations
 * assertAllMap2Operations(applicative, kind1, kind2, combiningFunction);
 *
 * // Test all map3 validations
 * assertAllMap3Operations(applicative, kind1, kind2, kind3, combiningFunction);
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>Composes {@link FunctionAssertions} and {@link KindAssertions}
 *   <li>Operation-specific naming for better readability
 *   <li>Consistent with Applicative interface method names
 *   <li>Supports all mapN operations (map2, map3, map4, map5)
 * </ul>
 *
 * @see org.higherkindedj.hkt.Applicative
 * @see FunctionAssertions
 * @see KindAssertions
 */
public final class ApplicativeAssertions {

  private ApplicativeAssertions() {
    throw new AssertionError(
        "ApplicativeAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Ap Operation Assertions
  // =============================================================================

  /**
   * Asserts that ap operation throws when function Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.ap(null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#ap(org.higherkindedj.hkt.Kind,
   *     org.higherkindedj.hkt.Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApFunctionKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "ap_function");
  }

  /**
   * Asserts that ap operation throws when argument Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.ap(validFunctionKind, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#ap(org.higherkindedj.hkt.Kind,
   *     org.higherkindedj.hkt.Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApArgumentKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "ap_argument");
  }

  // =============================================================================
  // Map2 Operation Assertions
  // =============================================================================

  /**
   * Asserts that map2 operation throws when first Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.map2(null, validKind, validFunction)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(org.higherkindedj.hkt.Kind,
   *     org.higherkindedj.hkt.Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map2_first");
  }

  /**
   * Asserts that map2 operation throws when second Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.map2(validKind, null, validFunction)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(org.higherkindedj.hkt.Kind,
   *     org.higherkindedj.hkt.Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map2_second");
  }

  /**
   * Asserts that map2 operation throws when combining function is null.
   *
   * <p>Tests the validation: {@code applicative.map2(validKind1, validKind2, (BiFunction) null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(org.higherkindedj.hkt.Kind,
   *     org.higherkindedj.hkt.Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "map2");
  }

  // =============================================================================
  // Map3 Operation Assertions
  // =============================================================================

  /**
   * Asserts that map3 operation throws when first Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.map3(null, k2, k3, f)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map3
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap3FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map3_first");
  }

  /**
   * Asserts that map3 operation throws when second Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.map3(k1, null, k3, f)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map3
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap3SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map3_second");
  }

  /**
   * Asserts that map3 operation throws when third Kind parameter is null.
   *
   * <p>Tests the validation: {@code applicative.map3(k1, k2, null, f)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map3
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap3ThirdKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map3_third");
  }

  /**
   * Asserts that map3 operation throws when combining function is null.
   *
   * <p>Tests the validation: {@code applicative.map3(k1, k2, k3, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map3
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap3FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "map3");
  }

  // =============================================================================
  // Map4 Operation Assertions
  // =============================================================================

  /**
   * Asserts that map4 operation throws when first Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map4
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap4FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map4_first");
  }

  /**
   * Asserts that map4 operation throws when second Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map4
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap4SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map4_second");
  }

  /**
   * Asserts that map4 operation throws when third Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map4
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap4ThirdKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map4_third");
  }

  /**
   * Asserts that map4 operation throws when fourth Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map4
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap4FourthKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map4_fourth");
  }

  /**
   * Asserts that map4 operation throws when combining function is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map4
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap4FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "map4");
  }

  // =============================================================================
  // Map5 Operation Assertions
  // =============================================================================

  /**
   * Asserts that map5 operation throws when first Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map5_first");
  }

  /**
   * Asserts that map5 operation throws when second Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map5_second");
  }

  /**
   * Asserts that map5 operation throws when third Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5ThirdKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map5_third");
  }

  /**
   * Asserts that map5 operation throws when fourth Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5FourthKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map5_fourth");
  }

  /**
   * Asserts that map5 operation throws when fifth Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5FifthKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map5_fifth");
  }

  /**
   * Asserts that map5 operation throws when combining function is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @see org.higherkindedj.hkt.Applicative#map5
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap5FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "map5");
  }

  // =============================================================================
  // Convenience Methods for Complete Testing
  // =============================================================================

  /**
   * Asserts all map2 operation validations.
   *
   * <p>Tests all three parameters of map2 for null handling.
   *
   * @param applicative The Applicative instance to test
   * @param validKind1 First valid Kind
   * @param validKind2 Second valid Kind
   * @param validFunction Valid combining function
   * @param <F> The Applicative witness type
   * @param <A> First input type
   * @param <B> Second input type
   * @param <C> Output type
   */
  public static <F, A, B, C> void assertAllMap2Operations(
      org.higherkindedj.hkt.Applicative<F> applicative,
      org.higherkindedj.hkt.Kind<F, A> validKind1,
      org.higherkindedj.hkt.Kind<F, B> validKind2,
      java.util.function.BiFunction<A, B, C> validFunction) {

    assertMap2FirstKindNull(() -> applicative.map2(null, validKind2, validFunction));
    assertMap2SecondKindNull(() -> applicative.map2(validKind1, null, validFunction));
    assertMap2FunctionNull(
        () -> applicative.map2(validKind1, validKind2, (BiFunction<A, B, C>) null));
  }

  /**
   * Asserts all map3 operation validations.
   *
   * <p>Tests all four parameters of map3 for null handling.
   *
   * @param applicative The Applicative instance to test
   * @param validKind1 First valid Kind
   * @param validKind2 Second valid Kind
   * @param validKind3 Third valid Kind
   * @param validFunction Valid combining function
   * @param <F> The Applicative witness type
   * @param <A> First input type
   * @param <B> Second input type
   * @param <C> Third input type
   * @param <R> Output type
   */
  public static <F, A, B, C, R> void assertAllMap3Operations(
      org.higherkindedj.hkt.Applicative<F> applicative,
      org.higherkindedj.hkt.Kind<F, A> validKind1,
      org.higherkindedj.hkt.Kind<F, B> validKind2,
      org.higherkindedj.hkt.Kind<F, C> validKind3,
      org.higherkindedj.hkt.function.Function3<A, B, C, R> validFunction) {

    assertMap3FirstKindNull(() -> applicative.map3(null, validKind2, validKind3, validFunction));
    assertMap3SecondKindNull(() -> applicative.map3(validKind1, null, validKind3, validFunction));
    assertMap3ThirdKindNull(() -> applicative.map3(validKind1, validKind2, null, validFunction));
    assertMap3FunctionNull(() -> applicative.map3(validKind1, validKind2, validKind3, null));
  }

  /**
   * Asserts all basic Applicative operations for null parameters.
   *
   * <p>Tests ap and map2 operations comprehensively.
   *
   * @param applicative The Applicative instance to test
   * @param validKind Valid Kind for testing
   * @param validKind2 Second valid Kind for map2
   * @param validFunctionKind Valid function Kind for ap
   * @param validCombiningFunction Valid combining function for map2
   * @param <F> The Applicative witness type
   * @param <A> Input type
   * @param <B> Output type
   */
  public static <F, A, B> void assertAllApplicativeOperations(
      Applicative<F> applicative,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    // Ap validations
    assertApFunctionKindNull(() -> applicative.ap(null, validKind));
    assertApArgumentKindNull(() -> applicative.ap(validFunctionKind, null));

    // Map2 validations
    assertAllMap2Operations(applicative, validKind, validKind2, validCombiningFunction);
  }
}
