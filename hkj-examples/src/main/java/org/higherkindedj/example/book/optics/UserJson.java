// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics;

import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Traversal;
import tools.jackson.databind.JsonNode;

/**
 * The domain-facing layer over {@link JsonApiBook}'s paths, shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/optics_spec_interfaces.html">Taming JSON with
 * Jackson</a> page. The page {@code {{#include}}}s the anchored region, so it cannot drift.
 *
 * <p>The paths are shaped like the wire format. Naming them in the language of the domain here
 * means the rest of the code never mentions {@code field("data")}: a service reads {@code
 * UserJson.emails()}, and the JSON structure is an implementation detail of one class.
 *
 * <p>{@code JsonApiBook}'s constants are package-private, so this facade sits beside them.
 */
// ANCHOR: facade
public final class UserJson {

  private UserJson() {}

  public static Traversal<JsonNode, String> emails() {
    return JsonApiBook.USER_EMAILS;
  }

  public static Traversal<JsonNode, Double> ages() {
    return JsonApiBook.USER_AGES;
  }

  public static Affine<JsonNode, Double> page() {
    return JsonApiBook.PAGE;
  }
}
// ANCHOR_END: facade
