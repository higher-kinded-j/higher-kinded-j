// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.prisms;

import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/prisms.html">Prisms</a> page. The page
 * {@code {{#include}}}s the anchored region, and {@code PrismsBookTest} holds the claims the page
 * makes about this code.
 */
public final class PrismsBook {

  /** The three results the page's Step 2 shows. */
  record CoreResults(
      Optional<JsonString> result1, Optional<JsonString> result2, JsonValue result3) {}

  private PrismsBook() {}

  static CoreResults coreOperations() {
    // ANCHOR: core_operations
    Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();

    // --- Using getOptional (the safe "cast") ---
    Optional<JsonString> result1 = jsonStringPrism.getOptional(new JsonString("hello"));
    // -> Optional[JsonString[value=hello]]

    Optional<JsonString> result2 = jsonStringPrism.getOptional(new JsonNumber(123));
    // -> Optional.empty

    // --- Using build (construct the sum type from a part) ---
    JsonValue result3 = jsonStringPrism.build(new JsonString("world"));
    // -> JsonString[value=world], typed as a JsonValue
    // ANCHOR_END: core_operations
    return new CoreResults(result1, result2, result3);
  }
}

@GeneratePrisms
sealed interface JsonValue {}

@GenerateLenses
record JsonString(String value) implements JsonValue {}

record JsonNumber(double value) implements JsonValue {}

record JsonBoolean(boolean value) implements JsonValue {}

@GenerateLenses
record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}
