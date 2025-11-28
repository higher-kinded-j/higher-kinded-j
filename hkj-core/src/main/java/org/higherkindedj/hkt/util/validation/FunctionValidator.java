// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static java.util.Objects.isNull;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.HANDLE_ERROR_WITH;
import static org.higherkindedj.hkt.util.validation.Operation.TRAVERSE;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
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
   *     <p>Example usage:
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
   *     <p>Example usage:
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
   * @throws NullPointerException with context-specific message if function is null Example usage:
   *     <pre>
   * Validation.functionValidator().requireFunction(fn, "runStateTFn", StateT.class, "construction");
   * // Error: "runStateTFn for StateT construction cannot be null"
   *  </pre>
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
   *     <p>Example usage:
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

  // ==================== Bulk Validation Helpers ====================
  // These methods reduce boilerplate by combining multiple validations into single calls.

  /**
   * Validates all parameters for a flatMap operation in a single call.
   *
   * <p>This combines function and Kind validation, reducing boilerplate in Monad implementations.
   *
   * @param f the flatMap function (must be non-null)
   * @param ma the input Kind (must be non-null)
   * @param contextClass the class performing the operation (for error messages)
   * @param <F> the functor type constructor
   * @param <A> input type
   * @param <B> output type
   * @throws NullPointerException if f or ma is null
   */
  public <F, A, B> void validateFlatMap(
      Function<? super A, ? extends Kind<F, B>> f, Kind<F, A> ma, Class<?> contextClass) {
    requireFlatMapper(f, "f", contextClass, FLAT_MAP);
    Validation.kind().requireNonNull(ma, contextClass, FLAT_MAP);
  }

  /**
   * Validates all parameters for a traverse operation in a single call.
   *
   * <p>This combines applicative, function, and Kind validation, reducing boilerplate in Traverse
   * implementations.
   *
   * @param applicative the target Applicative (must be non-null)
   * @param f the transformation function (must be non-null)
   * @param ta the traversable Kind (must be non-null)
   * @param contextClass the class performing the operation (for error messages)
   * @param <G> the applicative type constructor
   * @param <A> input element type
   * @param <B> output element type
   * @throws NullPointerException if any parameter is null
   */
  public <G, A, B> void validateTraverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<?, A> ta,
      Class<?> contextClass) {
    requireApplicative(applicative, "applicative", contextClass, TRAVERSE);
    requireMapper(f, "f", contextClass, TRAVERSE);
    Validation.kind().requireNonNull(ta, contextClass, TRAVERSE);
  }

  /**
   * Validates all parameters for a foldMap operation in a single call.
   *
   * <p>This combines monoid, function, and Kind validation, reducing boilerplate in
   * Foldable/Traverse implementations.
   *
   * @param monoid the combining Monoid (must be non-null)
   * @param f the transformation function (must be non-null)
   * @param fa the foldable Kind (must be non-null)
   * @param contextClass the class performing the operation (for error messages)
   * @param <M> the monoid type
   * @param <A> input element type
   * @throws NullPointerException if any parameter is null
   */
  public <M, A> void validateFoldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<?, A> fa, Class<?> contextClass) {
    requireMonoid(monoid, "monoid", contextClass, FOLD_MAP);
    requireMapper(f, "f", contextClass, FOLD_MAP);
    Validation.kind().requireNonNull(fa, contextClass, FOLD_MAP);
  }

  /**
   * Validates all parameters for a handleErrorWith operation in a single call.
   *
   * <p>This combines Kind and handler function validation, reducing boilerplate in MonadError
   * implementations.
   *
   * @param ma the source Kind (must be non-null)
   * @param handler the error recovery function (must be non-null)
   * @param contextClass the class performing the operation (for error messages)
   * @param <F> the functor type constructor
   * @param <A> the value type
   * @param <E> the error type
   * @throws NullPointerException if ma or handler is null
   */
  public <F, A, E> void validateHandleErrorWith(
      Kind<F, A> ma, Function<? super E, ? extends Kind<F, A>> handler, Class<?> contextClass) {
    Validation.kind().requireNonNull(ma, contextClass, HANDLE_ERROR_WITH, "source");
    requireFunction(handler, "handler", contextClass, HANDLE_ERROR_WITH);
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
