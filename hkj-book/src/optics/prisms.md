# Practical Guide: Handling Sum Types with Prisms

The previous guide demonstrated how a **`Lens`** gives us a powerful, composable way to work with "has-a" relationships—a field that is guaranteed to exist within a record.

But what happens when the data doesn't have a guaranteed structure? What if a value can be one of *several different types*? This is the domain of "is-a" relationships, or **sum types**, commonly modeled in Java using `sealed interface` or `enum`.

For this, we need a different kind of optic: the **Prism**.

---

## The Scenario: Working with JSON-like Data

A `Lens` is like a sniper rifle, targeting a single, known field. A **`Prism`** is like a safe-cracker's tool; it attempts to open a specific "lock" (a particular type) and only succeeds if it has the right key.

Consider a common scenario: modeling a JSON structure. A value can be a string, a number, a boolean, or a nested object.

**The Data Model:** We can represent this with a `sealed interface`.


```java
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateLenses;
import java.util.Map;

@GeneratePrisms // Generates Prisms for each case of the sealed interface
public sealed interface JsonValue {}

public record JsonString(String value) implements JsonValue {}
public record JsonNumber(double value) implements JsonValue {}

@GenerateLenses // We can still use Lenses on the product types within the sum type
public record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}
```

**Our Goal:** We need to safely access and update the `value` of a `JsonString` that is deeply nested within another `JsonObject`. An `instanceof` and casting approach would be unsafe and verbose. A `Lens` won't work because a `JsonValue` might be a `JsonNumber`, not the `JsonObject` we expect.

---

## A Step-by-Step Walkthrough

### Step 1: Generating the Prisms

Just as with lenses, we annotate our `sealed interface` with **`@GeneratePrisms`**. This automatically creates a companion class (e.g., `JsonValuePrisms`) with a `Prism` for each permitted subtype.

### Step 2: The Core Prism Operations

A `Prism` is defined by two unique, failable operations:

* **`getOptional(source)`**: Attempts to focus on the target. It returns an `Optional` which is non-empty only if the `source` matches the Prism's specific case. This is the safe alternative to an `instanceof` check and cast.
* **`build(value)`**: Constructs the top-level type from a part. This is the reverse operation, used to wrap a value back into its specific case (e.g., taking a `String` and building a `JsonString`).

```java
Prism<JsonValue, String> jsonStringPrism = JsonValuePrisms.jsonString()
    .andThen(JsonStringLenses.value()); // We can compose a Prism with a Lens!

// --- Using getOptional ---
Optional<String> result1 = jsonStringPrism.getOptional(new JsonString("hello")); // -> Optional.of("hello")
Optional<String> result2 = jsonStringPrism.getOptional(new JsonNumber(123));   // -> Optional.empty()

// --- Using build ---
JsonValue result3 = jsonStringPrism.build("world"); // -> new JsonString("world")
```

### Step 3: Composing Prisms for Deep Access

The true power is composing `Prism`s with other optics. When a composition might fail (any time a `Prism` is involved), the result is a `Traversal`. To ensure type-safety during composition, we convert each optic in the chain to a `Traversal` using `.asTraversal()`.


```java

// Create all the optics we need
Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();
Lens<JsonString, String> jsonStringValueLens = Lens.of(JsonString::value, (js, s) -> new JsonString(s));

// A helper Traversal to access a map value by key
Traversal<Map<String, JsonValue>, JsonValue> mapValueTraversal = ...;

// The composed optic
Traversal<JsonObject, String> userToJsonName =
    fieldsLens.asTraversal()                      // Start with a Traversal
        .andThen(mapValueTraversal)
        .andThen(jsonObjectPrism.asTraversal())  // Safely dive into nested object
        .andThen(fieldsLens.asTraversal())
        .andThen(mapValueTraversal)
        .andThen(jsonStringPrism.asTraversal())  // Safely focus on the string type
        .andThen(jsonStringValueLens.asTraversal());
```

This composed `Traversal` now represents a safe, deep path that will only succeed if every step in the chain matches.

---

## Complete, Runnable Example

This example puts it all together, showing how to use the composed `Traversal` to perform a safe update.


```java
package org.higherkindedj.example.prism;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Traversals;
// ... other necessary imports for Map, HashMap, etc.

public class PrismUsageExample {

    // 1. Define the nested data model with sum types.
    @GeneratePrisms
    public sealed interface JsonValue {}
    public record JsonString(String value) implements JsonValue {}
    public record JsonNumber(double value) implements JsonValue {}
    @GenerateLenses
    public record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}


    public static void main(String[] args) {
        // 2. Create the initial nested structure.
        var data = new JsonObject(Map.of("user", new JsonObject(Map.of("name", new JsonString("Alice")))));
        System.out.println("Original Data: " + data);

        // 3. Get the generated and manually created optics.
        Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
        Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
        Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();
        Lens<JsonString, String> jsonStringValueLens = Lens.of(JsonString::value, (js, s) -> new JsonString(s));

        // 4. Compose the full traversal.
        Traversal<JsonObject, String> userToJsonName =
            fieldsLens.asTraversal()
                .andThen(mapValue("user")) // mapValue is a helper Traversal for Maps
                .andThen(jsonObjectPrism.asTraversal())
                .andThen(fieldsLens.asTraversal())
                .andThen(mapValue("name"))
                .andThen(jsonStringPrism.asTraversal())
                .andThen(jsonStringValueLens.asTraversal());

        // 5. Use the composed traversal to perform an update.
        JsonObject updatedData = Traversals.modify(userToJsonName, String::toUpperCase, data);

        System.out.println("After `modify`:  " + updatedData);
        System.out.println("Original is unchanged: " + data);
    }
    // The mapValue helper method would be defined here...
}
```

### Why Prisms are Essential

`Lens` handles the "what" and `Prism` handles the "what if." Together, they allow you to model and operate on virtually any immutable data structure you can design. Prisms are essential for:

* **Safety**: Eliminating `instanceof` checks and unsafe casts.
* **Clarity**: Expressing failable focus in a clean, functional way.
* **Composability**: Combining checks for different data shapes into a single, reusable optic.

By adding Prisms to your toolkit, you can write even more robust, declarative, and maintainable code.
