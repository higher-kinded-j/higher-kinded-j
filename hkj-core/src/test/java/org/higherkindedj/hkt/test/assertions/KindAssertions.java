// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Kind parameter validation assertions aligned with production validators.
 *
 * <p>This class provides assertions that use the exact same validation logic as production {@link
 * KindValidator}, ensuring that test expectations match production behavior exactly.
 *
 * <h2>Key Benefits:</h2>
 *
 * <ul>
 *   <li>Error messages automatically match production validators
 *   <li>Context-aware error messages with class and descriptor support
 *   <li>Type-safe validation assertions
 *   <li>Single source of truth for Kind validation behavior
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Kind Validation:</h3>
 *
 * <pre>{@code
 * // Test basic Kind null validation
 * assertKindNull(() -> monad.map(validMapper, null), "map");
 *
 * // Test with descriptor for multi-parameter operations
 * assertKindNull(() -> monad.ap(null, validKind), AP, "function");
 * assertKindNull(() -> monad.ap(validFunctionKind, null), AP, "argument");
 * }</pre>
 *
 * <h3>Class-Based Context Validation:</h3>
 *
 * <pre>{@code
 * // Test with class context for better error messages
 * assertKindNull(() -> monad.map(validMapper, null), EitherMonad.class, "map");
 * // Error: "Kind for EitherMonad.map cannot be null"
 *
 * assertKindNull(() -> monad.ap(null, validKind), StateTMonad.class, AP, "function");
 * // Error: "Kind for StateTMonad.ap (function) cannot be null"
 * }</pre>
 *
 * <h3>Narrow/Widen Validation:</h3>
 *
 * <pre>{@code
 * // Test narrow validation
 * assertNarrowNull(() -> helper.narrow(null), Either.class);
 *
 * // Test widen validation
 * assertWidenNull(() -> helper.widen(null), Either.class);
 *
 * // Test invalid type validation
 * assertInvalidKindType(() -> helper.narrow(invalidKind), Either.class, invalidKind);
 * }</pre>
 *
 * @see KindValidator
 */
public final class KindAssertions {

  private KindAssertions() {
    throw new AssertionError("KindAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Basic Kind Validation (Operation Context Only)
  // =============================================================================

  /**
   * Asserts Kind null validation using production KindValidator.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Operation operation) {
    return assertWithProductionValidator(
        executable, () -> Validation.kind().requireNonNull((Kind<?, ?>) null, operation));
  }

  /**
   * Asserts Kind null validation with descriptor using production KindValidator.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @param descriptor Optional descriptor for the parameter (e.g., "function", "argument")
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Operation operation, String descriptor) {
    return assertWithProductionValidator(
        executable,
        () -> Validation.kind().requireNonNull((Kind<?, ?>) null, operation, descriptor));
  }

  // =============================================================================
  // Class-Based Context Validation
  // =============================================================================

  /**
   * Asserts Kind null validation with class context using production KindValidator.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context (e.g., EitherMonad.class)
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass, Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> Validation.kind().requireNonNull((Kind<?, ?>) null, contextClass, operation));
  }

  /**
   * Asserts Kind null validation with class context and descriptor using production KindValidator.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @param descriptor Optional descriptor for the parameter
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertKindNull(
      ThrowableAssert.ThrowingCallable executable,
      Class<?> contextClass,
      Operation operation,
      String descriptor) {
    return assertWithProductionValidator(
        executable,
        () ->
            Validation.kind()
                .requireNonNull((Kind<?, ?>) null, contextClass, operation, descriptor));
  }

  // =============================================================================
  // Narrow/Widen Validation
  // =============================================================================

  /**
   * Asserts narrow validation for null Kind using production KindValidator.
   *
   * @param executable The code that should throw
   * @param targetType The target type class for narrowing
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertNarrowNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {
    return assertKindUnwrapException(
        executable,
        () -> {
          Kind<Object, Object> nullKind = null;
          // Cast targetType to Class<Object> for the generic method call
          @SuppressWarnings("unchecked")
          Class<Object> objectTargetType = (Class<Object>) targetType;
          Validation.kind()
              .<Object, Object, Object>narrow(nullKind, objectTargetType, kind -> new Object());
        });
  }

  /**
   * Asserts narrow validation for invalid Kind type using production KindValidator.
   *
   * @param executable The code that should throw
   * @param targetType The target type class for narrowing
   * @param invalidKind The invalid Kind for error context
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertInvalidKindType(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType, Kind<?, ?> invalidKind) {

    // We expect the actual code to throw KindUnwrapException when narrowing an invalid Kind
    return assertThatThrownBy(executable)
        .isInstanceOf(org.higherkindedj.hkt.exception.KindUnwrapException.class)
        .hasMessageContaining("Kind instance cannot be narrowed to " + targetType.getSimpleName());
  }

  /**
   * Asserts widen validation for null input using production KindValidator.
   *
   * @param executable The code that should throw
   * @param inputType The input type class for widening
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertWidenNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> inputType) {
    return assertWithProductionValidator(
        executable, () -> Validation.kind().requireForWiden(null, inputType));
  }

  // =============================================================================
  // Specialized Assertions for Common Patterns
  // =============================================================================

  /**
   * Asserts ap function Kind validation (commonly used in applicative operations).
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApFunctionKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, AP, "function");
  }

  /**
   * Asserts ap argument Kind validation (commonly used in applicative operations).
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApArgumentKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, AP, "argument");
  }

  /**
   * Asserts map2 first Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2FirstKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, MAP_2, "first");
  }

  /**
   * Asserts map2 second Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMap2SecondKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, MAP_2, "second");
  }

  /**
   * Asserts traverse Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, TRAVERSE);
  }

  /**
   * Asserts foldMap Kind validation.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass) {
    return assertKindNull(executable, contextClass, FOLD_MAP);
  }

  // =============================================================================
  // Core Production Validator Integration
  // =============================================================================

  /**
   * Core method for NullPointerException validation that ensures test assertions match production
   * validation exactly.
   *
   * @param testCode Code that should throw the same exception as production validation
   * @param productionValidation Production validation logic that defines expected behavior
   * @return Throwable assertion for further chaining
   * @throws AssertionError if test behavior doesn't match production validation
   */
  private static AbstractThrowableAssert<?, ? extends Throwable> assertWithProductionValidator(
      ThrowableAssert.ThrowingCallable testCode,
      ThrowableAssert.ThrowingCallable productionValidation) {

    // Capture expected exception from production validation
    Throwable expectedThrowable = null;
    try {
      productionValidation.call();
      throw new AssertionError("Production validation should have thrown an exception");
    } catch (Throwable t) {
      expectedThrowable = t;
    }

    // Verify test code throws exactly the same exception
    final Throwable expected = expectedThrowable;
    return assertThatThrownBy(testCode)
        .isInstanceOf(expected.getClass())
        .hasMessage(expected.getMessage())
        .as("Test validation should match production KindValidator exactly");
  }

  /**
   * Core method for KindUnwrapException validation that ensures test assertions match production
   * validation exactly.
   *
   * @param testCode Code that should throw KindUnwrapException
   * @param productionValidation Production validation logic that defines expected behavior
   * @return Throwable assertion for further chaining
   * @throws AssertionError if test behavior doesn't match production validation
   */
  private static AbstractThrowableAssert<?, ? extends Throwable> assertKindUnwrapException(
      ThrowableAssert.ThrowingCallable testCode,
      ThrowableAssert.ThrowingCallable productionValidation) {

    // Capture expected exception from production validation
    Throwable expectedThrowable = null;
    try {
      productionValidation.call();
      throw new AssertionError("Production validation should have thrown an exception");
    } catch (Throwable t) {
      expectedThrowable = t;
    }

    // Verify test code throws exactly the same exception
    final Throwable expected = expectedThrowable;
    return assertThatThrownBy(testCode)
        .isInstanceOf(KindUnwrapException.class)
        .hasMessage(expected.getMessage())
        .as("Test validation should match production KindValidator exactly");
  }
}
