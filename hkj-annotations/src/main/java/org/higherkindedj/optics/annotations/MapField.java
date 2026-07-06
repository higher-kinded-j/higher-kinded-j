// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a domain-to-wire field rename on a {@link MappingSpec} method (issue #600).
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
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface MapField {
  /**
   * The wire-side field name this domain field maps to.
   *
   * @return the wire field name
   */
  String to();
}
