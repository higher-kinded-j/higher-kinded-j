// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the prism should match based on a predicate method.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a prism that
 * uses a predicate method to determine if the source matches, and a getter method to extract the
 * typed value when it does.
 *
 * <p>Use this when the source type has type-checking methods like {@code isElement()}/{@code
 * asElement()} instead of using sealed subtypes or instanceof checks.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * Prism.of(
 *     source -> source.isElement() ? Optional.of(source.asElement()) : Optional.empty(),
 *     element -> element
 * )
 * }</pre>
 *
 * <p>This pattern is common in JSON libraries (e.g., Jackson's {@code JsonNode}) and XML APIs where
 * nodes can be of various types:
 *
 * <pre>{@code
 * // External class with type-checking methods
 * public class JsonNode {
 *     public boolean isArray() { ... }
 *     public ArrayNode asArray() { ... }
 *     public boolean isObject() { ... }
 *     public ObjectNode asObject() { ... }
 *     public boolean isText() { ... }
 *     public String asText() { ... }
 * }
 *
 * @ImportOptics
 * interface JsonNodeOptics extends OpticsSpec<JsonNode> {
 *
 *     @MatchWhen(predicate = "isArray", getter = "asArray")
 *     Prism<JsonNode, ArrayNode> array();
 *
 *     @MatchWhen(predicate = "isObject", getter = "asObject")
 *     Prism<JsonNode, ObjectNode> object();
 *
 *     @MatchWhen(predicate = "isText", getter = "asText")
 *     Prism<JsonNode, String> text();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see InstanceOf
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface MatchWhen {

  /**
   * The predicate method name that returns boolean.
   *
   * <p>This method should return {@code true} when the source matches the target type, {@code
   * false} otherwise.
   *
   * <p>For example, "isArray", "isObject", "isElement".
   *
   * @return the predicate method name
   */
  String predicate();

  /**
   * The getter method name to extract the typed value.
   *
   * <p>This method should return the typed value when the predicate returns {@code true}. The
   * behaviour when called with a non-matching source is undefined (typically throws or returns
   * null).
   *
   * <p>For example, "asArray", "asObject", "asElement".
   *
   * @return the getter method name
   */
  String getter();
}
