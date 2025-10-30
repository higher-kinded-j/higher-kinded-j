# Prisms: A Practical Guide

## _Working with Sum Types_

![prism.jpeg](../images/prism.jpeg)

~~~admonish info title="What You'll Learn"
- How to safely work with sum types and sealed interfaces
- Using `@GeneratePrisms` to create type-safe variant accessors
- The difference between `getOptional` and `build` operations
- Composing prisms with other optics for deep conditional access
- Handling optional data extraction without `instanceof` chains
- When to use prisms vs pattern matching vs traditional type checking
~~~

~~~admonish title="Example Code"
[PrismUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismUsageExample.java)
~~~

The previous guide demonstrated how a **`Lens`** gives us a powerful, composable way to work with "has-a" relationships—a field that is guaranteed to exist within a record.

But what happens when the data doesn't have a guaranteed structure? What if a value can be one of *several different types*? This is the domain of "is-a" relationships, or **sum types**, commonly modeled in Java using `sealed interface` or `enum`.

For this, we need a different kind of optic: the **Prism**.

---

## The Scenario: Working with JSON-like Data

A `Lens` is like a sniper rifle, targeting a single, known field. A **`Prism`** is like a safe-cracker's tool; it attempts to open a specific "lock" (a particular type) and only succeeds if it has the right key.

Consider a common scenario: modelling a JSON structure. A value can be a string, a number, a boolean, or a nested object.

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

## Think of Prisms Like...

- **A type-safe filter**: Only "lets through" values that match a specific shape
- **A safe cast**: Like `instanceof` + cast, but functional and composable
- **A conditional lens**: Works like a lens, but might return empty if the type doesn't match
- **A pattern matcher**: Focuses on one specific case of a sum type

---

## A Step-by-Step Walkthrough

### Step 1: Generating the Prisms

Just as with lenses, we annotate our `sealed interface` with **`@GeneratePrisms`**. This automatically creates a companion class (e.g., `JsonValuePrisms`) with a `Prism` for each permitted subtype.

```java
// Generated automatically:
// JsonValuePrisms.jsonString() -> Prism<JsonValue, JsonString>
// JsonValuePrisms.jsonNumber() -> Prism<JsonValue, JsonNumber>
// JsonValuePrisms.jsonBoolean() -> Prism<JsonValue, JsonBoolean>
// JsonValuePrisms.jsonObject() -> Prism<JsonValue, JsonObject>
```

### Step 2: The Core Prism Operations

A `Prism` is defined by two unique, failable operations:

* **`getOptional(source)`**: Attempts to focus on the target. It returns an `Optional` which is non-empty only if the `source` matches the Prism's specific case. This is the safe alternative to an `instanceof` check and cast.
* **`build(value)`**: Constructs the top-level type from a part. This is the reverse operation, used to wrap a value back into its specific case (e.g., taking a `String` and building a `JsonString`).

```java
Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();

// --- Using getOptional (the safe "cast") ---
Optional<JsonString> result1 = jsonStringPrism.getOptional(new JsonString("hello")); 
// -> Optional.of(JsonString("hello"))

Optional<JsonString> result2 = jsonStringPrism.getOptional(new JsonNumber(123));   
// -> Optional.empty()

// --- Using build (construct the sum type from a part) ---
JsonValue result3 = jsonStringPrism.build(new JsonString("world")); 
// -> JsonString("world") (as JsonValue)
```

### Step 3: Composing Prisms for Deep Access

The true power is composing `Prism`s with other optics. When a composition might fail (any time a `Prism` is involved), the result is a `Traversal`. To ensure type-safety during composition, we convert each optic in the chain to a `Traversal` using `.asTraversal()`.

```java

// Create all the optics we need
Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();


// The composed optic: safely navigate from JsonObject -> userLogin field -> name field -> string value
Traversal<JsonObject, String> userNameTraversal =
    fieldsLens.asTraversal()                      // JsonObject -> Map<String, JsonValue>
        .andThen(mapValue("userLogin"))                // -> JsonValue (if "userLogin" key exists)
        .andThen(jsonObjectPrism.asTraversal())   // -> JsonObject (if it's an object)
        .andThen(fieldsLens.asTraversal())        // -> Map<String, JsonValue>
         .andThen(Traversals.forMap("name"))     // -> JsonValue (if "name" key exists)
        .andThen(jsonStringPrism.asTraversal())   // -> JsonString (if it's a string)
        .andThen(JsonStringLenses.value().asTraversal()); // -> String
```

This composed `Traversal` now represents a safe, deep path that will only succeed if every step in the chain matches.

---

## When to Use Prisms vs Other Approaches

### Use Prisms When:

* **Type-safe variant handling** - Working with `sealed interface` or `enum` cases
* **Optional data extraction** - You need to safely "try" to get a specific type
* **Composable type checking** - Building reusable type-safe paths
* **Functional pattern matching** - Avoiding `instanceof` chains

```java
// Perfect for safe type extraction
Optional<String> errorMessage = DomainErrorPrisms.validationError()
    .andThen(ValidationErrorLenses.message())
    .getOptional(someError);
```

### Use Traditional instanceof When:

* **One-off type checks** - Not building reusable logic
* **Imperative control flow** - You need if/else branching
* **Performance critical paths** - Minimal abstraction overhead needed

```java
// Sometimes instanceof is clearer for simple cases
if (jsonValue instanceof JsonString jsonStr) {
    return jsonStr.value().toUpperCase();
}
```

### Use Pattern Matching When:

* **Exhaustive case handling** - You need to handle all variants
* **Complex extraction logic** - Multiple levels of pattern matching
* **Modern codebases** - Using recent Java features

```java
// Pattern matching for comprehensive handling
return switch (jsonValue) {
    case JsonString(var str) -> str.toUpperCase();
    case JsonNumber(var num) -> String.valueOf(num);
    case JsonBoolean(var bool) -> String.valueOf(bool);
    case JsonObject(var fields) -> "Object with " + fields.size() + " fields";
};
```

---

## Common Pitfalls

### ❌ Don't Do This:


```java
// Unsafe: Assuming the cast will succeed
JsonString jsonStr = (JsonString) jsonValue; // Can throw ClassCastException!

// Verbose: Repeated instanceof checks
if (jsonValue instanceof JsonObject obj1) {
    var userValue = obj1.fields().get("userLogin");
    if (userValue instanceof JsonObject obj2) {
        var nameValue = obj2.fields().get("name");
        if (nameValue instanceof JsonString str) {
            return str.value().toUpperCase();
        }
    }
}

// Inefficient: Creating prisms repeatedly
var name1 = JsonValuePrisms.jsonString().getOptional(value1);
var name2 = JsonValuePrisms.jsonString().getOptional(value2);
var name3 = JsonValuePrisms.jsonString().getOptional(value3);
```

### ✅ Do This Instead:


```java
// Safe: Use prism's getOptional
Optional<JsonString> maybeJsonStr = JsonValuePrisms.jsonString().getOptional(jsonValue);

// Composable: Build reusable safe paths
var userNamePath = JsonValuePrisms.jsonObject()
    .andThen(JsonObjectLenses.fields())
    .andThen(mapValue("userLogin"))
    .andThen(JsonValuePrisms.jsonObject())
    // ... continue composition

// Efficient: Reuse prisms and composed paths
var stringPrism = JsonValuePrisms.jsonString();
var name1 = stringPrism.getOptional(value1);
var name2 = stringPrism.getOptional(value2);
var name3 = stringPrism.getOptional(value3);
```

---

## Performance Notes

Prisms are optimised for type safety and composability:

* **Fast type checking**: Prisms use `instanceof` under the hood, which is optimised by the JVM
* **Lazy evaluation**: Composed prisms only perform checks when needed
* **Memory efficient**: No boxing or wrapper allocation for failed matches
* **Composable**: Complex type-safe paths can be built once and reused

**Best Practice**: For frequently used prism combinations, create them once and store as constants:


```java
public class JsonOptics {
    public static final Prism<JsonValue, JsonString> STRING = 
        JsonValuePrisms.jsonString();
  
    public static final Traversal<JsonValue, String> STRING_VALUE = 
        STRING.andThen(JsonStringLenses.value());
  
    public static final Traversal<JsonObject, String> USER_NAME = 
        fieldsLens.asTraversal()
            .andThen(Traversals.forMap("userLogin"))
            .andThen(JsonValuePrisms.jsonObject().asTraversal())
            .andThen(fieldsLens.asTraversal())
            .andThen(Traversals.forMap("name"))
            .andThen(STRING.asTraversal())
            .andThen(JsonStringLenses.value().asTraversal());
}
```

## Real-World Example: API Response Handling

Here's a practical example of using prisms to handle different API response types safely:


```java
@GeneratePrisms
public sealed interface ApiResponse {}
public record SuccessResponse(String data, int statusCode) implements ApiResponse {}
public record ErrorResponse(String message, String errorCode) implements ApiResponse {}
public record TimeoutResponse(long timeoutMs) implements ApiResponse {}

public class ApiHandler {
    // Reusable prisms for different response types
    private static final Prism<ApiResponse, SuccessResponse> SUCCESS = 
        ApiResponsePrisms.successResponse();
    private static final Prism<ApiResponse, ErrorResponse> ERROR = 
        ApiResponsePrisms.errorResponse();
    private static final Prism<ApiResponse, TimeoutResponse> TIMEOUT = 
        ApiResponsePrisms.timeoutResponse();
  
    public String handleResponse(ApiResponse response) {
        // Type-safe extraction and handling
        return SUCCESS.getOptional(response)
            .map(success -> "Success: " + success.data())
            .or(() -> ERROR.getOptional(response)
                .map(error -> "Error " + error.errorCode() + ": " + error.message()))
            .or(() -> TIMEOUT.getOptional(response)
                .map(timeout -> "Request timed out after " + timeout.timeoutMs() + "ms"))
            .orElse("Unknown response type");
    }
  
    // Use prisms for conditional processing
    public boolean isRetryable(ApiResponse response) {
        return ERROR.getOptional(response)
            .map(error -> "RATE_LIMIT".equals(error.errorCode()) || "TEMPORARY".equals(error.errorCode()))
            .or(() -> TIMEOUT.getOptional(response).map(t -> true))
            .orElse(false);
    }
}
```

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
import java.util.*;

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
        var userData = Map.of(
            "userLogin", new JsonObject(Map.of(
                "name", new JsonString("Alice"),
                "age", new JsonNumber(30),
                "active", new JsonBoolean(true)
            )),
            "metadata", new JsonObject(Map.of(
                "version", new JsonString("1.0")
            ))
        );
        var data = new JsonObject(userData);
    
        System.out.println("Original Data: " + data);
        System.out.println("------------------------------------------");


        // 3. Get the generated and manually created optics.
        Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
        Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
        Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();
        Lens<JsonString, String> jsonStringValueLens = Lens.of(JsonString::value, (js, s) -> new JsonString(s));
    
        // 4. Demonstrate individual prism operations
        System.out.println("--- Individual Prism Operations ---");
    
        // Safe type extraction
        JsonValue userValue = data.fields().get("userLogin");
        Optional<JsonObject> userObject = jsonObjectPrism.getOptional(userValue);
        System.out.println("User object: " + userObject);
    
        // Attempting to extract wrong type
        JsonValue nameValue = ((JsonObject) userValue).fields().get("name");
        Optional<JsonNumber> nameAsNumber = JsonValuePrisms.jsonNumber().getOptional(nameValue);
        System.out.println("Name as number (should be empty): " + nameAsNumber);
    
        // Building new values
        JsonValue newString = jsonStringPrism.build(new JsonString("Bob"));
        System.out.println("Built new string: " + newString);
        System.out.println("------------------------------------------");
    
        // 5. Compose the full traversal.
        Traversal<JsonObject, String> userToJsonName =
            fieldsLens.asTraversal()
                .andThen(Traversals.forMap("userLogin")) 
                .andThen(jsonObjectPrism.asTraversal())
                .andThen(fieldsLens.asTraversal())
                .andThen(Traversals.forMap("name"))
                .andThen(jsonStringPrism.asTraversal())
                .andThen(jsonStringValueLens.asTraversal());

          // 6. Use the composed traversal to perform safe updates
        JsonObject updatedData = Traversals.modify(userNameTraversal, String::toUpperCase, data);
        System.out.println("After safe `modify`:  " + updatedData);
    
        // 7. Demonstrate that the traversal safely handles missing paths
        var dataWithoutUser = new JsonObject(Map.of("metadata", new JsonString("test")));
        JsonObject safeUpdate = Traversals.modify(userNameTraversal, String::toUpperCase, dataWithoutUser);
        System.out.println("Safe update on missing path: " + safeUpdate);
    
        System.out.println("Original is unchanged: " + data);
        System.out.println("------------------------------------------");
    
        // 8. Demonstrate error-resistant operations
        System.out.println("--- Error-Resistant Operations ---");
    
        // Get all string values safely
        List<String> allStrings = List.of(
            new JsonString("hello"),
            new JsonNumber(42),
            new JsonString("world"),
            new JsonBoolean(true)
        ).stream()
        .map(jsonStringPrism::getOptional)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(JsonString::value)
        .toList();
    
        System.out.println("Extracted strings only: " + allStrings);
    }
}
```

**Expected Output:**

```
Original Data: JsonObject[fields={userLogin=JsonObject[fields={name=JsonString[value=Alice], age=JsonNumber[value=30.0], active=JsonBoolean[value=true]}], metadata=JsonObject[fields={version=JsonString[value=1.0]}]}]
------------------------------------------
--- Individual Prism Operations ---
User object: Optional[JsonObject[fields={name=JsonString[value=Alice], age=JsonNumber[value=30.0], active=JsonBoolean[value=true]}]]
Name as number (should be empty): Optional.empty
Built new string: JsonString[value=Bob]
------------------------------------------
--- Composed Traversal Operations ---
After safe `modify`:  JsonObject[fields={userLogin=JsonObject[fields={name=JsonString[value=ALICE], age=JsonNumber[value=30.0], active=JsonBoolean[value=true]}], metadata=JsonObject[fields={version=JsonString[value=1.0]}]}]
Safe update on missing path: JsonObject[fields={metadata=JsonString[value=test]}]
Original is unchanged: JsonObject[fields={userLogin=JsonObject[fields={name=JsonString[value=Alice], age=JsonNumber[value=30.0], active=JsonBoolean[value=true]}], metadata=JsonObject[fields={version=JsonString[value=1.0]}]}]
------------------------------------------
--- Error-Resistant Operations ---
Extracted strings only: [hello, world]
```

---

## Why Prisms are Essential

`Lens` handles the "what" and `Prism` handles the "what if." Together, they allow you to model and operate on virtually any immutable data structure you can design. Prisms are essential for:

* **Safety**: Eliminating `instanceof` checks and unsafe casts.
* **Clarity**: Expressing failable focus in a clean, functional way.
* **Composability**: Combining checks for different data shapes into a single, reusable optic.
* **Maintainability**: Creating type-safe paths that won't break when data structures evolve.

By adding Prisms to your toolkit, you can write even more robust, declarative, and maintainable code that gracefully handles the complexity of real-world data structures.

---

**Previous:**[Lenses: Working with Product Types](lenses.md)
**Next:**[Isomorphisms: Data Equivalence](iso.md)
