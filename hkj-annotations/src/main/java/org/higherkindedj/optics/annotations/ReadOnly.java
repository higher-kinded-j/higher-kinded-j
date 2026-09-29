// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reads a bean getter that has no setter as a read-only property: {@code parse} reads it, and
 * {@code build} leaves it unwritten. The marker goes on a spec method named after the getter's
 * property.
 *
 * <p>A bean property is a getter and a writer that share a name, so a getter with no writer is left
 * out of the mapping, and one named after a domain component is refused as the likely misspelling.
 * Some beans mean it, though. An OpenAPI {@code readOnly} property, which openapi-generator writes
 * as a getter set through a {@code @JsonCreator} constructor, is sent in responses and never in
 * requests. A client wants to parse it and must not build it:
 *
 * <pre>{@code
 * public record Pet(Long id, String name) {}
 *
 * public class PetModel {             // generated: 'id' is readOnly
 *   public Long getId() { ... }       // no setter
 *   public String getName() { ... }
 *   public void setName(String name) { ... }
 * }
 *
 * @GenerateMapping
 * public interface PetMapping extends MappingSpec<Pet, PetModel> {
 *   @ReadOnly
 *   Long id();
 * }
 * // parse reads getId() into Pet.id; build writes name, and leaves id unset
 * }</pre>
 *
 * <p>{@code build} then produces a wire that {@code parse} cannot read back whole, so the mapping
 * is no {@code ValidatedPrism}. The generated Impl carries {@code parse} and {@code build}, each
 * exposed as its own half, {@code asValidatedParse()} and {@code asValidatedBuild()}, and no {@code
 * asValidatedPrism()} or {@code asIso()}. It nests wherever one direction is used: in a mapping
 * that only parses, an {@code UpdateSpec} or a merge through {@code parse}, in a mapping that only
 * builds, and as a property another mapping reads read-only. A mapping that builds and parses the
 * component holding it cannot nest it. A bean all of whose properties are read-only has nothing
 * left to build, and maps parse-only.
 *
 * <p>The marker may be a bare abstract method, whose return type is not read, so it may restate the
 * getter's own type; the generated Impl stubs it out, like a {@link MapField} rename. Where the
 * component it reads into has the same name and converts through a leaf, the marker goes on that
 * {@code default} leaf instead, since a marker and a same-named leaf would be one method. The
 * marker is retained in the class file, so that a shared vocabulary survives a jar.
 *
 * <p>Only a {@link MappingSpec} over a bean that is both read and written reads a property this
 * way, and only where parse can read the whole domain, so not on a projection. A marker the spec
 * declares itself must name a getter a domain component maps to: one the bean leaves unpaired, or a
 * getter-only {@code List}, which build then leaves alone rather than filling it through {@code
 * getX().addAll(...)}. One naming anything else is refused. One inherited from a <em>mix-in</em>
 * binds where it can and is otherwise inert, as every other inherited vocabulary member is, so one
 * mix-in serves specs whose wires differ: carrying {@link Unmapped} too, it leaves the getter out
 * of a projection or an {@code UpdateSpec} that extends it. A marker on a converting leaf cannot
 * also carry {@link Unmapped}, which only a bare marker can. To leave a getter out of the mapping
 * altogether, mark it {@link Unmapped} instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ReadOnly {}
