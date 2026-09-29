// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

/**
 * The specification interface for a generated bidirectional mapping.
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
 * <p>The generated Impl reads each {@code default} leaf and derived field it calls once, on its
 * first use, and keeps what it answers, so a leaf that builds its codec, such as a date codec over
 * a {@code DateTimeFormatter}, builds it once rather than on every call. What it keeps is shared by
 * every caller of the Impl, on every thread, for as long as the Impl lives. A leaf that picks its
 * codec on each call, from a flag, a system property or a field a test changes, keeps its first
 * pick, so make a choice that must vary inside the codec's parse. Build the codec from parts that
 * are safe to share: a {@code DateTimeFormatter}, never a {@code SimpleDateFormat} or a {@code
 * DecimalFormat}.
 *
 * <p>The wire type {@code W} may be a record or a bean-shaped class: a mutable class with a no-args
 * constructor and getters/setters, or an immutable one with a builder. The bean is read through
 * getters and written through setters or a builder, and one offering only one of the two maps that
 * way only, with {@code parse} or {@code build} alone; every reference-typed read is null-guarded,
 * so an unset property parses to a located {@code FieldError} rather than throwing. A component
 * whose {@code null} means <em>absent</em> rather than <em>broken</em> says so with {@link
 * OptionalBridge}; a bean wire needs no such declaration, because it bridges an {@code Optional}
 * component automatically wherever the property can be written as null. A protobuf-java message is
 * read by its fields: a field with {@code hasX()} reads {@code null} when unset, and an empty
 * {@code Optional} leaves it unset. The domain type {@code D} stays a record (or a sealed interface
 * of records), since {@code parse} assembles it through its canonical constructor.
 *
 * <p>A spec names one tier: an interface extending {@code MappingSpec} must not also extend {@link
 * UpdateSpec}, whose sparse null-as-absent tier emits {@code updateFrom} alone, and declaring both
 * is rejected with a diagnostic. A domain needing both is a pair of specs, which may share their
 * renames and leaves through a plain mix-in interface both extend.
 *
 * @param <D> the domain type (a record or a sealed interface of records)
 * @param <W> the wire type (a record or a bean-shaped class)
 * @see UpdateSpec
 */
public interface MappingSpec<D, W> {}
