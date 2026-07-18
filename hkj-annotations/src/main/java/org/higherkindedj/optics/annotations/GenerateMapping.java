// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates a compile-time, reflection-free bidirectional record mapping from a {@link MappingSpec}
 * interface (issue #600).
 *
 * <p>The generated class is named {@code <Spec>Impl} beside the spec (a nested spec joins its
 * enclosing simple names, so {@code Shop.CustomerMapping} generates {@code
 * ShopCustomerMappingImpl}) and is used through its {@code INSTANCE} constant. It exposes a total
 * {@code build(Domain) : Wire} and an accumulating {@code parse(Wire) :
 * Validated<NonEmptyList<FieldError>, Domain>} whose failures are located by component name.
 *
 * <p>Supported spec shapes:
 *
 * <ul>
 *   <li>The spec extends {@code MappingSpec} and nothing else — every rename and leaf is declared
 *       directly on the spec (spec inheritance arrives with the full mapper).
 *   <li>Same-named, same-typed components match automatically; {@link MapField} declares renames.
 *   <li>A validated leaf is a zero-parameter {@code default} method named after the domain
 *       component, returning {@code ValidatedPrism<WireComponent, DomainComponent>}. An explicit
 *       leaf wins even over a same-typed match, so it can validate or normalise a copied field.
 *   <li>Record components mapped by another spec in the same compilation nest automatically, and
 *       {@code List}/{@code Optional} components lift through the element's leaf or spec.
 *   <li>Sealed interface pairs dispatch over their permitted subtype pairs, one spec per pair.
 *   <li>The wire may be a bean-shaped class (issue #628) instead of a record: a mutable class with
 *       a no-args constructor and getters/setters, or an immutable one with a builder. {@code
 *       build} fills it through setters or a builder and {@code parse} reads it through getters. An
 *       unset bean property is null, so every reference-typed read is null-guarded into a located
 *       {@code FieldError}; those guards make an {@code asIso()} truthful only for an all-primitive
 *       bean. A domain {@code Optional<T>} bridges to a nullable bean property {@code T} (empty
 *       maps to absent). The domain stays a record.
 *   <li>A lossless mapping additionally gets {@code asIso()}; a wire record with fewer components
 *       maps as a projection with {@code asLens()} and no {@code parse} (truthful types); every
 *       parse-capable mapping gets {@code asValidatedPrism()} so it plugs in wherever a leaf does.
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateMapping {}
