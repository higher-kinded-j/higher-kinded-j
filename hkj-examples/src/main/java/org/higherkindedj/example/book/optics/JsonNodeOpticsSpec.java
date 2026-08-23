// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.InstanceOf;
import org.higherkindedj.optics.annotations.OpticsSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.NumericNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * The spec interface shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/optics_spec_interfaces.html">Taming JSON with
 * Optics</a> page. The page {@code {{#include}}}s the anchored region, so it cannot drift from the
 * API.
 *
 * <p>The generated class drops the {@code Spec} suffix, so this declaration produces {@code
 * JsonNodeOptics} with one static prism per abstract method.
 */
// ANCHOR: spec
@ImportOptics
public interface JsonNodeOpticsSpec extends OpticsSpec<JsonNode> {

  @InstanceOf(ObjectNode.class)
  Prism<JsonNode, ObjectNode> object();

  @InstanceOf(ArrayNode.class)
  Prism<JsonNode, ArrayNode> array();

  @InstanceOf(StringNode.class)
  Prism<JsonNode, StringNode> text();

  @InstanceOf(NumericNode.class)
  Prism<JsonNode, NumericNode> numeric();

  @InstanceOf(BooleanNode.class)
  Prism<JsonNode, BooleanNode> bool();
}
// ANCHOR_END: spec
