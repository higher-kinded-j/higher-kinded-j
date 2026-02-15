// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

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
 * Spec interface demonstrating how to generate prisms for {@code tools.jackson.databind.JsonNode}.
 *
 * <p>This is an example of using the {@code @InstanceOf} prism hint for external types that have
 * concrete subtypes. Jackson's JsonNode hierarchy uses subclasses like ArrayNode, ObjectNode,
 * StringNode, etc.
 *
 * <p>The {@code @InstanceOf} annotation specifies the target subtype class. The processor generates
 * a prism that:
 *
 * <ul>
 *   <li>Uses {@code instanceof} pattern matching to extract the subtype
 *   <li>Uses identity for the reverse direction (subtype → supertype)
 * </ul>
 *
 * <p>After annotation processing, the generated {@code JsonNodeOptics} class provides:
 *
 * <pre>{@code
 * JsonNodeOptics.array()   // Prism<JsonNode, ArrayNode>
 * JsonNodeOptics.object()  // Prism<JsonNode, ObjectNode>
 * JsonNodeOptics.text()    // Prism<JsonNode, StringNode>
 * JsonNodeOptics.numeric() // Prism<JsonNode, NumericNode>
 * JsonNodeOptics.bool()    // Prism<JsonNode, BooleanNode>
 * }</pre>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * JsonNode json = mapper.readTree("{\"items\": [1, 2, 3]}");
 *
 * // Safely extract an array (returns Optional)
 * Optional<ArrayNode> maybeArray = JsonNodeOptics.array().getOptional(json.get("items"));
 *
 * // Compose with other optics for deep access
 * Optional<ObjectNode> maybeObj = JsonNodeOptics.object().getOptional(json);
 *
 * // Build a JsonNode from a subtype
 * JsonNode arrayNode = JsonNodeOptics.array().build(mapper.createArrayNode());
 * }</pre>
 *
 * <p>Note: Jackson 3.x moved from {@code com.fasterxml.jackson} to {@code tools.jackson} package.
 *
 * @see tools.jackson.databind.JsonNode
 * @see InstanceOf
 * @see OpticsSpec
 */
@ImportOptics
public interface JsonNodeOpticsSpec extends OpticsSpec<JsonNode> {

  /**
   * Prism focusing on JsonNode instances that are arrays.
   *
   * <p>Matches when the node is an instance of {@link ArrayNode}.
   *
   * @return a prism from JsonNode to ArrayNode
   */
  @InstanceOf(ArrayNode.class)
  Prism<JsonNode, ArrayNode> array();

  /**
   * Prism focusing on JsonNode instances that are objects.
   *
   * <p>Matches when the node is an instance of {@link ObjectNode}.
   *
   * @return a prism from JsonNode to ObjectNode
   */
  @InstanceOf(ObjectNode.class)
  Prism<JsonNode, ObjectNode> object();

  /**
   * Prism focusing on JsonNode instances that are text/strings.
   *
   * <p>Matches when the node is an instance of {@link StringNode}.
   *
   * @return a prism from JsonNode to StringNode
   */
  @InstanceOf(StringNode.class)
  Prism<JsonNode, StringNode> text();

  /**
   * Prism focusing on JsonNode instances that are numeric.
   *
   * <p>Matches when the node is an instance of {@link NumericNode}, which includes IntNode,
   * LongNode, DoubleNode, etc.
   *
   * @return a prism from JsonNode to NumericNode
   */
  @InstanceOf(NumericNode.class)
  Prism<JsonNode, NumericNode> numeric();

  /**
   * Prism focusing on JsonNode instances that are booleans.
   *
   * <p>Matches when the node is an instance of {@link BooleanNode}.
   *
   * @return a prism from JsonNode to BooleanNode
   */
  @InstanceOf(BooleanNode.class)
  Prism<JsonNode, BooleanNode> bool();
}
