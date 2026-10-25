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
 * component becomes a static method returning a {@code FocusPath}, or an {@code AffinePath} or
 * {@code TraversalPath} for an {@code Optional} or collection component, that can be composed with
 * other optics for deep navigation.
 *
 * <p>Each path writes its field through the record's canonical constructor, exactly as {@link
 * GenerateLenses} does, so a compact constructor that normalises or checks a component runs on
 * every write.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @GenerateFocus
 * record Shopper(String name, Address address, Optional<String> nickname) {}
 *
 * @GenerateFocus
 * record Address(String street, String city, String postcode) {}
 *
 * // Generated code provides:
 * // ShopperFocus.name() -> FocusPath<Shopper, String>
 * // ShopperFocus.address() -> AddressNavigator<Shopper>, which is a FocusPath<Shopper, Address>
 * // ShopperFocus.nickname() -> AffinePath<Shopper, String>
 * // AddressFocus.street() -> FocusPath<Address, String>
 *
 * // Navigate fluently, or use the navigator as the path it is:
 * FocusPath<Shopper, String> streetPath = ShopperFocus.address().street();
 * FocusPath<Shopper, Address> addressPath = ShopperFocus.address();
 * }</pre>
 *
 * <h2>Fluent Navigation with Navigators</h2>
 *
 * <p>The method for a field whose type is itself a {@code @GenerateFocus} record returns a
 * navigator: the path to that field, with a method for each of the record's own fields. See {@link
 * #generateNavigators()}.
 *
 * <pre>{@code
 * @GenerateFocus
 * record Company(String name, Address address) {}
 *
 * @GenerateFocus
 * record Address(String street, String city, String postcode) {}
 *
 * String city = CompanyFocus.address().city().get(company);
 * }</pre>
 *
 * <p>By default, the generated class is placed in the same package as the annotated record. Use the
 * {@link #targetPackage()} element to specify a different package for the generated class.
 *
 * <p>Retained in the class file so that a record in one module stays navigable from another's
 * {@code Focus}. Navigability is decided by asking the component's type whether it carries this
 * annotation, which a type read from a jar can only answer if the annotation outlived the
 * compilation that declared it. The module declaring the record must also run the processor: a
 * navigator composes the {@code Focus} class that module generated, so where there is none the
 * field gets no navigator, and a note says why.
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
   * Whether a field whose type is a {@code @GenerateFocus} record, or a container of one, gets a
   * navigator.
   *
   * <p>A navigator enables patterns like {@code PersonFocus.address().city()} without explicit
   * {@code .via()} calls. A record from another module qualifies once that module has generated its
   * {@code Focus} class. A navigator into a generic record declares the record's type parameters,
   * instantiated with the arguments the field gives it: {@code Box<String> parcel} gets a {@code
   * ParcelNavigator<S, String>}. A field naming no single type for one of them, a wildcard such as
   * {@code Box<?>} or a raw {@code Box}, gets no navigator.
   *
   * <p>A navigator is the path the field's method would return without one, with a navigation
   * method added for each field of the record it reaches:
   *
   * <ul>
   *   <li>It extends {@code NavigatorFocusPath}, {@code NavigatorAffinePath} or {@code
   *       NavigatorTraversalPath}, so it goes wherever a path of that type goes, and code that
   *       names the field's path type keeps compiling when the field gets a navigator.
   *   <li>It steps into a container only where the field's method does: an {@code Optional}, {@code
   *       Maybe}, {@code List}, {@code Set} or {@code Collection}, and an {@code Either}, {@code
   *       Try} or other zero-or-one SPI container, always; a {@code Map}, an array or other
   *       zero-or-more one under {@link #widenCollections()} or {@link #widenFields()}. Containers
   *       nested in one another compose to the record they hold. So {@code List<LineItem> lines}
   *       gets a navigator over every line, and {@code lines().quantity()} chains. A {@code Kind}
   *       field the processor traverses gets one too.
   *   <li>It has a form nested in it for each wider path type, {@code AffineForm} and {@code
   *       TraversalForm}: the same navigator over that path. A hop that widens the path hands on
   *       the form. So from {@code OrderHistoryFocus.orders()}, a navigator over a {@code
   *       List<Order>}, {@code orders().customer()} returns {@code
   *       OrderFocus.CustomerNavigator.TraversalForm}, and {@code .email()} chains.
   *   <li>It cannot navigate to a field of the record it reaches named like one of the path's own
   *       no-argument methods, such as {@code last} or {@code head}, since a call to it would reach
   *       the path's method instead. A record that writes {@code generateNavigators = true} is
   *       refused at the field that navigates; one that leaves the default keeps that field's plain
   *       path, with a note. A form is left out where its own path has such a method, such as
   *       {@code headOption} on a {@code TraversalPath}, and a hop that would need it returns a
   *       plain path. The record declaring the field is told so, with a note, if it writes {@code
   *       generateNavigators = true}.
   *   <li>A navigator class takes its field's name with the first letter in upper case, so two
   *       fields whose names differ only in that letter's case, such as {@code items} and {@code
   *       Items}, cannot both have one. Of two that would, the first in declaration order keeps it;
   *       an earlier field with no navigator, such as a {@code String items}, leaves the name free.
   *       A record that writes {@code generateNavigators = true} is refused at the later field; one
   *       that leaves the default keeps that field's plain path, with a note.
   * </ul>
   *
   * @return true to generate navigators, false for plain paths only (default: true, since 0.5.0)
   */
  boolean generateNavigators() default true;

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
   * declares for that field, or that navigator's form for a wider path. Each is generated once per
   * record, so a longer chain generates no more code.
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
   * return {@code TraversalPath} instead of {@code FocusPath}. An array of a primitive type, such
   * as {@code int[]}, stays a {@code FocusPath}: {@code EachInstances.arrayEach()} is {@code
   * Each<A[], A>}, and no primitive is an {@code A}.
   *
   * <p>By default this is false, preserving backwards compatibility: ZERO_OR_MORE SPI types remain
   * as {@code FocusPath}, and users must manually call {@code .each(eachInstance)} for traversal.
   *
   * <p>When enabled, the processor calls {@code .each(opticExpression)} automatically for any SPI
   * generator with {@code Cardinality.ZERO_OR_MORE} cardinality.
   *
   * <p>To widen some containers and leave others, name the fields in {@link #widenFields()}
   * instead. Beside {@code widenCollections = true}, {@code widenFields} widens nothing more, and
   * draws a warning. A navigator steps into a zero-or-more container only where it is widened.
   *
   * @return true to auto-widen ZERO_OR_MORE SPI types to TraversalPath (default: false)
   * @since 0.4.0
   */
  boolean widenCollections() default false;

  /**
   * Fields whose ZERO_OR_MORE SPI container is widened to a {@code TraversalPath}, as {@link
   * #widenCollections()} widens every such field. Use it to widen one container and keep another,
   * such as a {@code Map}, whole.
   *
   * <p>A navigator steps into a container only where the field is widened, so this is also how a
   * {@code Map}, an array or a third-party collection of {@code @GenerateFocus} records gets a
   * navigator over its elements.
   *
   * <p>Each name is checked against the record's components. A name that matches none is an error,
   * offering the component it most likely meant. A name whose field this cannot widen draws a
   * warning: a {@code List}, an {@code Optional} or another container the field's method widens
   * whether named or not, or a field that holds no such container, such as a {@code String} or an
   * {@code int[]}.
   *
   * @return names of the fields to widen, or empty for none beyond {@link #widenCollections()}
   * @since 0.5.0
   */
  String[] widenFields() default {};

  /**
   * Field names to include in navigator generation.
   *
   * <p>If empty (the default), all fields with navigable types are included. When specified, only
   * the listed fields will have navigator methods generated.
   *
   * <p>Each name is checked against the record's components, and a name that matches none is an
   * error, offering the component it most likely meant: such a name would turn a navigator off
   * rather than on. A name whose field has no navigator, such as a {@code String}, an unannotated
   * record, or a {@code Map} of navigable records that the record does not widen, draws a warning,
   * unless a note or error already explains why. Set this or {@link #excludeFields()}, not both:
   * beside a non-empty {@code includeFields}, {@code excludeFields} is never read, and draws a
   * warning. With {@code generateNavigators = false}, neither is read, and each draws a warning.
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
   * <p>Each name is checked against the record's components, and a name that matches none is an
   * error, offering the component it most likely meant. Set this or {@link #includeFields()}, not
   * both: beside a non-empty {@code includeFields}, this is never read, and draws a warning. With
   * {@code generateNavigators = false}, it is not read either, and draws a warning.
   *
   * @return array of field names to exclude
   */
  String[] excludeFields() default {};
}
