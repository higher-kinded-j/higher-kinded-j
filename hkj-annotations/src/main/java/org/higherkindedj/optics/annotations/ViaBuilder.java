// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the lens should use a builder pattern for copying.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a lens that uses
 * the builder pattern for creating modified copies of the source object.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * Lens.of(
 *     source -> source.getFieldName(),
 *     (source, newValue) -> source.toBuilder().fieldName(newValue).build()
 * )
 * }</pre>
 *
 * <p>All parameters have sensible defaults for common builder patterns (e.g., Lombok's
 * {@code @Builder}, Immutables, AutoValue):
 *
 * <ul>
 *   <li>{@code fieldName()} or {@code getFieldName()} - getter on source (derived from optic method
 *       name, or explicitly specified via {@link #getter()})
 *   <li>{@code toBuilder()} - method to obtain a builder from existing instance
 *   <li>{@code fieldName(value)} - setter on builder (derived from optic method name)
 *   <li>{@code build()} - method to construct final object
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * @ImportOptics
 * interface PersonOptics extends OpticsSpec<Person> {
 *
 *     @ViaBuilder
 *     Lens<Person, String> name();
 *
 *     @ViaBuilder(setter = "emailAddress")  // builder method differs from getter
 *     Lens<Person, String> email();
 *
 *     @ViaBuilder(getter = "getUrl", setter = "withUrl")  // explicit getter for JavaBean-style
 *     Lens<Request, String> url();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see Wither
 * @see ViaConstructor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ViaBuilder {

  /**
   * Getter method name on the source object.
   *
   * <p>If empty (the default), the processor uses the optic method name as the getter. For example,
   * if the optic method is {@code name()}, the getter is assumed to be {@code name()}
   * (record-style). For JavaBean-style classes, specify the getter explicitly (e.g., {@code
   * "getName"}).
   *
   * @return the getter method name, or empty to derive from optic method name
   */
  String getter() default "";

  /**
   * Method name to obtain a builder from the source object.
   *
   * <p>This method should return a mutable builder instance that can be modified and then built
   * into a new immutable object.
   *
   * @return the toBuilder method name, defaults to "toBuilder"
   */
  String toBuilder() default "toBuilder";

  /**
   * Setter method name on the builder.
   *
   * <p>If empty (the default), the processor uses the optic method name. For example, if the optic
   * method is {@code name()}, the builder setter is assumed to be {@code name(String)}.
   *
   * @return the setter method name, or empty to derive from optic method name
   */
  String setter() default "";

  /**
   * Method name to build the final object from the builder.
   *
   * @return the build method name, defaults to "build"
   */
  String build() default "build";
}
