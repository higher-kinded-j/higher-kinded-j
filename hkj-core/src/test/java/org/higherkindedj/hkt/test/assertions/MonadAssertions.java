// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

/**
 * Monad-specific assertion shortcuts.
 *
 * <p>Provides convenient methods for common Monad operation assertions by composing from {@link
 * FunctionAssertions} and {@link KindAssertions}. This class makes tests more readable by providing
 * operation-specific method names rather than generic ones.
 *
 * <p>All assertions align with the production validation framework and use the same context-based
 * error messages as {@link org.higherkindedj.hkt.util.validation.FunctionValidator} and {@link
 * org.higherkindedj.hkt.util.validation.KindValidator}.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Map Operation:</h3>
 *
 * <pre>{@code
 * // Instead of generic:
 * FunctionAssertions.assertMapperNull(() -> monad.map(null, kind), "map");
 *
 * // Use specific:
 * MonadAssertions.assertMapFunctionNull(() -> monad.map(null, kind));
 *
 * // For Kind parameter:
 * MonadAssertions.assertMapKindNull(() -> monad.map(validMapper, null));
 * }</pre>
 *
 * <h3>FlatMap Operation:</h3>
 *
 * <pre>{@code
 * // Function parameter
 * assertFlatMapFunctionNull(() -> monad.flatMap(null, validKind));
 *
 * // Kind parameter
 * assertFlatMapKindNull(() -> monad.flatMap(validFlatMapper, null));
 * }</pre>
 *
 * <h3>Ap Operation:</h3>
 *
 * <pre>{@code
 * // Function Kind parameter
 * assertApFunctionKindNull(() -> monad.ap(null, validKind));
 *
 * // Argument Kind parameter
 * assertApArgumentKindNull(() -> monad.ap(validFunctionKind, null));
 * }</pre>
 *
 * <h3>Complete Validation:</h3>
 *
 * <pre>{@code
 * // Test all monad operations at once
 * assertAllMonadOperations(
 *     monad,
 *     validKind,
 *     validMapper,
 *     validFlatMapper,
 *     validFunctionKind
 * );
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>Composes {@link FunctionAssertions} and {@link KindAssertions}
 *   <li>Operation-specific naming for better readability
 *   <li>Consistent with Monad interface method names
 *   <li>Error messages match production validators exactly
 * </ul>
 *
 * @see org.higherkindedj.hkt.Monad
 * @see FunctionAssertions
 * @see KindAssertions
 */
public final class MonadAssertions {

  private MonadAssertions() {
    throw new AssertionError("MonadAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Map Operation Assertions
  // =============================================================================

  /**
   * Asserts that map operation throws when function parameter is null.
   *
   * <p>Tests the validation: {@code monad.map(null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Functor#map(java.util.function.Function, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMapperNull(executable, "map");
  }

  /**
   * Asserts that map operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.map(validMapper, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Functor#map(java.util.function.Function, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map");
  }

  // =============================================================================
  // FlatMap Operation Assertions
  // =============================================================================

  /**
   * Asserts that flatMap operation throws when function parameter is null.
   *
   * <p>Tests the validation: {@code monad.flatMap(null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Monad#flatMap(java.util.function.Function, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFlatMapperNull(executable, "flatMap");
  }

  /**
   * Asserts that flatMap operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.flatMap(validFlatMapper, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Monad#flatMap(java.util.function.Function, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "flatMap");
  }

  // =============================================================================
  // Ap Operation Assertions
  // =============================================================================

  /**
   * Asserts that ap operation throws when function Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.ap(null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#ap(Kind, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApFunctionKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "ap_function");
  }

  /**
   * Asserts that ap operation throws when argument Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.ap(validFunctionKind, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#ap(Kind, Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApArgumentKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "ap_argument");
  }

  // =============================================================================
  // Of Operation Assertions
  // =============================================================================

  /**
   * Asserts that of operation accepts null values (if the Monad allows it).
   *
   * <p>Note: This is informational - some Monads allow null in {@code of()}, others don't. Use this
   * to document expected behavior.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * // For Maybe monad (allows null -> Nothing)
   * assertOfAcceptsNull(() -> maybeMonad.of(null));
   *
   * // For Either monad (allows null in Right)
   * assertOfAcceptsNull(() -> eitherMonad.of(null));
   * }</pre>
   *
   * @param executable The code that should NOT throw
   * @throws AssertionError if an exception is thrown unexpectedly
   * @see org.higherkindedj.hkt.Applicative#of(Object)
   */
  public static void assertOfAcceptsNull(ThrowableAssert.ThrowingCallable executable) {
    org.assertj.core.api.Assertions.assertThatCode(executable)
        .as("of() should accept null values for this Monad")
        .doesNotThrowAnyException();
  }

  // =============================================================================
  // MapN Operation Assertions
  // =============================================================================

  /**
   * Asserts that map2 operation throws when first Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.map2(null, validKind, validFunction)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(Kind, Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2FirstKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map2_first");
  }

  /**
   * Asserts that map2 operation throws when second Kind parameter is null.
   *
   * <p>Tests the validation: {@code monad.map2(validKind, null, validFunction)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(Kind, Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2SecondKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "map2_second");
  }

  /**
   * Asserts that map2 operation throws when function parameter is null.
   *
   * <p>Tests the validation: {@code monad.map2(validKind1, validKind2, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Applicative#map2(Kind, Kind, java.util.function.BiFunction)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2FunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "map2");
  }

  // =============================================================================
  // Convenience Methods for Complete Testing
  // =============================================================================

  /**
   * Asserts all basic Monad operations for null parameters.
   *
   * <p>This is a convenience method that tests all three core Monad operations (map, flatMap, ap)
   * for null parameter handling in a single call.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * assertAllMonadOperations(
   *     monad,
   *     validKind,
   *     validMapper,
   *     validFlatMapper,
   *     validFunctionKind
   * );
   * }</pre>
   *
   * @param monad The Monad instance to test
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap
   * @param <F> The Monad witness type
   * @param <A> The input type
   * @param <B> The output type
   */
  public static <F, A, B> void assertAllMonadOperations(
      Monad<F> monad,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    // Map validations
    assertMapFunctionNull(() -> monad.map(null, validKind));
    assertMapKindNull(() -> monad.map(validMapper, null));

    // FlatMap validations
    assertFlatMapFunctionNull(() -> monad.flatMap(null, validKind));
    assertFlatMapKindNull(() -> monad.flatMap(validFlatMapper, null));

    // Ap validations
    assertApFunctionKindNull(() -> monad.ap(null, validKind));
    assertApArgumentKindNull(() -> monad.ap(validFunctionKind, null));
  }

  // =============================================================================
  // MonadError-Specific Assertions
  // =============================================================================

  /**
   * Asserts that handleErrorWith operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code monadError.handleErrorWith(null, handler)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.MonadError#handleErrorWith(Kind, java.util.function.Function)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertHandleErrorWithKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "handleErrorWith");
  }

  /**
   * Asserts that handleErrorWith operation throws when handler function is null.
   *
   * <p>Tests the validation: {@code monadError.handleErrorWith(validKind, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.MonadError#handleErrorWith(Kind, java.util.function.Function)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertHandleErrorWithHandlerNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "error handler", "handleErrorWith");
  }
}
