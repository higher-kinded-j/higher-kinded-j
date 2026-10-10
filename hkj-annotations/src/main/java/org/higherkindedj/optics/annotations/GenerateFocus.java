// Copyright (c) 2025 - 2026 Magnus Smith
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
 * <p>Each path writes its field through the record's canonical constructor, exactly as {@link
 * GenerateLenses} does, so a compact constructor that normalises or checks a component runs on
 * every write.
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
 * <p>Retained in the class file so that a record in one module stays navigable from another's
 * {@code Focus}. Navigability is decided by asking the component's type whether it carries this
 * annotation, which a type read from a jar can only answer if the annotation outlived the
 * compilation that declared it. The module declaring the record must also run the processor: a
 * navigator composes the {@code Focus} class that module generated, and keeps the plain path, with
 * a note, when there is none.
 *
 * @see GenerateFocus#generateNavigators()
 * @see GenerateFocus#maxNavigatorDepth()
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
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
   * {@code @GenerateFocus}. A record from another module qualifies once that module has generated
   * its {@code Focus} class. A navigator into a generic record declares the record's type
   * parameters, instantiated with the arguments the field gives it: {@code Box<String> parcel} gets
   * a {@code ParcelNavigator<S, String>}. A field naming no single type for one of them, a wildcard
   * such as {@code Box<?>} or a raw {@code Box}, keeps the path method it would have without
   * navigators.
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
   * Whether a navigator's own navigation methods return navigators.
   *
   * <p>A value of {@code 1} or less changes the generated code: the navigation methods of this
   * record's navigators return plain {@code FocusPath}, {@code AffinePath} or {@code TraversalPath}
   * instances. So in {@code OrderFocus.customer().email()}, {@code customer()} still returns a
   * navigator, {@code email()} returns a plain path, and any further hop composes with {@code
   * .via()}.
   *
   * <p>A value of {@code 2} or more, the default included, ends no chain. A hop into another
   * navigable record returns the navigator that the {@code Focus} class of the record it leaves
   * declares for that field, and that navigator is generated once per record, so a longer chain
   * generates no more code. A chain still ends where a hop widens the path: a navigator over an
   * {@code Optional} or a collection that hops into a record field returns the composed {@code
   * AffinePath} or {@code TraversalPath}.
   *
   * <p>Default: 3.
   *
   * @return {@code 1} or less to end each chain at a navigator's own navigation methods; {@code 2}
   *     or more to end none by depth
   */
  int maxNavigatorDepth() default 3;

  /**
   * When true, fields of SPI-registered ZERO_OR_MORE container types (e.g., Eclipse Collections
   * {@code ImmutableList}, Guava {@code ImmutableSet}, {@code Map}, arrays) will automatically
   * return {@code TraversalPath} instead of {@code FocusPath}.
   *
   * <p>By default this is false, preserving backwards compatibility: ZERO_OR_MORE SPI types remain
   * as {@code FocusPath}, and users must manually call {@code .each(eachInstance)} for traversal.
   *
   * <p>When enabled, the processor calls {@code .each(opticExpression)} automatically for any SPI
   * generator with {@code Cardinality.ZERO_OR_MORE} cardinality.
   *
   * @return true to auto-widen ZERO_OR_MORE SPI types to TraversalPath (default: false)
   * @since 0.4.0
   */
  boolean widenCollections() default false;

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
