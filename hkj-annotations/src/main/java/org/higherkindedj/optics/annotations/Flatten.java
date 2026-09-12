// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spreads one nested domain record component across the wire's flat components, both directions.
 *
 * <p>A wire format fixed by someone else is often flat where the domain is nested: an address
 * record on the domain side, three plain fields on the wire. Restructuring the wire is not an
 * option there, and a leaf cannot help, since no single wire component carries the address. The
 * annotation goes on an abstract marker method named after the domain component:
 *
 * <pre>{@code
 * public record Address(String street, String city, String postcode) {}
 * public record Customer(String name, Address address) {}
 * public record CustomerDto(String name, String street, String city, String postcode) {}
 *
 * @GenerateMapping
 * public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
 *   @Flatten
 *   Address address();
 * }
 * // build:  street = domain.address().street(), and so on for each component of Address
 * // parse:  street, city and postcode assemble an Address; a failure locates as address.street
 * }</pre>
 *
 * <p>The component's record is spread by <em>name</em>: each of its components (the group) maps to
 * the wire component of the same name, exactly as a top-level component would, so the whole
 * vocabulary applies inside the group by name. A {@link MapField} rename named after an inner
 * component points it at a differently named wire component; a {@code default} leaf named after an
 * inner component converts it; an {@link OptionalBridge} named after an inner {@code Optional}
 * bridges it; an inner component that is itself a record nests through its own spec; containers
 * lift. The group assembles through its own {@code Validated.fields()} ladder, so its failures
 * accumulate with everyone else's and locate under the domain path ({@code address.street: must not
 * be null}), which the flat wire never named: the domain-named-paths contract, applied to a nesting
 * the wire does not have.
 *
 * <p>Names must be unambiguous, since every wire component takes exactly one source. An inner
 * component may not share its name with a component of the domain record or of another flattened
 * group (so two components of the same record type cannot both be spread), a derived field may not
 * be named after one, and a wire component named after the flattened domain component itself must
 * be fed by a rename from another component.
 *
 * <p>The marker is stubbed out by the generated Impl, like a rename, and its return type must be
 * the component's record type, so a spec that drifts from its domain fails to compile rather than
 * spreading whatever the component has become. A group whose inner components all copy by identity
 * keeps the mapping lossless, so {@code asIso()} survives; one carrying a leaf or a nested spec is
 * fallible, as it would be at the top level. A mapping carrying a group is nested by other specs
 * like any other, in the same compilation or from a dependency; the marker is retained in the class
 * file so that a dependent compilation can tell such a spec is parse-capable.
 *
 * <p>Not supported yet: a flattened component on a bean-shaped wire, on a generic spec, on a
 * projection (a wire with fewer components) or on a sparse {@link UpdateSpec} (see below); a group
 * wider than one {@code fields()} ladder; a raw or wildcard-carrying record type; and a second
 * level of spreading (a record inside the group nests through its own spec instead).
 *
 * <p>On a sparse {@code UpdateSpec} the refusal reaches a marker the spec declares itself, and one
 * it inherits from a shared mix-in only when the PATCH bean carries inner properties of the group
 * that nothing else fills. A bean declaring the group's own component instead patches it whole by
 * identity, so the marker is inert there and one vocabulary serves a full spec and its PATCH
 * sibling.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Flatten {}
