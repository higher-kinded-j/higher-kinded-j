// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a custom Path wrapper for an effect type.
 *
 * <p>Apply this annotation to a type (typically a sealed interface) to generate a corresponding
 * Path class with fluent composition methods.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @PathSource(
 *     witness = ApiResultKind.Witness.class,
 *     errorType = ApiError.class
 * )
 * public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {
 *     <B> ApiResult<B> map(Function<? super A, ? extends B> f);
 *     <B> ApiResult<B> flatMap(Function<? super A, ? extends ApiResult<B>> f);
 * }
 * }</pre>
 *
 * <p>This generates {@code ApiResultPath<A>} implementing {@code Recoverable<ApiError, A>}.
 *
 * <h2>Generated Path Class</h2>
 *
 * <p>The generated class includes:
 *
 * <ul>
 *   <li>Factory methods ({@code of}, {@code pure})
 *   <li>All capability interface methods (map, via, etc.)
 *   <li>Conversion methods to other path types
 *   <li>Error recovery methods (if errorType is specified)
 * </ul>
 *
 * @see PathConfig
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathSource {

  /**
   * The HKT witness class for this type.
   *
   * <p>Must be a class with a {@code Witness} inner type marker, following the higher-kinded-j
   * convention.
   *
   * @return the witness type class
   */
  Class<?> witness();

  /**
   * The error type for this effect, if it supports error handling.
   *
   * <p>When specified, the generated Path implements {@code Recoverable<E, A>} and includes error
   * recovery methods.
   *
   * <p>Use {@link Void} to indicate no error type (the default).
   *
   * @return the error type class, or Void if no error handling
   */
  Class<?> errorType() default Void.class;

  /**
   * The capability level for the generated Path.
   *
   * <p>This determines which interfaces the generated Path implements:
   *
   * <ul>
   *   <li>{@link Capability#COMPOSABLE} - Only map and peek
   *   <li>{@link Capability#COMBINABLE} - Adds zipWith
   *   <li>{@link Capability#CHAINABLE} - Adds via and then
   *   <li>{@link Capability#RECOVERABLE} - Adds error recovery (requires errorType)
   *   <li>{@link Capability#EFFECTFUL} - Full effect operations
   *   <li>{@link Capability#ACCUMULATING} - Error accumulation (requires Semigroup)
   * </ul>
   *
   * @return the capability level
   */
  Capability capability() default Capability.CHAINABLE;

  /**
   * The suffix to append to the type name for the generated Path class.
   *
   * <p>Defaults to "Path". For example, {@code ApiResult} generates {@code ApiResultPath}.
   *
   * @return the class name suffix
   */
  String suffix() default "Path";

  /**
   * The package where the generated class should be placed.
   *
   * <p>If empty (the default), the generated class is placed in the same package as the annotated
   * type.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";

  /** Capability levels for generated Path types. */
  enum Capability {
    /** Functor-level: map, peek */
    COMPOSABLE,

    /** Applicative-level: zipWith, zipWith3 */
    COMBINABLE,

    /** Monad-level: via, then, flatMap */
    CHAINABLE,

    /** MonadError-level: recover, recoverWith, mapError */
    RECOVERABLE,

    /** IO-level: unsafeRun, delay, async */
    EFFECTFUL,

    /** Validated-level: combine, accumulateErrors */
    ACCUMULATING
  }
}
