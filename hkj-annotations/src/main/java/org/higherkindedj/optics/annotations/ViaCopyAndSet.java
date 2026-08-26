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
 *   <li>{@link #copyConstructor()} names a supertype the state does not live on, so the constructor
 *       it selects cannot copy the source's own fields
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
 * <p>An overloaded copy constructor is disambiguated with {@link #copyConstructor()}, which names
 * the parameter type to cast the source to.
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
   * <p>If empty (the default), the source is passed unchanged: {@code new SourceType(source)}.
   *
   * <p>Give a fully qualified class name naming a supertype of the source type to pass the source
   * under that type: {@code new SourceType((Base) source)}. Name the class alone - no type
   * arguments, since the processor supplies them from the source type's own {@code extends} clause
   * - and a nested class as {@code com.example.Outer.Base}. The attribute is a plain string, so it
   * is not resolved against this interface's imports.
   *
   * <p>The processor rejects a name that does not resolve, one naming a type the source does not
   * extend or implement, one the generated class cannot see, and one no constructor of the source
   * accepts. What it cannot check is whether the constructor it selects copies everything: see the
   * warning above.
   *
   * <p>Only the overloaded case needs it. A sole {@code SourceType(Base other)} already accepts the
   * source by widening; the cast is what picks between {@code SourceType(Base)} and {@code
   * SourceType(SourceType)}, or resolves a call left ambiguous by two unrelated supertypes:
   *
   * <pre>{@code
   * public class Custom implements Base, Auditable {
   *     public Custom(Base other) { ... }
   *     public Custom(Auditable other) { ... }   // new Custom(source) is ambiguous
   * }
   *
   * @ViaCopyAndSet(copyConstructor = "com.example.Base", setter = "setName")
   * Lens<Custom, String> name();                  // new Custom((Base) source)
   * }</pre>
   *
   * @return the copy constructor parameter type, or empty to pass the source type unchanged
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
