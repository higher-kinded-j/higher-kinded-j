// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers generation of the staged accumulating-assembly builder classes.
 *
 * <p>Apply this annotation to a {@code package-info.java} in the package where the generated
 * classes should be placed. The annotation processor generates six stage families, one class per
 * arity from {@code minArity} to {@code maxArity}:
 *
 * <ul>
 *   <li>{@code ValidatedAccumN} / {@code ValidatedFieldsN} for {@code Validated} assembly
 *   <li>{@code ValidationPathAccumN} / {@code ValidationPathFieldsN} for {@code ValidationPath}
 *       assembly
 *   <li>{@code EitherOrBothAccumN} / {@code EitherOrBothFieldsN} for tolerant {@code EitherOrBoth}
 *       assembly
 * </ul>
 *
 * <p>The {@code Accum} flavour is generic in the error payload {@code X} (carried as {@code
 * NonEmptyList<X>}) and adds fields with {@code and(value)}. The {@code Fields} flavour fixes the
 * error channel to {@code NonEmptyList<FieldError>} and additionally offers {@code field(label,
 * value)}, which prepends the label onto each error's path so that nested assemblies compose (e.g.
 * {@code "address.zip"}). Errors always accumulate in field-declaration order.
 *
 * <p>Example usage in {@code package-info.java}:
 *
 * <pre>{@code
 * @GenerateAccumulators(minArity = 1, maxArity = 12)
 * package org.higherkindedj.hkt.assembly;
 *
 * import org.higherkindedj.optics.annotations.GenerateAccumulators;
 * }</pre>
 *
 * @see org.higherkindedj.optics.annotations.Generated
 * @see GenerateForComprehensions
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateAccumulators {

  /**
   * Maximum arity for the generated stage families. The ceiling is 12, matching the shipped {@code
   * FunctionN} and {@code TupleN} arities.
   *
   * @return the maximum arity (default 12)
   */
  int maxArity() default 12;

  /**
   * Minimum arity for the generated stage families. Arities below this value are assumed to be
   * hand-written; the arity-0 entry stages are always hand-written.
   *
   * @return the minimum arity (default 1)
   */
  int minArity() default 1;
}
