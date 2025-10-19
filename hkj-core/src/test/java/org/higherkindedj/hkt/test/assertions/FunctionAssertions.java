// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.Operation;

/**
 * Function parameter validation assertions aligned with production validators.
 *
 * <p>This class provides assertions that use the exact same validation logic as production {@link
 * FunctionValidator}, ensuring that test expectations match production behavior exactly.
 *
 * <h2>Key Benefits:</h2>
 *
 * <ul>
 *   <li>Error messages automatically match production validators
 *   <li>Changes to validation logic propagate to tests automatically
 *   <li>Single source of truth for validation behavior
 *   <li>Context-aware error messages with class-based contexts
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Function Validation:</h3>
 *
 * <pre>{@code
 * // Test mapper function validation
 * assertMapperNull(() -> functor.map(null, validKind), "map");
 *
 * // Test flatMapper function validation
 * assertFlatMapperNull(() -> monad.flatMap(null, validKind), "flatMap");
 *
 * // Test applicative validation
 * assertApplicativeNull(() -> traverse.traverse(null, validFunction, validKind), "traverse");
 * }</pre>
 *
 * <h3>Class-Based Context Validation:</h3>
 *
 * <pre>{@code
 * // Test with class context for better error messages
 * assertMapperNull(() -> monad.map(null, validKind), EitherMonad.class, "map");
 * // Error: "function f for EitherMonad.map cannot be null"
 *
 * assertFlatMapperNull(() -> monad.flatMap(null, validKind), StateTMonad.class, "flatMap");
 * // Error: "function f for StateTMonad.flatMap cannot be null"
 * }</pre>
 *
 * <h3>Custom Function Validation:</h3>
 *
 * <pre>{@code
 * // Test custom function with specific name
 * assertFunctionNull(() -> transformer.create(null), "createFunction", TransformerT.class, "create");
 * // Error: "createFunction for TransformerT.create cannot be null"
 * }</pre>
 *
 * @see FunctionValidator
 * @see org.higherkindedj.hkt.util.context.FunctionContext
 */
public final class FunctionAssertions {

  private FunctionAssertions() {
    throw new AssertionError(
        "FunctionAssertions is a utility class and should not be instantiated");
  }

  // =============================================================================
  // Basic Function Validation (Operation Context Only)
  // =============================================================================

  /**
   * Asserts mapper function validation using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapperNull(
      ThrowableAssert.ThrowingCallable executable, String mapperName, Operation operation) {
    return assertWithProductionValidator(
        executable, () -> FunctionValidator.requireMapper(null, mapperName, operation.toString()));
  }

  /**
   * Asserts flatMapper function validation using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable, String flatMapperName, Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireFlatMapper(null, flatMapperName, operation.toString()));
  }

  /**
   * Asserts applicative validation using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, String applicativeName, Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireApplicative(null, applicativeName, operation.toString()));
  }

  /**
   * Asserts monoid validation using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param monoidName The name of the monoid parameter
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable, String monoidName, Operation operation) {
    return assertWithProductionValidator(
        executable, () -> FunctionValidator.requireMonoid(null, monoidName, operation.toString()));
  }

  /**
   * Asserts generic function validation using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param functionName The name of the function parameter
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName, Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireFunction(null, functionName, operation.toString()));
  }

  // =============================================================================
  // Class-Based Context Validation
  // =============================================================================

  /**
   * Asserts mapper function validation with class context using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context (e.g., EitherMonad.class)
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMapperNull(
      ThrowableAssert.ThrowingCallable executable,
      String mapperName,
      Class<?> contextClass,
      Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireMapper(null, mapperName, contextClass, operation));
  }

  /**
   * Asserts flatMapper function validation with class context using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable,
      String flatMapperName,
      Class<?> contextClass,
      Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireFlatMapper(null, flatMapperName, contextClass, operation));
  }

  /**
   * Asserts applicative validation with class context using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable,
      String applicativeName,
      Class<?> contextClass,
      Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireApplicative(null, applicativeName, contextClass, operation));
  }

  /**
   * Asserts monoid validation with class context using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param monoidName The name of the monoid parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable,
      String monoidName,
      Class<?> contextClass,
      Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireMonoid(null, monoidName, contextClass, operation));
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass, Operation operation) {
    return assertWithProductionValidator(
        executable, () -> FunctionValidator.requireMonoid(null, "monoid", contextClass, operation));
  }

  /**
   * Asserts custom function validation with class context using production FunctionValidator.
   *
   * @param executable The code that should throw
   * @param functionName The name of the function parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   * @throws AssertionError if validation doesn't match production behavior
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable,
      String functionName,
      Class<?> contextClass,
      Operation operation) {
    return assertWithProductionValidator(
        executable,
        () -> FunctionValidator.requireFunction(null, functionName, contextClass, operation));
  }

  /**
   * Asserts handler function validation (commonly used in error handling).
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return Throwable assertion for further chaining
   */
  public static AbstractThrowableAssert<?, ? extends Throwable> assertHandlerNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass, Operation operation) {
    return assertWithProductionValidator(
        executable, () -> FunctionValidator.requireHandler(null, contextClass, operation));
  }

  // =============================================================================
  // Core Production Validator Integration
  // =============================================================================

  /**
   * Core method that ensures test assertions match production validation exactly.
   *
   * <p>This method executes production validation to capture the expected exception, then verifies
   * that test code throws an identical exception.
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
        .as("Test validation should match production FunctionValidator exactly");
  }
}
