// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers generation of extended-arity for-comprehension support classes.
 *
 * <p>Apply this annotation to a {@code package-info.java} in the package where the generated
 * classes should be placed. The annotation processor will generate:
 *
 * <ul>
 *   <li>{@code TupleN} records from {@code minArity} to {@code maxArity}
 *   <li>{@code MonadicStepsN} classes for the {@code For} comprehension builder
 *   <li>{@code FilterableStepsN} classes with guard ({@code when}) support
 *   <li>{@code *PathStepsN} classes for each ForPath effect type
 * </ul>
 *
 * <p>Example usage in {@code package-info.java}:
 *
 * <pre>{@code
 * @GenerateForComprehensions(minArity = 2, maxArity = 12)
 * package org.higherkindedj.hkt.expression;
 *
 * import org.higherkindedj.optics.annotations.GenerateForComprehensions;
 * }</pre>
 *
 * @see org.higherkindedj.optics.annotations.Generated
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateForComprehensions {

  /**
   * Maximum arity for generated comprehension support. The processor generates Tuple, step, and
   * path step classes up to this arity.
   *
   * @return the maximum arity (default 8)
   */
  int maxArity() default 8;

  /**
   * Minimum arity for generated comprehension support. Arities below this value are assumed to be
   * hand-written. Set to 2 to replace all hand-written arities with generated code.
   *
   * @return the minimum arity (default 6)
   */
  int minArity() default 6;
}
