// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the lens should use a copy constructor followed by a setter.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a lens that
 * creates a copy of the source object and then mutates it via a setter method.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * Lens.of(
 *     source -> source.getFieldName(),
 *     (source, newValue) -> {
 *         SourceType copy = new SourceType(source);
 *         copy.setFieldName(newValue);
 *         return copy;
 *     }
 * )
 * }</pre>
 *
 * <p><strong>Warning:</strong> This strategy produces mutable intermediate objects, which may
 * violate lens laws if:
 *
 * <ul>
 *   <li>The setter has side effects beyond setting the field
 *   <li>The copy constructor does not create a true deep copy
 *   <li>The object maintains internal invariants that the setter does not preserve
 * </ul>
 *
 * <p>Prefer {@link ViaBuilder} or {@link Wither} when available, as they typically provide stronger
 * immutability guarantees.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Legacy class with copy constructor and setters
 * public class LegacyPerson {
 *     private String name;
 *
 *     public LegacyPerson() {}
 *     public LegacyPerson(LegacyPerson other) { this.name = other.name; }
 *
 *     public String getName() { return name; }
 *     public void setName(String name) { this.name = name; }
 * }
 *
 * @ImportOptics
 * interface LegacyPersonOptics extends OpticsSpec<LegacyPerson> {
 *
 *     @ViaCopyAndSet(setter = "setName")
 *     Lens<LegacyPerson, String> name();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see ViaBuilder
 * @see Wither
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ViaCopyAndSet {

  /**
   * The copy constructor parameter type.
   *
   * <p>If empty (the default), assumes a copy constructor that takes an instance of the source
   * type: {@code new SourceType(source)}.
   *
   * <p>Specify a fully qualified class name if the copy constructor takes a different type (e.g., a
   * base class or interface).
   *
   * @return the copy constructor parameter type, or empty to use the source type
   */
  String copyConstructor() default "";

  /**
   * The setter method name.
   *
   * <p>For example, "setName" for a name field.
   *
   * @return the setter method name
   */
  String setter();
}
