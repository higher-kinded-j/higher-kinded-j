// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reads a bean accessor the mapping leaves out as deliberate, on an abstract marker method named
 * after it.
 *
 * <p>A bean property is a getter and a writer that share a name, so an accessor with no partner is
 * left out of the mapping. Where that accessor is named after a domain component the bean carries
 * no other way, leaving it out would drop the component without a word, and the mapping is refused.
 * Some beans mean it, though: a response DTO reused as a PATCH body whose {@code getId()} the
 * client must not change, a view whose {@code getStatus()} is computed on the wire, a generated
 * request with a setter the domain does not model. The marker says so, and is named after the
 * <em>accessor's</em> property:
 *
 * <pre>{@code
 * public record Contact(String id, String name) {}
 *
 * public class ContactPatch {          // shared with the GET response
 *   public String getId() { ... }      // server-assigned: no setter
 *   public String getName() { ... }
 *   public void setName(String name) { ... }
 * }
 *
 * @GenerateMapping
 * public interface ContactPatchMapping extends UpdateSpec<Contact, ContactPatch> {
 *   @Unmapped
 *   String id();
 * }
 * // updateFrom folds 'name' and never reads getId(), as if the bean did not declare it
 * }</pre>
 *
 * <p>The marker changes nothing the mapping generates: the accessor was never a property, so the
 * component it names stays unmapped, and a wire that is narrower than the domain is still a
 * projection. All it withholds is the refusal. It reaches both tiers, a {@link MappingSpec} and a
 * sparse {@link UpdateSpec}, and both refusals a stray accessor draws there: an accessor named
 * after a domain component, and a {@code setX} setter a PATCH bean cannot read.
 *
 * <p>The return type is not read, so it may restate the accessor's own type. The marker is stubbed
 * out by the generated Impl, like a {@link MapField} rename, and is retained in the class file so
 * that a shared vocabulary survives a jar.
 *
 * <p>A marker the spec declares itself must name an accessor the bean leaves unpaired: one naming a
 * property the mapping carries, or naming nothing on the wire, is refused as the misspelling it
 * usually is. One inherited from a <em>mix-in</em> binds where it can and is otherwise inert, as
 * every other inherited vocabulary member is, so one mix-in serves specs whose wires differ.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Unmapped {}
