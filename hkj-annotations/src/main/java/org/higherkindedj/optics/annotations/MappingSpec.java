// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * The specification interface for a generated bidirectional mapping (issue #600).
 *
 * <p>Declare an interface extending {@code MappingSpec<Domain, Wire>} and annotate it with {@link
 * GenerateMapping}. An empty body means same-named, same-typed fields match automatically; a
 * validated leaf is a typed {@code default} method named after the <em>domain</em> component,
 * returning the boundary optic {@code ValidatedPrism<WireComponent, DomainComponent>} — note the
 * argument order is wire first, domain second (the opposite of this interface's own order):
 *
 * <pre>{@code
 * @GenerateMapping
 * public interface UserMapping extends MappingSpec<User, UserDto> {
 *   default ValidatedPrism<String, EmailAddress> email() { return EmailCodecs.EMAIL; }
 * }
 * // generates UserMappingImpl with:
 * //   UserDto build(User)                                        (total)
 * //   Validated<NonEmptyList<FieldError>, User> parse(UserDto)   (accumulating, located)
 * }</pre>
 *
 * @param <A> the domain type (a record)
 * @param <B> the wire type (a record)
 */
public interface MappingSpec<A, B> {}
