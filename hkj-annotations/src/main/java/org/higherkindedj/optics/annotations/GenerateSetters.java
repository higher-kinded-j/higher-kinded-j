// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java record for which a Setters utility class should be generated. The generated class
 * will be named by appending "Setters" to the record's name.
 *
 * <p>For each component in the record, a static method will be generated that returns a {@code
 * Setter} for that component. Additionally, convenience setter methods will be generated.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @GenerateSetters
 * public record Person(String name, int age) {}
 *
 * // Generates:
 * public final class PersonSetters {
 *   public static Setter<Person, String> name() { ... }
 *   public static Setter<Person, Integer> age() { ... }
 *   public static Person withName(Person source, String newValue) { ... }
 *   public static Person withAge(Person source, int newValue) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateSetters {}
