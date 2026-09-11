// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the leaf that converts a {@code Map} component's <em>keys</em> on a {@link MappingSpec}.
 *
 * <p>A {@code Map} component's value leaf is named after the component, like every other leaf. Its
 * keys need a second leaf, and Java forbids two zero-parameter methods sharing that name — so a key
 * leaf carries this annotation instead, and {@code value} names the domain component it belongs to:
 *
 * <pre>{@code
 * @GenerateMapping
 * public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
 *
 *   // values, named after the component: Map<..., UserDto> <-> Map<..., User>
 *   default ValidatedPrism<UserDto, User> attributes() {
 *     return UserCodecs.user();
 *   }
 *
 *   // keys: Map<String, ...> <-> Map<Locale, ...>
 *   @MapKey("attributes")
 *   default ValidatedPrism<String, Locale> attributeLocale() {
 *     return Codecs.locale();
 *   }
 * }
 * }</pre>
 *
 * <p>The method is a zero-parameter {@code default} method returning exactly {@code
 * ValidatedPrism<WireKey, DomainKey>} (wire first, domain second), the same shape a value leaf
 * takes. Either side may be declared alone: a key leaf without a value leaf converts the keys and
 * copies the values, and a value leaf alone is the plain {@code Map} lifting that needs no
 * annotation.
 *
 * <p>A failing key locates by the <em>source</em> key's rendering, so the location names what the
 * caller sent rather than what it parsed to; a failing value locates there too, meaning an entry
 * wrong on both sides reports both reasons at that one place. Two source keys that parse to equal
 * domain keys are a located failure, not a silent collapse: the discarded entry would take its
 * value with it.
 *
 * <p>Without a key leaf a {@code Map} pair's key types must match exactly — keys pass through by
 * identity, and mismatched ones are a compile error that offers this annotation as the fix.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface MapKey {
  /**
   * The domain {@code Map} component whose keys this leaf converts.
   *
   * @return the domain component name
   */
  String value();
}
