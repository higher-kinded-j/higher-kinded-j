// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;

/**
 * Foldable-specific assertion shortcuts.
 *
 * <p>Provides convenient methods for Foldable operation assertions by composing from {@link
 * FunctionAssertions} and {@link KindAssertions}. This class focuses on fold operations like {@code
 * foldMap}, {@code foldLeft}, and {@code foldRight}.
 *
 * <p>All assertions align with the production validation framework and use the same context-based
 * error messages as {@link org.higherkindedj.hkt.util.validation.FunctionValidator} and {@link
 * org.higherkindedj.hkt.util.validation.KindValidator}.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>FoldMap Operation:</h3>
 *
 * <pre>{@code
 * // Monoid parameter
 * assertFoldMapMonoidNull(() -> foldable.foldMap(null, validFunction, validKind));
 *
 * // Mapping function parameter
 * assertFoldMapFunctionNull(() -> foldable.foldMap(validMonoid, null, validKind));
 *
 * // Kind parameter
 * assertFoldMapKindNull(() -> foldable.foldMap(validMonoid, validFunction, null));
 * }</pre>
 *
 * <h3>FoldLeft Operation (if implemented):</h3>
 *
 * <pre>{@code
 * // Initial value can be null for some types
 * assertFoldLeftFunctionNull(() -> foldable.foldLeft(null, validSeed, validKind));
 *
 * // Kind parameter
 * assertFoldLeftKindNull(() -> foldable.foldLeft(validFunction, validSeed, null));
 * }</pre>
 *
 * <h3>FoldRight Operation (if implemented):</h3>
 *
 * <pre>{@code
 * assertFoldRightFunctionNull(() -> foldable.foldRight(null, validSeed, validKind));
 * assertFoldRightKindNull(() -> foldable.foldRight(validFunction, validSeed, null));
 * }</pre>
 *
 * <h3>Complete Validation:</h3>
 *
 * <pre>{@code
 * // Test all foldMap validations
 * assertAllFoldMapOperations(foldable, validMonoid, validFunction, validKind);
 * }</pre>
 *
 * <h2>Design Notes:</h2>
 *
 * <ul>
 *   <li>Composes {@link FunctionAssertions} and {@link KindAssertions}
 *   <li>Operation-specific naming for better readability
 *   <li>Consistent with Foldable interface method names
 *   <li>Supports standard fold operations
 *   <li>Monoid validation uses specialized assertions
 * </ul>
 *
 * @see org.higherkindedj.hkt.Foldable
 * @see FunctionAssertions
 * @see KindAssertions
 */
public final class FoldableAssertions {

  private FoldableAssertions() {
    throw new AssertionError(
        "FoldableAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // FoldMap Operation Assertions
  // =============================================================================

  /**
   * Asserts that foldMap operation throws when monoid parameter is null.
   *
   * <p>Tests the validation: {@code foldable.foldMap(null, validFunction, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Foldable#foldMap(org.higherkindedj.hkt.Monoid,
   *     java.util.function.Function, org.higherkindedj.hkt.Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapMonoidNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMonoidNull(executable, "foldMap");
  }

  /**
   * Asserts that foldMap operation throws when mapping function is null.
   *
   * <p>Tests the validation: {@code foldable.foldMap(validMonoid, null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Foldable#foldMap(org.higherkindedj.hkt.Monoid,
   *     java.util.function.Function, org.higherkindedj.hkt.Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMapperNull(executable, "foldMap");
  }

  /**
   * Asserts that foldMap operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code foldable.foldMap(validMonoid, validFunction, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   * @see org.higherkindedj.hkt.Foldable#foldMap(org.higherkindedj.hkt.Monoid,
   *     java.util.function.Function, org.higherkindedj.hkt.Kind)
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "foldMap");
  }

  // =============================================================================
  // FoldLeft Operation Assertions (for implementations that provide it)
  // =============================================================================

  /**
   * Asserts that foldLeft operation throws when folding function is null.
   *
   * <p>Tests the validation: {@code foldable.foldLeft(null, seed, validKind)}
   *
   * <p><b>Note:</b> Not all Foldable implementations provide foldLeft. This is for implementations
   * that do provide it.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldLeftFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "folding function", "foldLeft");
  }

  /**
   * Asserts that foldLeft operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code foldable.foldLeft(validFunction, seed, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldLeftKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "foldLeft");
  }

  /**
   * Asserts that foldLeft accepts null seed values (if implementation allows it).
   *
   * <p>Some foldable implementations may allow null seed values. Use this to document that
   * behavior.
   *
   * @param executable The code that should NOT throw
   * @throws AssertionError if an exception is thrown unexpectedly
   */
  public static void assertFoldLeftAcceptsNullSeed(ThrowableAssert.ThrowingCallable executable) {
    assertThatCode(executable)
        .as("foldLeft should accept null seed values for this implementation")
        .doesNotThrowAnyException();
  }

  // =============================================================================
  // FoldRight Operation Assertions (for implementations that provide it)
  // =============================================================================

  /**
   * Asserts that foldRight operation throws when folding function is null.
   *
   * <p>Tests the validation: {@code foldable.foldRight(null, seed, validKind)}
   *
   * <p><b>Note:</b> Not all Foldable implementations provide foldRight. This is for implementations
   * that do provide it.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldRightFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "folding function", "foldRight");
  }

  /**
   * Asserts that foldRight operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code foldable.foldRight(validFunction, seed, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldRightKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "foldRight");
  }

  /**
   * Asserts that foldRight accepts null seed values (if implementation allows it).
   *
   * @param executable The code that should NOT throw
   * @throws AssertionError if an exception is thrown unexpectedly
   */
  public static void assertFoldRightAcceptsNullSeed(ThrowableAssert.ThrowingCallable executable) {
    assertThatCode(executable)
        .as("foldRight should accept null seed values for this implementation")
        .doesNotThrowAnyException();
  }

  // =============================================================================
  // Fold Operation Assertions (for implementations with generic fold)
  // =============================================================================

  /**
   * Asserts that generic fold operation throws when folding function is null.
   *
   * <p>Some Foldable implementations provide a generic {@code fold} method.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "folding function", "fold");
  }

  /**
   * Asserts that generic fold operation throws when Kind parameter is null.
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "fold");
  }

  // =============================================================================
  // Reduce Operation Assertions (for implementations with reduce)
  // =============================================================================

  /**
   * Asserts that reduce operation throws when combining function is null.
   *
   * <p>Tests the validation: {@code foldable.reduce(null, validKind)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertReduceFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertFunctionNull(executable, "combining function", "reduce");
  }

  /**
   * Asserts that reduce operation throws when Kind parameter is null.
   *
   * <p>Tests the validation: {@code foldable.reduce(validFunction, null)}
   *
   * @param executable The code that should throw
   * @return Throwable assertion for further chaining
   * @throws AssertionError if exception not thrown or has wrong message
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertReduceKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "reduce");
  }

  // =============================================================================
  // Convenience Methods for Complete Testing
  // =============================================================================

  /**
   * Asserts all foldMap operation validations.
   *
   * <p>Tests all three parameters of foldMap for null handling.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * assertAllFoldMapOperations(
   *     foldable,
   *     stringMonoid,
   *     i -> "Value: " + i,
   *     validKind
   * );
   * }</pre>
   *
   * @param foldable The Foldable instance to test
   * @param validMonoid Valid Monoid for combining results
   * @param validFunction Valid mapping function
   * @param validKind Valid Kind to fold
   * @param <F> The Foldable type witness
   * @param <A> The element type in the structure
   * @param <M> The Monoid type
   */
  public static <F, A, M> void assertAllFoldMapOperations(
      Foldable<F> foldable,
      Monoid<M> validMonoid,
      Function<A, M> validFunction,
      Kind<F, A> validKind) {

    assertFoldMapMonoidNull(() -> foldable.foldMap(null, validFunction, validKind));
    assertFoldMapFunctionNull(() -> foldable.foldMap(validMonoid, null, validKind));
    assertFoldMapKindNull(() -> foldable.foldMap(validMonoid, validFunction, null));
  }

  /**
   * Asserts all foldLeft operation validations (for implementations that provide it).
   *
   * <p>Tests function and Kind parameters for null handling.
   *
   * @param foldable The Foldable instance to test
   * @param validFunction Valid folding function
   * @param validSeed Valid seed/initial value
   * @param validKind Valid Kind to fold
   * @param <F> The Foldable type witness
   * @param <A> The element type
   * @param <B> The accumulator type
   */
  public static <F, A, B> void assertAllFoldLeftOperations(
      Foldable<F> foldable, BiFunction<B, A, B> validFunction, B validSeed, Kind<F, A> validKind) {

    // Note: This assumes foldable has a foldLeft method
    // Individual implementations should provide this method
    assertFoldLeftFunctionNull(() -> invokeFoldLeft(foldable, null, validSeed, validKind));
    assertFoldLeftKindNull(() -> invokeFoldLeft(foldable, validFunction, validSeed, null));
  }

  /**
   * Asserts all foldable operations comprehensively.
   *
   * <p>Tests foldMap and any additional fold operations provided by the implementation.
   *
   * @param foldable The Foldable instance to test
   * @param validMonoid Valid Monoid
   * @param validFoldMapFunction Valid foldMap function
   * @param validKind Valid Kind
   * @param <F> The Foldable type witness
   * @param <A> The element type
   * @param <M> The Monoid type
   */
  public static <F, A, M> void assertAllFoldableOperations(
      Foldable<F> foldable,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction,
      Kind<F, A> validKind) {

    // Always test foldMap (required by Foldable interface)
    assertAllFoldMapOperations(foldable, validMonoid, validFoldMapFunction, validKind);

    // Additional operations would be tested by specific implementations
  }

  // =============================================================================
  // Helper Methods
  // =============================================================================

  /**
   * Helper to invoke foldLeft reflectively (if it exists on the implementation).
   *
   * <p>This is a placeholder - actual implementations should provide foldLeft directly or use a
   * method reference.
   */
  private static <F, A, B> B invokeFoldLeft(
      Foldable<F> foldable, BiFunction<B, A, B> function, B seed, Kind<F, A> kind) {
    // This would need to be implemented by specific Foldable instances
    // that provide foldLeft. It's here as a placeholder for the assertion pattern.
    throw new UnsupportedOperationException(
        "foldLeft is not part of the base Foldable interface. "
            + "Specific implementations should test their foldLeft directly.");
  }
}
