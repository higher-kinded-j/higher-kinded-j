// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static java.util.Objects.isNull;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.HANDLE_ERROR_WITH;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.util.validation.Operation.TRAVERSE;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.exception.KindUnwrapException;

/**
 * Handles parameter validations in monad operations.
 *
 * <p>Despite the name, this validator is used for any reference parameter that participates in a
 * monad/functor/applicative operation — functions, monoids, error values, optics, applicatives,
 * etc. The error message format is parameter-name-led so it reads correctly for any of these:
 *
 * <ul>
 *   <li>{@code require(f, "f", FLAT_MAP)} → {@code "f for flatMap cannot be null"}
 *   <li>{@code require(monoid, "monoid", OF)} → {@code "monoid for of cannot be null"}
 *   <li>{@code require(optic, "optic", LIFT_F)} → {@code "optic for liftF cannot be null"}
 * </ul>
 *
 * <p>The class name is retained for source compatibility; if a more neutral name is wanted, an
 * alias may be introduced in a future release.
 */
public enum FunctionValidator {
  FUNCTION_VALIDATOR;

  /**
   * Generic function validation with class-based context.
   *
   * @param function The function to validate
   * @param functionName The name of the function parameter
   * @param operation The operation name
   * @param <T> The function type
   * @return The validated function
   * @throws NullPointerException with context-specific message if the parameter is null
   *     <p>Example usage:
   *     <pre>
   * Validation.function().require(fn, "runStateTFn", CONSTRUCTION);
   * // Error: "runStateTFn for construction cannot be null"
   *     </pre>
   */
  public <T> T require(T parameter, String parameterName, Operation operation) {
    Objects.requireNonNull(parameterName, "parameterName cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");

    if (parameter == null) {
      throw new NullPointerException(
          new FunctionContext(parameterName, operation.toString()).nullParameterMessage());
    }
    return parameter;
  }

  /**
   * Validates that a function result is not null (for flatMap scenarios).
   *
   * @param result The result returned by a function
   * @param operation The operation that produced this result
   * @param <T> The result type
   * @return The validated result
   * @throws KindUnwrapException if result is null
   */
  public <T> T requireNonNullResult(T result, String functionName, Operation operation) {
    Objects.requireNonNull(operation, "operation cannot be null");
    Objects.requireNonNull(functionName, "functionName cannot be null");

    if (isNull(result)) {
      String msg =
          "Function %s in %s returned null, which is not allowed"
              .formatted(functionName, operation);

      throw new KindUnwrapException(msg);
    }
    return result;
  }

  // Add validation for handlers (used in error handling)
  public <T> T requireHandler(T handler, Operation operation) {
    return require(handler, "handler", operation);
  }

  // ==================== Bulk Validation Helpers ====================
  // These methods reduce boilerplate by combining multiple validations into single calls.

  /**
   * Validates all parameters for a map operation in a single call.
   *
   * <p>This combines function and Kind validation, reducing boilerplate in Functor implementations.
   *
   * @param f the mapping function (must be non-null)
   * @param fa the input Kind (must be non-null)
   * @param <F> the functor type constructor
   * @param <A> input type
   * @param <B> output type
   * @throws NullPointerException if f or fa is null
   */
  public <F extends WitnessArity<?>, A, B> void validateMap(
      Function<? super A, ? extends B> f, Kind<F, A> fa) {
    require(f, "f", MAP);
    Validation.kind().requireNonNull(fa, MAP);
  }

  /**
   * Validates all parameters for a flatMap operation in a single call.
   *
   * <p>This combines function and Kind validation, reducing boilerplate in Monad implementations.
   *
   * @param f the flatMap function (must be non-null)
   * @param ma the input Kind (must be non-null)
   * @param <F> the functor type constructor
   * @param <A> input type
   * @param <B> output type
   * @throws NullPointerException if f or ma is null
   */
  public <F extends WitnessArity<?>, A, B> void validateFlatMap(
      Function<? super A, ? extends Kind<F, B>> f, Kind<F, A> ma) {
    require(f, "f", FLAT_MAP);
    Validation.kind().requireNonNull(ma, FLAT_MAP);
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
   * @param <G> the applicative type constructor
   * @param <A> input element type
   * @param <B> output element type
   * @throws NullPointerException if any parameter is null
   */
  public <G extends WitnessArity<TypeArity.Unary>, A, B> void validateTraverse(
      Applicative<G> applicative,
      Function<? super A, ? extends Kind<G, ? extends B>> f,
      Kind<?, A> ta) {
    require(applicative, "applicative", TRAVERSE);
    require(f, "f", TRAVERSE);
    Validation.kind().requireNonNull(ta, TRAVERSE);
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
   * @param <M> the monoid type
   * @param <A> input element type
   * @throws NullPointerException if any parameter is null
   */
  public <M, A> void validateFoldMap(
      Monoid<M> monoid, Function<? super A, ? extends M> f, Kind<?, A> fa) {
    require(monoid, "monoid", FOLD_MAP);
    require(f, "f", FOLD_MAP);
    Validation.kind().requireNonNull(fa, FOLD_MAP);
  }

  /**
   * Validates all parameters for a handleErrorWith operation in a single call.
   *
   * <p>This combines Kind and handler function validation, reducing boilerplate in MonadError
   * implementations.
   *
   * @param ma the source Kind (must be non-null)
   * @param handler the error recovery function (must be non-null)
   * @param <F> the functor type constructor
   * @param <A> the value type
   * @param <E> the error type
   * @throws NullPointerException if ma or handler is null
   */
  public <F extends WitnessArity<?>, A, E> void validateHandleErrorWith(
      Kind<F, A> ma, Function<? super E, ? extends Kind<F, A>> handler) {
    Validation.kind().requireNonNull(ma, HANDLE_ERROR_WITH, "source");
    require(handler, "handler", HANDLE_ERROR_WITH);
  }

  /**
   * Context record for parameter validation. Despite the legacy field name {@code functionName},
   * this carries the name of any reference parameter (function, monoid, error, etc.) — the
   * generated message is parameter-name-led, e.g. {@code "f for flatMap cannot be null"}.
   */
  public record FunctionContext(String functionName, String operation) {

    public FunctionContext {
      Objects.requireNonNull(functionName, "functionName cannot be null");
      Objects.requireNonNull(operation, "operation cannot be null");
    }

    public String nullParameterMessage() {
      return String.format("%s for %s cannot be null", functionName, operation);
    }
  }
}
