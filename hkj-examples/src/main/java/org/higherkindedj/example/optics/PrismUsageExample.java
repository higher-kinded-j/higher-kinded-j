// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdentityMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A runnable example demonstrating how to use and compose Prisms to safely access and update data
 * within nested sum types (sealed interfaces).
 */
public class PrismUsageExample {

  // 1. Define a nested data model with sum types.
  // Here, `JsonValue` is a sum type representing different kinds of JSON values.
  @GeneratePrisms
  public sealed interface JsonValue {}

  public record JsonString(String value) implements JsonValue {}

  public record JsonNumber(double value) implements JsonValue {}

  @GenerateLenses
  public record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}

  /** Helper method to create a Traversal that focuses on a map value by its key. */
  private static Traversal<Map<String, JsonValue>, JsonValue> mapValue(String key) {
    return new Traversal<>() {
      @Override
      public <F> Kind<F, Map<String, JsonValue>> modifyF(
          Function<JsonValue, Kind<F, JsonValue>> f,
          Map<String, JsonValue> source,
          Applicative<F> applicative) {

        JsonValue currentValue = source.get(key);
        if (currentValue == null) {
          // If the key doesn't exist, do nothing.
          return applicative.of(source);
        }

        // Apply the function to the existing value to get the new value in context.
        Kind<F, JsonValue> newValueF = f.apply(currentValue);

        // Map the result back into the map structure.
        return applicative.map(
            newValue -> {
              Map<String, JsonValue> newMap = new HashMap<>(source);
              newMap.put(key, newValue);
              return newMap;
            },
            newValueF);
      }
    };
  }

  public static void main(String[] args) {

    // 2. Create an initial, nested JSON-like structure.
    var data =
        new JsonObject(
            Map.of(
                "user",
                new JsonObject(
                    Map.of(
                        "name", new JsonString("Alice"),
                        "id", new JsonNumber(123))),
                "status",
                new JsonString("active")));

    System.out.println("Original Data: " + data);
    System.out.println("------------------------------------------");

    // 3. Compose Prisms and Lenses to create a deep, safe focus.
    Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
    Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
    Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();

    // The setter lambda now correctly takes two arguments, satisfying the BiFunction.
    Lens<JsonString, String> jsonStringValueLens =
        Lens.of(JsonString::value, (jsonString, s) -> new JsonString(s));

    // Compose the optics to create the full path.
    Traversal<JsonObject, String> userToJsonName =
        fieldsLens
            .asTraversal() // Start with a Traversal
            .andThen(mapValue("user"))
            .andThen(jsonObjectPrism.asTraversal()) // Convert Prism to Traversal
            .andThen(fieldsLens.asTraversal()) // Convert Lens to Traversal
            .andThen(mapValue("name"))
            .andThen(jsonStringPrism.asTraversal()) // Convert Prism to Traversal
            .andThen(jsonStringValueLens.asTraversal()); // Convert Lens to Traversal

    // 4. Use the composed traversal to perform an update.
    var updatedData =
        IdKindHelper.ID
            .narrow(
                userToJsonName.modifyF(
                    name -> Id.of(name.toUpperCase()), data, IdentityMonad.instance()))
            .value();

    System.out.println("After `modify`:  " + updatedData);
    System.out.println("Original is unchanged: " + data);
    System.out.println("------------------------------------------");

    // --- Scenario: Path does not match ---
    Traversal<JsonObject, String> userToJsonIdString =
        fieldsLens
            .asTraversal()
            .andThen(mapValue("user"))
            .andThen(jsonObjectPrism.asTraversal())
            .andThen(fieldsLens.asTraversal())
            .andThen(mapValue("id"))
            .andThen(jsonStringPrism.asTraversal()) // This will not match a JsonNumber
            .andThen(jsonStringValueLens.asTraversal());

    // The operation does nothing because the `jsonStringPrism` fails to match.
    var notUpdatedData =
        IdKindHelper.ID
            .narrow(
                userToJsonIdString.modifyF(
                    id -> Id.of("SHOULD_NOT_UPDATE"), data, IdentityMonad.instance()))
            .value();
    System.out.println("After trying to modify a non-matching path: " + notUpdatedData);
    System.out.println("The data remains unchanged: " + (data.equals(notUpdatedData)));
  }
}
