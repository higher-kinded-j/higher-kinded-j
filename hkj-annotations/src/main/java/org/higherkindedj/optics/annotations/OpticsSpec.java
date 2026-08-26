// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * Marker interface for optics specification interfaces.
 *
 * <p>Extend this interface to define custom optics for an external type {@code S}. The processor
 * will analyse abstract methods and generate implementations based on their return types and
 * annotations.
 *
 * <h2>Generated Class Naming</h2>
 *
 * <p>The generated class name is derived from the spec interface name:
 *
 * <ul>
 *   <li>If the interface name ends with "Spec", that suffix is removed (e.g., {@code
 *       PersonOpticsSpec} → {@code PersonOptics})
 *   <li>Otherwise, "Impl" is appended (e.g., {@code PersonOptics} → {@code PersonOpticsImpl})
 * </ul>
 *
 * <p><strong>Recommended convention:</strong> Name your spec interface with a "Spec" suffix for
 * cleaner generated class names.
 *
 * <h2>Method Annotations</h2>
 *
 * <p>Abstract methods must return an optic type ({@code Lens}, {@code Prism}, {@code Traversal},
 * etc.) and be annotated with a copy strategy or matching annotation:
 *
 * <ul>
 *   <li>{@link ViaBuilder} - use builder pattern
 *   <li>{@link Wither} - use wither methods
 *   <li>{@link ViaConstructor} - use all-args constructor
 *   <li>{@link ViaCopyAndSet} - use copy constructor and setter
 *   <li>{@link InstanceOf} - type-based prism matching
 *   <li>{@link MatchWhen} - predicate-based prism matching
 *   <li>{@link TraverseWith} - explicit traversal specification
 *   <li>{@link ThroughField} - field-based traversal
 * </ul>
 *
 * <h2>Composition</h2>
 *
 * <p>A spec interface declares primitives only. A {@code default} method is rejected at the
 * declaration: annotation processing cannot read a method body, so the generated class could only
 * carry a stub that throws. Composed optics belong in a {@code static} method on the interface, or
 * in an ordinary utility class; either one calls the generated statics.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @ImportOptics
 * public interface PersonOpticsSpec extends OpticsSpec<Person> {
 *
 *     @ViaBuilder
 *     Lens<Person, String> name();
 *
 *     @ViaBuilder
 *     Lens<Person, Integer> age();
 *
 *     // Composition lives in a static method, referring to the generated class by name
 *     static Lens<Person, String> firstName() {
 *         return PersonOptics.name().andThen(
 *             Lens.of(
 *                 n -> n.split(" ")[0],
 *                 (n, first) -> first + n.substring(n.indexOf(" "))
 *             )
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>The processor generates a static utility class from the abstract methods (note: "Spec" suffix
 * removed):
 *
 * <pre>{@code
 * // Generated from PersonOpticsSpec
 * public final class PersonOptics {
 *     private PersonOptics() {}
 *     public static Lens<Person, String> name() { ... }
 *     public static Lens<Person, Integer> age() { ... }
 * }
 * }</pre>
 *
 * <p>{@code S} names one class, record or interface. It is read for its members and rebuilt through
 * a constructor, wither or setter, so a type parameter of the spec interface itself, {@code
 * interface BoxOpticsSpec<S extends Box> extends OpticsSpec<S>}, has nothing to generate from and
 * is rejected. A source type that is itself generic is supported, and the spec names its own type
 * parameters: {@code interface BoxOpticsSpec<U> extends OpticsSpec<Box<U>>} generates {@code static
 * <U> Lens<Box<U>, String> label()}, while {@code OpticsSpec<Box<String>>} generates a method with
 * no type parameters at all.
 *
 * @param <S> the source type to create optics for; one class, record or interface
 * @see ImportOptics
 * @see ViaBuilder
 * @see Wither
 * @see ViaConstructor
 */
public interface OpticsSpec<S> {
  // Marker interface only - allows processor to extract S via type argument
}
