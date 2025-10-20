// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static java.util.Objects.isNull;

import java.util.Objects;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Handles function parameter validations in monad operations.
 *
 * <p>This validator ensures consistent error messaging for function parameters across all monad
 * operations, preventing confusion about which operation failed and why.
 */
public enum FunctionValidator {
  FUNCTION_VALIDATOR;

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
   * Validation.functionValidator().requireMapper(f, StateTMonad.class, "map");
   * // Error: "function f for StateTMonad.map cannot be null"
   * </pre>
   */
  public <T> T requireMapper(
      T function, String functionName, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
    Objects.requireNonNull(functionName, "functionName cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireMapper(function, functionName, fullOperation);
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
  public <T> T requireMapper(T function, String functionName, String operation) {
    var context = FunctionContext.mapper(functionName, operation);
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
   * Validation.functionValidator().requireFlatMapper(f, StateTMonad.class, FLAT_MAP);
   * // Error: "function f for StateTMonad.flatMap cannot be null"
   * </pre>
   */
  public <T> T requireFlatMapper(
      T function, String functionName, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
    Objects.requireNonNull(functionName, "functionName cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireFlatMapper(function, functionName, fullOperation);
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
  public <T> T requireFlatMapper(T function, String functionName, String operation) {
    var context = FunctionContext.flatMapper(functionName, operation);
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
  public <T> T requireApplicative(
      T applicative, String applicativeName, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
    Objects.requireNonNull(applicativeName, "applicativeName cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireApplicative(applicative, applicativeName, fullOperation);
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
  public <T> T requireApplicative(T applicative, String applicativeName, String operation) {
    var context = FunctionContext.applicative(applicativeName, operation);
    return Objects.requireNonNull(applicative, context.nullParameterMessage());
  }

  /**
   * Validates monoid instance with class-based operation context.
   *
   * @param monoid The monoid instance to validate
   * @param monoidName The name of the monoid parameter
   * @param contextClass The class providing context
   * @param operation The operation name (e.g., "foldMap")
   * @param <T> The monoid type
   * @return The validated monoid
   * @throws NullPointerException with context-specific message if monoid is null
   */
  public <T> T requireMonoid(
      T monoid, String monoidName, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    return requireMonoid(monoid, monoidName, fullOperation);
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
  public <T> T requireMonoid(T monoid, String monoidName, String operation) {
    var context = new FunctionContext(monoidName, operation);
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
   * Validation.functionValidator().requireFunction(fn, "runStateTFn", StateT.class, "construction");
   * // Error: "runStateTFn for StateT construction cannot be null"
   * </pre>
   */
  public <T> T requireFunction(
      T function, String functionName, Class<?> contextClass, Operation operation) {

    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
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
  public <T> T requireFunction(T function, String functionName, String operation) {
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
   * Validation.functionValidator().requireNonNullResult(kindB, StateTMonad.class, "flatMap");
   * // Error: "Function in StateTMonad.flatMap returned null, which is not allowed"
   * </pre>
   */
  public <T> T requireNonNullResult(
      T result, String functionName, Class<?> contextClass, Operation operation) {
    return requireNonNullResult(result, functionName, contextClass, operation, null);
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
  public <T> T requireNonNullResult(
      T result,
      String functionName,
      Class<?> contextClass,
      Operation operation,
      @Nullable Class<?> targetType) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
    Objects.requireNonNull(functionName, "functionName cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    if (isNull(result)) {
      String msg =
          (targetType == null)
              ? "Function %s in %s returned null, which is not allowed"
                  .formatted(functionName, fullOperation)
              : "Function %s in %s returned null when %s expected, which is not allowed"
                  .formatted(functionName, fullOperation, targetType.getSimpleName());

      throw new KindUnwrapException(msg);
    }
    return result;
  }

  // Add validation for handlers (used in error handling)
  public <T> T requireHandler(T handler, Class<?> contextClass, Operation operation) {
    Objects.requireNonNull(contextClass, "contextClass cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    String fullOperation = contextClass.getSimpleName() + "." + operation;
    var context = new FunctionContext("handler", fullOperation);
    return Objects.requireNonNull(handler, context.nullParameterMessage());
  }

  public record FunctionContext(String functionName, String operation) {

    public FunctionContext {
      Objects.requireNonNull(functionName, "functionName cannot be null");
      Objects.requireNonNull(operation, "operation cannot be null");
    }

    public static FunctionContext mapper(String functionName, String operation) {
      return new FunctionContext(functionName, operation);
    }

    public static FunctionContext flatMapper(String functionName, String operation) {
      return new FunctionContext(functionName, operation);
    }

    public static FunctionContext applicative(String applicativeName, String operation) {
      return new FunctionContext(applicativeName, operation);
    }

    public String nullParameterMessage() {
      return String.format("Function %s for %s cannot be null", functionName, operation);
    }
  }
}
