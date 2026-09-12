// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a domain-to-wire field rename on a {@link MappingSpec} method.
 *
 * <p>The rename is an abstract, zero-parameter method named after the <em>domain</em> component,
 * with {@code to} naming the <em>wire</em> component it maps to:
 *
 * <pre>{@code
 * @GenerateMapping
 * public interface PersonMapping extends MappingSpec<Person, PersonDto> {
 *   @MapField(to = "fullName")
 *   String name(); // domain Person.name <-> wire PersonDto.fullName
 * }
 * }</pre>
 *
 * <p>Each wire component takes exactly one domain source; colliding renames are compile errors.
 *
 * <p>A rename may also be named after a component of a record that a {@link Flatten} marker spreads
 * across the wire, pointing that inner component at a differently named wire component. The
 * flattened component itself is never renamed: it has no single wire counterpart.
 *
 * <p>Retained in the class file so that a shared vocabulary interface keeps its renames when a
 * dependent compilation extends it from a jar; a spec reading the interface from a class file would
 * otherwise see a bare abstract method and refuse it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MapField {
  /**
   * The wire-side field name this domain field maps to.
   *
   * @return the wire field name
   */
  String to();
}
