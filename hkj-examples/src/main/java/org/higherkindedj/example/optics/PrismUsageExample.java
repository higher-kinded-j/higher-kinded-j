// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.Map;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating how to use and compose Prisms to safely access and update data
 * within nested sum types (sealed interfaces).
 */
public class PrismUsageExample {

  // 1. Define a nested data model with sum types.
  @GeneratePrisms
  public sealed interface JsonValue {}

  @GenerateLenses
  public record JsonString(String value) implements JsonValue {}

  public record JsonNumber(double value) implements JsonValue {}

  @GenerateLenses
  @GenerateTraversals // Generates JsonObjectTraversals.fields()
  public record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}

  public static void main(String[] args) {

    // 2. Create an initial, nested JSON-like structure.
    var data =
        new JsonObject(
            Map.of(
                "user",
                new JsonObject(Map.of("name", new JsonString("Alice"), "id", new JsonNumber(123))),
                "status",
                new JsonString("active"),
                "empty_field",
                new JsonString("")));

    System.out.println("Original Data: " + data);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 1: Using composed Prisms and Lenses for deep, specific updates
    // =======================================================================
    System.out.println("--- Scenario 1: Using Composed Traversal for Deep Updates ---");
    Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
    Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
    Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();
    Lens<JsonString, String> jsonStringValueLens = JsonStringLenses.value();

    // Compose the optics to create the full path from the root to the user's name.
    // The local `mapValue` helper has been replaced with the static `Traversal.forMap` method.
    Traversal<JsonObject, String> userToJsonName =
        fieldsLens
            .asTraversal()
            .andThen(Traversals.forMap("user"))
            .andThen(jsonObjectPrism.asTraversal())
            .andThen(fieldsLens.asTraversal())
            .andThen(Traversals.forMap("name"))
            .andThen(jsonStringPrism.asTraversal())
            .andThen(jsonStringValueLens.asTraversal());

    var updatedData =
        IdKindHelper.ID
            .narrow(
                userToJsonName.modifyF(name -> Id.of(name.toUpperCase()), data, IdMonad.instance()))
            .value();

    System.out.println("After deep `modify`:    " + updatedData);
    System.out.println("------------------------------------------");

    // =======================================================================
    // SCENARIO 2: Using the generated Traversal to operate on all elements
    // =======================================================================
    System.out.println("--- Scenario 2: Using Generated Traversal to Validate All Fields ---");

    Traversal<JsonObject, String> allTopLevelStringValues =
        JsonObjectTraversals.fields() // Traverses all values in the `fields` map
            .andThen(jsonStringPrism.asTraversal()) // Filters for strings
            .andThen(jsonStringValueLens.asTraversal()); // Gets the string content

    Function<String, Kind<ValidatedKind.Witness<String>, String>> checkNonEmpty =
        s ->
            s.isEmpty()
                ? VALIDATED.widen(Validated.invalid("A string field was empty"))
                : VALIDATED.widen(Validated.valid(s));

    Applicative<ValidatedKind.Witness<String>> applicative =
        ValidatedMonad.instance(Semigroups.string("; "));

    Kind<ValidatedKind.Witness<String>, JsonObject> validationResult =
        allTopLevelStringValues.modifyF(checkNonEmpty, data, applicative);

    System.out.println("Validation Result: " + VALIDATED.narrow(validationResult));
  }
}
