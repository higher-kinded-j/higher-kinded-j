// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Objects;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.util.context.FunctionContext;
import org.higherkindedj.hkt.util.context.KindContext;

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
   * Validates that a function result is not null (for flatMap scenarios).
   *
   * @param result The result returned by a function
   * @param operation The operation that produced this result
   * @param targetType The expected type for error messaging
   * @param <T> The result type
   * @return The validated result
   * @throws KindUnwrapException if result is null
   */
  public static <T> T requireNonNullResult(T result, String operation, Class<?> targetType) {
    if (result == null) {
      var context = KindContext.narrow(targetType);
      throw new KindUnwrapException(
          context.customMessage("Function in %s returned null, which is not allowed", operation));
    }
    return result;
  }
}
