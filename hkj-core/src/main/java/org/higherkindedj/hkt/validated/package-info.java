/**
 * Provides components for the {@code Validated} type and its simulation as a Higher-Kinded Type.
 * {@code Validated} is a data type similar to {@code Either} but is typically used in contexts
 * where errors can be accumulated (when used with an Applicative that supports it).
 *
 * <p>Includes the {@link org.higherkindedj.hkt.validated.Validated} interface, its implementations
 * {@link org.higherkindedj.hkt.validated.Valid} and {@link
 * org.higherkindedj.hkt.validated.Invalid}, the HKT wrapper {@link
 * org.higherkindedj.hkt.validated.ValidatedKind}, the {@link
 * org.higherkindedj.hkt.validated.ValidatedMonad} instance, and helper utilities.
 *
 * <h2>Open-arity accumulating assembly</h2>
 *
 * <p>{@link org.higherkindedj.hkt.validated.Validated#accumulate()} and {@link
 * org.higherkindedj.hkt.validated.Validated#fields()} open a staged builder that assembles a value
 * from N independently validated fields, collecting <b>all</b> errors in field-declaration order,
 * with no {@code Semigroup} argument, no arity wall, and no {@code Kind} ceremony:
 *
 * <pre>{@code
 * Validated<NonEmptyList<FieldError>, User> user =
 *     Validated.fields()
 *         .field("name", Name.parse(dto.name()))
 *         .field("email", Email.parse(dto.email()))
 *         .field("age", Age.parse(dto.age()))
 *         .apply(User::new);
 * }</pre>
 *
 * <p>The {@code fields()} flavour fixes the error channel to {@code NonEmptyList<}{@link
 * org.higherkindedj.hkt.validated.FieldError}{@code >}, and {@code field(label, value)} prepends
 * the label onto each error's path so nested assemblies compose ({@code "address.zip"}). The
 * {@code @GenerateAccumulators} annotation below triggers generation of the staged builder classes
 * for all three carriers ({@code Validated} here, {@code ValidationPath} in {@code
 * org.higherkindedj.hkt.effect}, and {@code EitherOrBoth} in {@code
 * org.higherkindedj.hkt.eitherorboth}); only the arity-0 entry stages and {@code FieldError} are
 * hand-written. Records with more than sixteen fields nest a sub-record per slot.
 */
@NullMarked
@GenerateAccumulators(minArity = 1, maxArity = ArityCeilings.ASSEMBLY)
package org.higherkindedj.hkt.validated;

import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.annotations.GenerateAccumulators;
import org.jspecify.annotations.NullMarked;
