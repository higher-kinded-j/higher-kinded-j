// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interpreter class for compile-time validation that all operations in the referenced
 * effect algebra have corresponding handler methods.
 *
 * <p>The annotated class must implement {@code Natural<Witness, M>} for the referenced effect
 * algebra's witness type. The processor validates that every sealed permit in the algebra has a
 * matching handler method.
 *
 * <h2>Validation Rules</h2>
 *
 * <ul>
 *   <li>Each operation (sealed permit) must have a corresponding handler method
 *   <li>Handler methods must accept the operation record as a parameter
 *   <li>Handler methods must return {@code Kind<M, ?>} where {@code M} is the target monad
 *   <li>Extra handler methods that don't match any operation trigger a warning
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @Handles(ConsoleOp.class)
 * public class ConsoleInterpreter implements Natural<ConsoleOpKind.Witness, IOKind.Witness> {
 *     // Handler for each ConsoleOp operation...
 * }
 * }</pre>
 *
 * @see EffectAlgebra
 * @see "org.higherkindedj.hkt.Natural"
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Handles {

  /**
   * The effect algebra class that this interpreter handles.
   *
   * @return the effect algebra class
   */
  Class<?> value();
}
