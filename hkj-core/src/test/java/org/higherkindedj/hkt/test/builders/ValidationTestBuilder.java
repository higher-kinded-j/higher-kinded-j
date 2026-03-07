// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.builders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.test.assertions.FunctionAssertions;
import org.higherkindedj.hkt.test.assertions.KindAssertions;
import org.higherkindedj.hkt.test.assertions.TypeClassAssertions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Fluent builder for testing multiple validation conditions using standardised framework.
 *
 * <p>Allows chaining multiple validation assertions and executing them all together, collecting
 * failures for comprehensive error reporting. All assertions use production validators to ensure
 * test expectations match actual implementation behaviour.
 *
 * <h2>Key Benefits:</h2>
 *
 * <ul>
 *   <li>Fluent API for readable test construction
 *   <li>Comprehensive error reporting for multiple failures
 *   <li>Production-aligned validation using standardised framework
 *   <li>Type-safe validation with class-based contexts
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Validation Chain:</h3>
 *
 * <pre>{@code
 * ValidationTestBuilder.create()
 *     .assertMapperNull(() -> functor.map(null, validKind), MyFunctor.class, "map")
 *     .assertKindNull(() -> functor.map(validMapper, null), MyFunctor.class, "map")
 *     .assertFlatMapperNull(() -> monad.flatMap(null, validKind), MyMonad.class, FLAT_MAP)
 *     .execute();
 * }</pre>
 *
 * <h3>Complete Type Class Validation:</h3>
 *
 * <pre>{@code
 * ValidationTestBuilder.create()
 *     .assertAllMonadOperations(monad, MyMonad.class, validKind, validMapper, validFlatMapper, validFunctionKind)
 *     .assertAllTraverseOperations(traverse, MyTraverse.class, validKind, validMapper,
 *         validApplicative, validTraverseFunction, validMonoid, validFoldMapFunction)
 *     .execute();
 * }</pre>
 *
 * <h3>Custom Error Context:</h3>
 *
 * <pre>{@code
 * ValidationTestBuilder.create()
 *     .assertKindNull(() -> monad.ap(null, validKind), MyMonad.class, AP, "function")
 *     .assertKindNull(() -> monad.ap(validFunctionKind, null), MyMonad.class, AP, "argument")
 *     .execute();
 * }</pre>
 *
 * @see FunctionAssertions
 * @see KindAssertions
 * @see TypeClassAssertions
 */
public final class ValidationTestBuilder {

  private final List<ValidationAssertion> assertions = new ArrayList<>();

  private ValidationTestBuilder() {}

  /**
   * Creates a new ValidationTestBuilder.
   *
   * @return A new builder instance
   */
  public static ValidationTestBuilder create() {
    return new ValidationTestBuilder();
  }

  /**
   * Adds core type value validation to the test suite.
   *
   * @param executable The code that should throw
   * @param valueName The name of the value parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertValueNull(
      ThrowableAssert.ThrowingCallable executable,
      String valueName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () -> {
          // Capture expected exception from production validation
          Throwable expectedThrowable = null;
          try {
            // This simulates what the production validator would do
            Validation.coreType().requireValue(null, valueName, contextClass, operation);
            throw new AssertionError("Production validation should have thrown an exception");
          } catch (Throwable t) {
            expectedThrowable = t;
          }

          // Verify test code throws exactly the same exception
          final Throwable expected = expectedThrowable;
          assertThatThrownBy(executable)
              .isInstanceOf(expected.getClass())
              .hasMessage(expected.getMessage())
              .as("Test validation should match production CoreTypeValidator exactly");
        });
    return this;
  }

  /**
   * Adds mapper function validation to the test suite.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertMapperNull(
      ThrowableAssert.ThrowingCallable executable, String mapperName, Operation operation) {
    assertions.add(() -> FunctionAssertions.assertMapperNull(executable, mapperName, operation));
    return this;
  }

  /**
   * Adds flatMapper function validation to the test suite.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable, String flatMapperName, Operation operation) {
    assertions.add(
        () -> FunctionAssertions.assertFlatMapperNull(executable, flatMapperName, operation));
    return this;
  }

  /**
   * Adds applicative validation to the test suite.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable, String applicativeName, Operation operation) {
    assertions.add(
        () -> FunctionAssertions.assertApplicativeNull(executable, applicativeName, operation));
    return this;
  }

  /**
   * Adds monoid validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param monoidName The name of the monoid parameter
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable, String monoidName, Operation operation) {
    assertions.add(() -> FunctionAssertions.assertMonoidNull(executable, monoidName, operation));
    return this;
  }

  /**
   * Adds custom function validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param functionName The name of the function parameter
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable, String functionName, Operation operation) {
    assertions.add(
        () -> FunctionAssertions.assertFunctionNull(executable, functionName, operation));
    return this;
  }

  // =============================================================================
  // Kind Validation Methods
  // =============================================================================

  /**
   * Adds Kind null validation to the test suite.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Operation operation) {
    assertions.add(() -> KindAssertions.assertKindNull(executable, operation));
    return this;
  }

  /**
   * Adds Kind null validation with descriptor to the test suite.
   *
   * @param executable The code that should throw
   * @param operation The operation name
   * @param descriptor Optional descriptor for the parameter
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Operation operation, String descriptor) {
    assertions.add(() -> KindAssertions.assertKindNull(executable, operation, descriptor));
    return this;
  }

  /**
   * Adds narrow null validation to the test suite.
   *
   * @param executable The code that should throw
   * @param targetType The target type class
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertNarrowNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType) {
    assertions.add(() -> KindAssertions.assertNarrowNull(executable, targetType));
    return this;
  }

  /**
   * Adds widen null validation to the test suite.
   *
   * @param executable The code that should throw
   * @param inputType The input type class
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertWidenNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> inputType) {
    assertions.add(() -> KindAssertions.assertWidenNull(executable, inputType));
    return this;
  }

  // =============================================================================
  // Complete Type Class Validation Methods
  // =============================================================================

  /**
   * Adds transformer outer monad validation to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertTransformerOuterMonadNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass, Operation operation) {
    assertions.add(
        () -> {
          // Capture expected exception from production validation
          Throwable expectedThrowable = null;
          try {
            Validation.transformer().requireOuterMonad(null, contextClass, operation);
            throw new AssertionError("Production validation should have thrown an exception");
          } catch (Throwable t) {
            expectedThrowable = t;
          }

          // Verify test code throws exactly the same exception
          final Throwable expected = expectedThrowable;
          assertThatThrownBy(executable)
              .isInstanceOf(expected.getClass())
              .hasMessage(expected.getMessage())
              .as("Test validation should match production DomainValidator exactly");
        });
    return this;
  }

  /**
   * Adds transformer component validation to the test suite.
   *
   * @param executable The code that should throw
   * @param componentName The name of the component (e.g., "inner Either")
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertTransformerComponentNull(
      ThrowableAssert.ThrowingCallable executable,
      String componentName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () -> {
          // Capture expected exception from production validation
          Throwable expectedThrowable = null;
          try {
            Validation.transformer()
                .requireTransformerComponent(null, componentName, contextClass, operation);
            throw new AssertionError("Production validation should have thrown an exception");
          } catch (Throwable t) {
            expectedThrowable = t;
          }

          // Verify test code throws exactly the same exception
          final Throwable expected = expectedThrowable;
          assertThatThrownBy(executable)
              .isInstanceOf(expected.getClass())
              .hasMessage(expected.getMessage())
              .as("Test validation should match production DomainValidator exactly");
        });
    return this;
  }

  // =============================================================================
  // Execution Methods
  // =============================================================================

  /**
   * Execute all validation assertions.
   *
   * <p>Each assertion runs independently. All failures are collected and reported together to
   * provide complete validation feedback. If any assertions fail, a combined AssertionError is
   * thrown with all individual failures as suppressed exceptions.
   *
   * @throws AssertionError if any validation assertions fail
   */
  public void execute() {
    if (assertions.isEmpty()) {
      throw new IllegalStateException(
          "No validation assertions configured. Use assertXxx() methods to add assertions before"
              + " calling execute().");
    }

    var failures = new ArrayList<AssertionError>();

    for (int i = 0; i < assertions.size(); i++) {
      try {
        assertions.get(i).run();
      } catch (AssertionError e) {
        failures.add(new AssertionError("Validation assertion " + (i + 1) + " failed", e));
      } catch (Exception e) {
        failures.add(
            new AssertionError(
                "Validation assertion " + (i + 1) + " failed with unexpected exception", e));
      }
    }

    if (!failures.isEmpty()) {
      AssertionError combined =
          new AssertionError(failures.size() + " validation assertion(s) failed");
      for (AssertionError failure : failures) {
        combined.addSuppressed(failure);
      }
      throw combined;
    }
  }

  /**
   * Gets the number of validation assertions configured.
   *
   * @return The number of assertions that will be executed
   */
  public int size() {
    return assertions.size();
  }

  /**
   * Checks if any validation assertions have been configured.
   *
   * @return true if no assertions have been added, false otherwise
   */
  public boolean isEmpty() {
    return assertions.isEmpty();
  }

  // =============================================================================
  // Helper Interface
  // =============================================================================

  /** Represents a single validation assertion that can throw an exception. */
  @FunctionalInterface
  private interface ValidationAssertion {
    /**
     * Runs the validation assertion.
     *
     * @throws Exception if the assertion fails
     */
    void run() throws Exception;
  }
}
