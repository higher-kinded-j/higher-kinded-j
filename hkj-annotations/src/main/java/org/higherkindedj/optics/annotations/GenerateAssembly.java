// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record for which a validated-assembly companion should be generated. The generated class
 * lives in the record's package and is named by appending "Assembly" to the record's name; for a
 * nested record the enclosing simple names are joined first ({@code Outer.Inner} gives {@code
 * OuterInnerAssembly}).
 *
 * <p>The companion is a staged, order-enforcing builder over {@code
 * Validated<NonEmptyList<FieldError>, R>} with one named method per record component, in
 * declaration order. Labels come from the component names, so they cannot drift; the arity is
 * exact, so there is no ceiling; and the terminal {@code assemble()} invokes the canonical
 * constructor, so field order cannot be wrong.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @GenerateAssembly
 * record User(String name, String email, int age) {}
 *
 * // Generated companion:
 * Validated<NonEmptyList<FieldError>, User> user =
 *     UserAssembly.fields()
 *         .name(parseName(dto.name()))      // label "name" attached automatically
 *         .email(parseEmail(dto.email()))
 *         .age(parseAge(dto.age()))
 *         .assemble();                       // canonical constructor baked in
 * }</pre>
 *
 * <p>Every bad field is reported at once, each error carrying its path, in component-declaration
 * order. A component whose type is itself annotated accepts its sub-companion's result directly;
 * the outer component name is prepended onto the inner paths ({@code "address.zip"}).
 *
 * <p>Generic records are not supported. See the hand-written {@code Validated.fields()} builder for
 * records you cannot annotate.
 *
 * @see Generated
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateAssembly {}
