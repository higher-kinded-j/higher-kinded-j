// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.builders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.test.assertions.FunctionAssertions;
import org.higherkindedj.hkt.test.assertions.KindAssertions;
import org.higherkindedj.hkt.test.assertions.TypeClassAssertions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Fluent builder for testing multiple validation conditions using standardized framework.
 *
 * <p>Allows chaining multiple validation assertions and executing them all together, collecting
 * failures for comprehensive error reporting. All assertions use production validators to ensure
 * test expectations match actual implementation behavior.
 *
 * <h2>Key Benefits:</h2>
 *
 * <ul>
 *   <li>Fluent API for readable test construction
 *   <li>Comprehensive error reporting for multiple failures
 *   <li>Production-aligned validation using standardized framework
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
   * Adds mapper function validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertMapperNull(
      ThrowableAssert.ThrowingCallable executable,
      String mapperName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () -> FunctionAssertions.assertMapperNull(executable, mapperName, contextClass, operation));
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
   * Adds flatMapper function validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertFlatMapperNull(
      ThrowableAssert.ThrowingCallable executable,
      String flatMapperName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () ->
            FunctionAssertions.assertFlatMapperNull(
                executable, flatMapperName, contextClass, operation));
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
   * Adds applicative validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertApplicativeNull(
      ThrowableAssert.ThrowingCallable executable,
      String applicativeName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () ->
            FunctionAssertions.assertApplicativeNull(
                executable, applicativeName, contextClass, operation));
    return this;
  }

  /**
   * Adds monoid validation to the test suite.
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
   * Adds monoid validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param monoidName The name of the monoid parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertMonoidNull(
      ThrowableAssert.ThrowingCallable executable,
      String monoidName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () -> FunctionAssertions.assertMonoidNull(executable, monoidName, contextClass, operation));
    return this;
  }

  /**
   * Adds custom function validation to the test suite.
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

  /**
   * Adds custom function validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param functionName The name of the function parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertFunctionNull(
      ThrowableAssert.ThrowingCallable executable,
      String functionName,
      Class<?> contextClass,
      Operation operation) {
    assertions.add(
        () ->
            FunctionAssertions.assertFunctionNull(
                executable, functionName, contextClass, operation));
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
   * Adds Kind null validation with class context to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertKindNull(
      ThrowableAssert.ThrowingCallable executable, Class<?> contextClass, Operation operation) {
    assertions.add(() -> KindAssertions.assertKindNull(executable, contextClass, operation));
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
   * Adds Kind null validation with class context and descriptor to the test suite.
   *
   * @param executable The code that should throw
   * @param contextClass The class providing context
   * @param operation The operation name
   * @param descriptor Optional descriptor for the parameter
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertKindNull(
      ThrowableAssert.ThrowingCallable executable,
      Class<?> contextClass,
      Operation operation,
      String descriptor) {
    assertions.add(
        () -> KindAssertions.assertKindNull(executable, contextClass, operation, descriptor));
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

  /**
   * Adds invalid Kind type validation to the test suite.
   *
   * @param executable The code that should throw
   * @param targetType The target type class
   * @param invalidKind The invalid Kind for error context
   * @return This builder for chaining
   */
  public ValidationTestBuilder assertInvalidKindType(
      ThrowableAssert.ThrowingCallable executable, Class<?> targetType, Kind<?, ?> invalidKind) {
    assertions.add(() -> KindAssertions.assertInvalidKindType(executable, targetType, invalidKind));
    return this;
  }

  // =============================================================================
  // Complete Type Class Validation Methods
  // =============================================================================

  /**
   * Adds all Functor operation validations to the test suite.
   *
   * @param functor The Functor instance to test
   * @param contextClass The Functor implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param <F> The Functor witness type
   * @param <A> The input type
   * @param <B> The output type
   * @return This builder for chaining
   */
  public <F, A, B> ValidationTestBuilder assertAllFunctorOperations(
      Functor<F> functor,
      Class<?> contextClass,
      Kind<F, A> validKind,
      java.util.function.Function<A, B> validMapper) {

    assertMapperNull(() -> functor.map(null, validKind), "f", contextClass, Operation.MAP);
    assertKindNull(() -> functor.map(validMapper, null), contextClass, Operation.MAP);
    return this;
  }

  /**
   * Adds all Applicative operation validations to the test suite.
   *
   * @param applicative The Applicative instance to test
   * @param contextClass The Applicative implementation class
   * @param validKind A valid Kind for testing
   * @param validKind2 A second valid Kind for map2 testing
   * @param validMapper A valid mapping function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param validCombiningFunction A valid combining function for map2 testing
   * @param <F> The Applicative witness type
   * @param <A> The input type
   * @param <B> The output type
   * @return This builder for chaining
   */
  public <F, A, B> ValidationTestBuilder assertAllApplicativeOperations(
      Applicative<F> applicative,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Kind<F, A> validKind2,
      Function<A, B> validMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      BiFunction<A, A, B> validCombiningFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(applicative, contextClass, validKind, validMapper);

    // Ap operations
    assertKindNull(() -> applicative.ap(null, validKind), contextClass, AP, "function");
    assertKindNull(() -> applicative.ap(validFunctionKind, null), contextClass, AP, "argument");

    // Map2 operations
    assertKindNull(
        () -> applicative.map2(null, validKind2, validCombiningFunction),
        contextClass,
        MAP_2,
        "first");
    assertKindNull(
        () -> applicative.map2(validKind, null, validCombiningFunction),
        contextClass,
        MAP_2,
        "second");
    assertFunctionNull(
        () -> applicative.map2(validKind, validKind2, (BiFunction<A, A, B>) null),
        "combining function",
        contextClass,
        MAP_2);

    return this;
  }

  /**
   * Adds all Monad operation validations to the test suite.
   *
   * @param monad The Monad instance to test
   * @param contextClass The Monad implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param <F> The Monad witness type
   * @param <A> The input type
   * @param <B> The output type
   * @return This builder for chaining
   */
  public <F, A, B> ValidationTestBuilder assertAllMonadOperations(
      Monad<F> monad,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind) {

    // Applicative operations (inherited) - using BiFunction for map2
    BiFunction<A, A, B> validCombiningFunction = (a1, a2) -> validMapper.apply(a1);
    assertAllApplicativeOperations(
        monad,
        contextClass,
        validKind,
        validKind,
        validMapper,
        validFunctionKind,
        validCombiningFunction);

    // FlatMap operations
    assertFlatMapperNull(() -> monad.flatMap(null, validKind), "f", contextClass, FLAT_MAP);
    assertKindNull(() -> monad.flatMap(validFlatMapper, null), contextClass, FLAT_MAP);

    return this;
  }

  /**
   * Adds all MonadError operation validations to the test suite.
   *
   * @param monadError The MonadError instance to test
   * @param contextClass The MonadError implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validFlatMapper A valid flatMap function
   * @param validFunctionKind A valid function Kind for ap testing
   * @param validHandler A valid error handler function
   * @param validFallback A valid fallback Kind
   * @param <F> The MonadError witness type
   * @param <E> The error type
   * @param <A> The input type
   * @param <B> The output type
   * @return This builder for chaining
   */
  public <F, E, A, B> ValidationTestBuilder assertAllMonadErrorOperations(
      MonadError<F, E> monadError,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Function<A, Kind<F, B>> validFlatMapper,
      Kind<F, Function<A, B>> validFunctionKind,
      Function<E, Kind<F, A>> validHandler,
      Kind<F, A> validFallback) {

    // Monad operations (inherited)
    assertAllMonadOperations(
        monadError, contextClass, validKind, validMapper, validFlatMapper, validFunctionKind);

    // MonadError operations
    assertKindNull(
        () -> monadError.handleErrorWith(null, validHandler),
        contextClass,
        HANDLE_ERROR_WITH,
        "source");
    assertFunctionNull(
        () -> monadError.handleErrorWith(validKind, null),
        "handler",
        contextClass,
        HANDLE_ERROR_WITH);
    assertKindNull(
        () -> monadError.recoverWith(null, validFallback), contextClass, RECOVER_WITH, "source");
    assertKindNull(
        () -> monadError.recoverWith(validKind, null), contextClass, RECOVER_WITH, "fallback");

    return this;
  }

  /**
   * Adds all Foldable operation validations to the test suite.
   *
   * @param foldable The Foldable instance to test
   * @param contextClass The Foldable implementation class
   * @param validKind A valid Kind for testing
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Foldable witness type
   * @param <A> The element type
   * @param <M> The Monoid type
   * @return This builder for chaining
   */
  public <F, A, M> ValidationTestBuilder assertAllFoldableOperations(
      Foldable<F> foldable,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    assertMonoidNull(
        () -> foldable.foldMap(null, validFoldMapFunction, validKind),
        "monoid",
        contextClass,
        FOLD_MAP);
    assertMapperNull(
        () -> foldable.foldMap(validMonoid, null, validKind), "f", contextClass, FOLD_MAP);
    assertKindNull(
        () -> foldable.foldMap(validMonoid, validFoldMapFunction, null), contextClass, FOLD_MAP);

    return this;
  }

  /**
   * Adds all Traverse operation validations to the test suite.
   *
   * @param traverse The Traverse instance to test
   * @param contextClass The Traverse implementation class
   * @param validKind A valid Kind for testing
   * @param validMapper A valid mapping function
   * @param validApplicative A valid Applicative instance
   * @param validTraverseFunction A valid traverse function
   * @param validMonoid A valid Monoid instance
   * @param validFoldMapFunction A valid foldMap function
   * @param <F> The Traverse witness type
   * @param <G> The Applicative witness type
   * @param <A> The source element type
   * @param <B> The target element type
   * @param <M> The Monoid type
   * @return This builder for chaining
   */
  public <F, G, A, B, M> ValidationTestBuilder assertAllTraverseOperations(
      Traverse<F> traverse,
      Class<?> contextClass,
      Kind<F, A> validKind,
      Function<A, B> validMapper,
      Applicative<G> validApplicative,
      Function<A, Kind<G, B>> validTraverseFunction,
      Monoid<M> validMonoid,
      Function<A, M> validFoldMapFunction) {

    // Functor operations (inherited)
    assertAllFunctorOperations(traverse, contextClass, validKind, validMapper);

    // Foldable operations (inherited)
    assertAllFoldableOperations(
        traverse, contextClass, validKind, validMonoid, validFoldMapFunction);

    // Traverse operations
    assertApplicativeNull(
        () -> traverse.traverse(null, validTraverseFunction, validKind),
        "applicative",
        contextClass,
        TRAVERSE);
    assertMapperNull(
        () -> traverse.traverse(validApplicative, null, validKind), "f", contextClass, TRAVERSE);
    assertKindNull(
        () -> traverse.traverse(validApplicative, validTraverseFunction, null),
        contextClass,
        TRAVERSE);

    return this;
  }

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
