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
 *     errorType = ApiError.class,
 *     capability = PathSource.Capability.RECOVERABLE
 * )
 * public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {
 *     <B> ApiResult<B> map(Function<? super A, ? extends B> f);
 *     <B> ApiResult<B> flatMap(Function<? super A, ? extends ApiResult<B>> f);
 * }
 * }</pre>
 *
 * <p>This generates {@code ApiResultPath<A>}, whose {@code of} and {@code pure} factories take a
 * {@code MonadError<ApiResultKind.Witness, ApiError>} as well as the {@code Monad}, and which has
 * {@code recover}, {@code recoverWith} and {@code mapError} beside the chaining methods.
 *
 * <h2>Generated Path Class</h2>
 *
 * <p>The generated class includes:
 *
 * <ul>
 *   <li>Factory methods ({@code of}, {@code pure}) and the terminals {@code run} and {@code
 *       runKind}
 *   <li>The methods of its {@link #capability()}, and those of each level below it
 *   <li>{@code equals}, {@code hashCode} and a {@code toString} naming the generated class
 * </ul>
 *
 * <p>The capability interfaces above {@code Combinable} are sealed to the library's own Path types,
 * so a generated Path implements {@code Composable<A>} for {@link Capability#COMPOSABLE} and {@code
 * Combinable<A>} for every other level, and declares the further methods itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface PathSource {

  /**
   * The HKT witness for this type: the marker class its {@code Kind} is indexed by, such as {@code
   * ApiResultKind.Witness.class} in the higher-kinded-j convention of a {@code Witness} nested in
   * the type's {@code Kind} interface. The generated Path wraps a {@code Kind} of it.
   *
   * @return the witness class
   */
  Class<?> witness();

  /**
   * The error type for this effect, if it supports error handling.
   *
   * <p>Recovery methods are generated only when this is given and {@link #capability()} is {@link
   * Capability#RECOVERABLE}: the generated {@code of} and {@code pure} then take a {@code
   * MonadError} over it, so it must be a reference type. With any other capability it is not used,
   * and the processor reports a note saying so.
   *
   * <p>Use {@link Void} to indicate no error type (the default).
   *
   * @return the error type class, or Void if no error handling
   */
  Class<?> errorType() default Void.class;

  /**
   * The capability level for the generated Path.
   *
   * <p>Each level generates the methods of the levels before it:
   *
   * <ul>
   *   <li>{@link Capability#COMPOSABLE} - {@code map} and {@code peek}
   *   <li>{@link Capability#COMBINABLE} - adds {@code zipWith}
   *   <li>{@link Capability#CHAINABLE} - adds {@code via}, {@code then} and {@code flatMap}
   *   <li>{@link Capability#RECOVERABLE} - adds {@code recover}, {@code recoverWith} and {@code
   *       mapError}, given an {@link #errorType()}
   * </ul>
   *
   * @return the capability level
   */
  Capability capability() default Capability.CHAINABLE;

  /**
   * The suffix to append to the type name for the generated Path class.
   *
   * <p>Defaults to "Path". For example, {@code ApiResult} generates {@code ApiResultPath}. The
   * resulting name must be one Java allows for a class, and must not be taken by a type compiled
   * beside it: an empty suffix is refused where the Path would land on the annotated type itself,
   * and accepted where a {@link #targetPackage()} or an enclosing type keeps the two apart.
   *
   * @return the class name suffix
   */
  String suffix() default "Path";

  /**
   * The package where the generated class should be placed.
   *
   * <p>If empty (the default), the generated class is placed in the same package as the annotated
   * type. Otherwise it must be a package name, such as {@code com.example.paths}.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";

  /** Capability levels for generated Path types. */
  enum Capability {
    /** Functor-level: {@code map}, {@code peek}. */
    COMPOSABLE,

    /** Applicative-level: adds {@code zipWith}. */
    COMBINABLE,

    /** Monad-level: adds {@code via}, {@code then}, {@code flatMap}. */
    CHAINABLE,

    /**
     * MonadError-level: adds {@code recover}, {@code recoverWith}, {@code mapError}, given an
     * {@link PathSource#errorType()}.
     */
    RECOVERABLE,

    /**
     * Generates exactly what {@link #CHAINABLE} does.
     *
     * @deprecated since 0.4.11, for removal in 0.5.0. It generates no {@code unsafeRun}, {@code
     *     delay} or {@code async}, since a Path over an arbitrary witness has no way to run the
     *     effect: use {@link #CHAINABLE}, which generates the same class. The {@code
     *     MigrateDeprecationsTo0_5_0} OpenRewrite recipe replaces it.
     */
    @Deprecated(since = "0.4.11", forRemoval = true)
    EFFECTFUL,

    /**
     * Generates exactly what {@link #RECOVERABLE} does.
     *
     * @deprecated since 0.4.11, for removal in 0.5.0. It generates no {@code combine} or {@code
     *     accumulateErrors}, and no error is accumulated: {@code zipWith} stops at the first, as
     *     {@link #RECOVERABLE}'s does. Use {@link #RECOVERABLE}, which generates the same class.
     *     The {@code MigrateDeprecationsTo0_5_0} OpenRewrite recipe replaces it.
     */
    @Deprecated(since = "0.4.11", forRemoval = true)
    ACCUMULATING
  }
}
