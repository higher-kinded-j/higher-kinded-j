// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java record for which a Focus DSL utility class should be generated. The generated class
 * will be named by appending "Focus" to the record's name.
 *
 * <p>The generated class provides type-safe navigation paths using the Focus DSL. Each record
 * component becomes a static method returning a {@code FocusPath} that can be composed with other
 * optics for deep navigation.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @GenerateFocus
 * record User(String name, Address address) {}
 *
 * @GenerateFocus
 * record Address(String street, String city) {}
 *
 * // Generated code provides:
 * // UserFocus.name() -> FocusPath<User, String>
 * // UserFocus.address() -> FocusPath<User, Address>
 * // AddressFocus.street() -> FocusPath<Address, String>
 *
 * // Compose paths for deep navigation:
 * FocusPath<User, String> streetPath = UserFocus.address().via(AddressFocus.street().toLens());
 * }</pre>
 *
 * <h2>Fluent Navigation with Navigators</h2>
 *
 * <p>When {@link #generateNavigators()} is enabled, the processor generates navigator wrapper
 * classes that enable fluent cross-type navigation without explicit {@code .via()} calls:
 *
 * <pre>{@code
 * @GenerateFocus(generateNavigators = true)
 * record Company(String name, Address headquarters) {}
 *
 * @GenerateFocus(generateNavigators = true)
 * record Address(String street, String city) {}
 *
 * // With navigators enabled:
 * String city = CompanyFocus.headquarters().city().get(company);
 *
 * // Instead of:
 * String city = CompanyFocus.headquarters().via(AddressFocus.city().toLens()).get(company);
 * }</pre>
 *
 * <p>By default, the generated class is placed in the same package as the annotated record. Use the
 * {@link #targetPackage()} element to specify a different package for the generated class.
 *
 * @see org.higherkindedj.optics.focus.FocusPath
 * @see org.higherkindedj.optics.focus.AffinePath
 * @see org.higherkindedj.optics.focus.TraversalPath
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateFocus {

  /**
   * The package where the generated class should be placed. If empty (the default), the generated
   * class will be placed in the same package as the annotated record.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";

  /**
   * When true, generates fluent navigator wrapper classes for cross-type navigation.
   *
   * <p>This enables patterns like {@code PersonFocus.address().city()} without explicit {@code
   * .via()} calls. Navigator classes are generated for fields whose types are also annotated with
   * {@code @GenerateFocus}.
   *
   * <p>The navigator classes:
   *
   * <ul>
   *   <li>Wrap the underlying {@code FocusPath} and delegate all standard methods
   *   <li>Provide navigation methods for each field of the nested type
   *   <li>Handle path type widening (e.g., {@code FocusPath} to {@code AffinePath}) automatically
   * </ul>
   *
   * @return true to generate navigator classes, false otherwise (default: false)
   */
  boolean generateNavigators() default false;

  /**
   * Maximum depth for generated navigator chains.
   *
   * <p>This limits code generation for deeply nested structures. When the depth limit is reached,
   * navigation methods return plain {@code FocusPath}/{@code AffinePath}/{@code TraversalPath}
   * instances instead of navigators.
   *
   * <p>Valid range: 1-10. Default: 3.
   *
   * @return the maximum navigator chain depth
   */
  int maxNavigatorDepth() default 3;

  /**
   * Field names to include in navigator generation.
   *
   * <p>If empty (the default), all fields with navigable types are included. When specified, only
   * the listed fields will have navigator methods generated.
   *
   * <p>This is mutually exclusive with {@link #excludeFields()}. If both are specified, {@code
   * includeFields} takes precedence.
   *
   * @return array of field names to include, or empty for all fields
   */
  String[] includeFields() default {};

  /**
   * Field names to exclude from navigator generation.
   *
   * <p>When specified, the listed fields will not have navigator methods generated, even if their
   * types are navigable.
   *
   * <p>This is mutually exclusive with {@link #includeFields()}. If both are specified, {@code
   * includeFields} takes precedence.
   *
   * @return array of field names to exclude
   */
  String[] excludeFields() default {};
}
