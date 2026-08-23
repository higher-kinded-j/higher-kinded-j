// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * The composed JSON optics shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/optics_spec_interfaces.html">Taming JSON with
 * Optics</a> page. The page {@code {{#include}}}s the anchored regions, so they cannot drift from
 * the API.
 *
 * <p>Composed optics live in an ordinary utility class rather than in default methods on the spec
 * interface: the processor copies a default method into the generated class as a stub that throws,
 * so a spec interface is for declaring primitives, not for building on them.
 */
public final class JsonPaths {

  private JsonPaths() {}

  // ANCHOR: field
  /**
   * A named field of an object node: empty when the node is not an object, or has no such field.
   */
  public static Affine<JsonNode, JsonNode> field(String name) {
    return JsonNodeOptics.object()
        .andThen(
            Affine.of(
                obj -> Optional.ofNullable(obj.get(name)),
                (obj, value) -> {
                  ObjectNode copy = obj.deepCopy();
                  copy.set(name, value);
                  return copy;
                }));
  }

  // ANCHOR_END: field

  // ANCHOR: elements
  /** Every element of an array node, rebuilt as an array node after modification. */
  public static Traversal<JsonNode, JsonNode> elements() {
    return JsonNodeOptics.array()
        .andThen(
            Traversals.<ArrayNode, JsonNode>forIterableCollecting(
                list -> {
                  ArrayNode rebuilt = JsonNodeFactory.instance.arrayNode();
                  list.forEach(rebuilt::add);
                  return rebuilt;
                }));
  }

  // ANCHOR_END: elements

  // ANCHOR: values
  /** The String behind a string node. */
  public static Affine<JsonNode, String> textValue() {
    return JsonNodeOptics.text()
        .andThen(
            Affine.of(
                node -> Optional.of(node.stringValue()),
                (node, value) -> (StringNode) JsonNodeFactory.instance.stringNode(value)));
  }

  /** The double behind a numeric node. */
  public static Affine<JsonNode, Double> numericValue() {
    return JsonNodeOptics.numeric()
        .andThen(
            Affine.of(
                node -> Optional.of(node.doubleValue()),
                (node, value) -> DoubleNode.valueOf(value)));
  }
  // ANCHOR_END: values
}
