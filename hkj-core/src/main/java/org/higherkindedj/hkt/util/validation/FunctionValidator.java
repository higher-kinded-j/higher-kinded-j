// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.context.FunctionContext;

/**
 * Handles function parameter validations in monad operations.
 *
 * <p>This validator ensures consistent error messaging for function parameters across all monad
 * operations, preventing confusion about which operation failed and why.
 */
public final class FunctionValidator {

  private FunctionValidator() {
    throw new AssertionError("FunctionValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates mapping function with class-based operation context.
   *
   * @param function The mapping function to validate
   * @param contextClass The class providing context (e.g., StateTMonad.class, OptionalT.class)
   * @param operation The operation name (e.g., "map", "traverse")
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   * @example
   *     <pre>
   * FunctionValidator.requireMapper(f, StateTMonad.class, "map");
   * // Error: "function f for StateTMonad.map cannot be null"
   * </pre>
   */
  public static <T> T requireMapper(T function, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireMapper(function, fullOperation);
  }

  /**
   * Validates mapping function with operation context.
   *
   * @param function The mapping function to validate
   * @param operation The operation name (e.g., "map", "traverse")
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   */
  public static <T> T requireMapper(T function, String operation) {
    var context = FunctionContext.mapper(operation);
    return Objects.requireNonNull(function, context.nullParameterMessage());
  }

  /**
   * Validates flat mapping function with class-based operation context.
   *
   * @param function The flat mapping function to validate
   * @param contextClass The class providing context (e.g., StateTMonad.class)
   * @param operation The operation name (e.g., "FLAT_MAP, OF)
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   * @example
   *     <pre>
   * FunctionValidator.requireFlatMapper(f, StateTMonad.class, FLAT_MAP);
   * // Error: "function f for StateTMonad.flatMap cannot be null"
   * </pre>
   */
  public static <T> T requireFlatMapper(T function, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation.toString();
    return requireFlatMapper(function, fullOperation);
  }

  /**
   * Validates flat mapping function with operation context.
   *
   * @param function The flat mapping function to validate
   * @param operation The operation name (e.g., "flatMap", "bind")
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   */
  public static <T> T requireFlatMapper(T function, String operation) {
    var context = FunctionContext.flatMapper(operation);
    return Objects.requireNonNull(function, context.nullParameterMessage());
  }

  /**
   * Validates applicative instance with class-based operation context.
   *
   * @param applicative The applicative instance to validate
   * @param contextClass The class providing context
   * @param operation The operation name (e.g., "traverse", "sequence")
   * @param <T> The applicative type
   * @return The validated applicative
   * @throws NullPointerException with context-specific message if applicative is null
   */
  public static <T> T requireApplicative(
      T applicative, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireApplicative(applicative, fullOperation);
  }

  /**
   * Validates applicative instance with operation context.
   *
   * @param applicative The applicative instance to validate
   * @param operation The operation name (e.g., "traverse", "sequence")
   * @param <T> The applicative type
   * @return The validated applicative
   * @throws NullPointerException with context-specific message if applicative is null
   */
  public static <T> T requireApplicative(T applicative, String operation) {
    var context = FunctionContext.applicative(operation);
    return Objects.requireNonNull(applicative, context.nullParameterMessage());
  }

  /**
   * Validates monoid instance with class-based operation context.
   *
   * @param monoid The monoid instance to validate
   * @param contextClass The class providing context
   * @param operation The operation name (e.g., "foldMap")
   * @param <T> The monoid type
   * @return The validated monoid
   * @throws NullPointerException with context-specific message if monoid is null
   */
  public static <T> T requireMonoid(T monoid, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireMonoid(monoid, fullOperation);
  }

  /**
   * Validates monoid instance with operation context.
   *
   * @param monoid The monoid instance to validate
   * @param operation The operation name (e.g., "foldMap")
   * @param <T> The monoid type
   * @return The validated monoid
   * @throws NullPointerException with context-specific message if monoid is null
   */
  public static <T> T requireMonoid(T monoid, String operation) {
    var context = new FunctionContext("monoid", operation);
    return Objects.requireNonNull(monoid, context.nullParameterMessage());
  }

  /**
   * Generic function validation with class-based context.
   *
   * @param function The function to validate
   * @param functionName The name of the function parameter
   * @param contextClass The class providing context
   * @param operation The operation name
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   * @example
   *     <pre>
   * FunctionValidator.requireFunction(fn, "runStateTFn", StateT.class, "construction");
   * // Error: "runStateTFn for StateT construction cannot be null"
   * </pre>
   */
  public static <T> T requireFunction(
      T function, String functionName, Class<?> contextClass, Operation operation) {

    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + " " + operation;
    return requireFunction(function, functionName, fullOperation);
  }

  /**
   * Generic function validation with custom name and operation context.
   *
   * @param function The function to validate
   * @param functionName The name of the function parameter
   * @param operation The operation name
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if function is null
   */
  public static <T> T requireFunction(T function, String functionName, String operation) {
    var context = new FunctionContext(functionName, operation);
    return Objects.requireNonNull(function, context.nullParameterMessage());
  }

  /**
   * Validates that a function result is not null (for flatMap scenarios) with class-based context.
   *
   * @param result The result returned by a function
   * @param contextClass The class providing context
   * @param operation The operation that produced this result
   * @param <T> The result type
   * @return The validated result
   * @throws KindUnwrapException if result is null
   * @example
   *     <pre>
   * FunctionValidator.requireNonNullResult(kindB, StateTMonad.class, "flatMap");
   * // Error: "Function in StateTMonad.flatMap returned null, which is not allowed"
   * </pre>
   */
  public static <T> T requireNonNullResult(T result, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireNonNullResult(result, fullOperation);
  }

  /**
   * Validates that a function result is not null (for flatMap scenarios).
   *
   * @param result The result returned by a function
   * @param operation The operation that produced this result
   * @param targetType The expected type for error messaging
   * @param <T> The result type
   * @return The validated result
   * @throws KindUnwrapException if result is null
   */
  public static <T> T requireNonNullResult(T result, Operation operation, Class<?> targetType) {
    if (result == null) {
      throw new KindUnwrapException(
          "Function in %s returned null, which is not allowed".formatted(operation));
    }
    return result;
  }

  // Add missing overload
  public static <T> T requireNonNullResult(T result, String operation) {
    if (result == null) {
      throw new KindUnwrapException(
          "Function in %s returned null, which is not allowed".formatted(operation));
    }
    return result;
  }

  // Add validation for predicates (used in MonadZero filtering)
  public static <T> T requirePredicate(T predicate, Operation operation) {
    var context = new FunctionContext("predicate", operation.toString());
    return Objects.requireNonNull(predicate, context.nullParameterMessage());
  }

  // Add validation for handlers (used in error handling)
  public static <T> T requireHandler(T handler, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    var context = new FunctionContext("handler", fullOperation);
    return Objects.requireNonNull(handler, context.nullParameterMessage());
  }
}
