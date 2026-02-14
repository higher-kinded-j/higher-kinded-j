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
 * <h3>Generated Class Naming</h3>
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
 * <h3>Method Annotations</h3>
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
 * <p>Default methods are copied unchanged to the generated class, allowing manual implementations
 * for complex compositions. Note: default method bodies must use explicit class-qualified
 * references (e.g., {@code PersonOptics.name()}) rather than unqualified method calls.
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
 *     // Manual implementation for complex cases
 *     default Lens<Person, String> firstName() {
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
 * <p>The processor generates a static utility class (note: "Spec" suffix removed):
 *
 * <pre>{@code
 * // Generated from PersonOpticsSpec
 * public final class PersonOptics {
 *     private PersonOptics() {}
 *     public static Lens<Person, String> name() { ... }
 *     public static Lens<Person, Integer> age() { ... }
 *     public static Lens<Person, String> firstName() { ... }
 * }
 * }</pre>
 *
 * @param <S> the source type to create optics for
 * @see ImportOptics
 * @see ViaBuilder
 * @see Wither
 * @see ViaConstructor
 */
public interface OpticsSpec<S> {
  // Marker interface only - allows processor to extract S via type argument
}
