// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a compile-time, reflection-free bidirectional record mapping from a {@link MappingSpec}
 * interface.
 *
 * <p>The generated class is named {@code <Spec>Impl} beside the spec (a nested spec joins its
 * enclosing simple names, so {@code Shop.CustomerMapping} generates {@code
 * ShopCustomerMappingImpl}) and is used through its {@code INSTANCE} constant (generic specs use
 * the {@code instance()} accessor or the {@code of(...)} factory instead; see the generics bullet
 * below). It exposes a total {@code build(Domain) : Wire} and an accumulating {@code parse(Wire) :
 * Validated<NonEmptyList<FieldError>, Domain>} whose failures are located by component name.
 *
 * <p>The happy path is one empty interface:
 *
 * <pre>{@code
 * public record User(String name, EmailAddress email) {}
 * public record UserDto(String name, String email) {}
 *
 * @GenerateMapping
 * public interface UserMapping extends MappingSpec<User, UserDto> {
 *   // same-named, same-typed components map automatically; this leaf parses the one that differs
 *   default ValidatedPrism<String, EmailAddress> email() { return EmailCodecs.EMAIL; }
 * }
 *
 * UserDto dto = UserMappingImpl.INSTANCE.build(user);                          // total
 * Validated<NonEmptyList<FieldError>, User> back =
 *     UserMappingImpl.INSTANCE.parse(dto);                                     // every bad field
 * }</pre>
 *
 * <p>The full shape taxonomy, for when a mapping is not the happy path:
 *
 * <ul>
 *   <li>The spec extends {@code MappingSpec} directly, and may additionally extend plain
 *       <em>mix-in</em> interfaces: renames, leaves and derived fields inherited from a mix-in
 *       (transitively) count exactly as if declared on the spec, with Java's own precedence — a
 *       local override hides the mix-in's member, unrelated mix-ins agreeing on an abstract rename
 *       count once, and conflicting rename targets are diagnosed naming both interfaces. A mix-in
 *       must not itself be a mapping spec, and must be non-generic; diagnostics about inherited
 *       members name the declaring interface.
 *   <li>Same-named, same-typed components match automatically; {@link MapField} declares renames.
 *   <li>A validated leaf is a zero-parameter {@code default} method named after the domain
 *       component, returning {@code ValidatedPrism<WireComponent, DomainComponent>}. An explicit
 *       leaf wins even over a same-typed match, so it can validate or normalise a copied field.
 *   <li>Record components mapped by another spec in the same compilation nest automatically, and
 *       {@code List}/{@code Optional} components lift through the element's leaf or spec.
 *   <li>Sealed interface pairs dispatch over their permitted subtype pairs, one spec per pair.
 *   <li>Generic records map three ways. As concrete instantiations: {@code MappingSpec<Page<User>,
 *       PageDto<UserDto>>} classifies every component under the substitution. As threaded specs:
 *       {@code PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>>} generates one generic Impl
 *       serving every instantiation, reached through the {@code instance()} generic-singleton
 *       convention ({@code EitherMonad.instance()}); elements sharing a variable copy by identity.
 *       As element-mapped specs: the two sides thread under different variables and an abstract
 *       {@code ValidatedPrism<TDto, T>} leaf declares the element mapping, supplied at runtime
 *       through the generated {@code of(...)} factory (one prism per leaf, declaration order; the
 *       Impl carries them, so no singleton). All three nest: instantiated mappings register like
 *       any other, threaded specs resolve at use sites by type-argument unification, element-mapped
 *       specs compose in place ({@code of(entries())}) with an unresolvable element pair diagnosed.
 *       Generic mappings are record-to-record only (bean-shaped wires and {@code UpdateSpec}
 *       mappings stay concrete); raw uses and wildcards are diagnosed, array arguments are concrete
 *       and unify structurally.
 *   <li>Every reference-typed {@code parse} read is null-guarded into a located {@code FieldError}
 *       (inside containers too: a null element or map value locates by index or key, identity
 *       copies included), on both wire shapes: an unset bean property is null, and a JSON binder
 *       leaves a missing record component null just the same. A bean's guarded reads make {@code
 *       asIso()} truthful only for an all-primitive bean; a lossless record mapping keeps {@code
 *       asIso()}, its guards covering hostile bindings only.
 *   <li>The wire may be a bean-shaped class instead of a record: a mutable class with a no-args
 *       constructor and getters/setters, or an immutable one with a builder. {@code build} fills it
 *       through setters or a builder and {@code parse} reads it through getters. A domain {@code
 *       Optional<T>} bridges to a nullable bean property {@code T} (empty maps to absent). The
 *       domain stays a record.
 *   <li>A lossless mapping additionally gets {@code asIso()}; a wire record with fewer components
 *       maps as a projection with {@code asLens()} and no {@code parse} (truthful types); a
 *       projection carrying a fallible leaf swaps the lens for a validated {@code patch(Domain,
 *       Wire)} write-back — <em>dense</em> semantics: every projected component applies, a null is
 *       a located error, never absence. Every parse-capable mapping gets {@code asValidatedPrism()}
 *       so it plugs in wherever a leaf does.
 *   <li>A spec extending {@link UpdateSpec} instead of {@link MappingSpec} opts into
 *       <em>sparse</em> null-as-absent PATCH — the REST {@code PATCH} contract: it generates only
 *       {@code updateFrom(Wire) : Edits.Accumulated<Domain>}, folding the present (non-null) wire
 *       properties into an update and leaving absent ones unchanged. The two write-backs are
 *       deliberate opposites: {@code patch} is dense (a missing value is an error), {@code
 *       updateFrom} is sparse (a missing value means keep the current one).
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateMapping {}
