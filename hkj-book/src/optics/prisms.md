# Prisms: A Practical Guide

## _Working with Sum Types_

![Visual representation of a prism safely extracting one variant from a sum type](../images/prism.jpeg)

~~~admonish info title="What You'll Learn"
- How to safely work with sum types and sealed interfaces
- Using `@GeneratePrisms` to create type-safe variant accessors
- The difference between `getOptional` and `build` operations
- Composing prisms with other optics for deep conditional access
- Handling optional data extraction without `instanceof` chains
- When to use prisms vs pattern matching vs traditional type checking
~~~

~~~admonish title="Hands On Practice"
[Tutorial03_PrismBasics.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial03_PrismBasics.java)
~~~

~~~admonish title="Example Code"
[PrismUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismUsageExample.java)
[PrismConvenienceMethodsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismConvenienceMethodsExample.java)
[PrismsUtilityExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismsUtilityExample.java)
~~~

The previous guide demonstrated how a **`Lens`** gives us a powerful, composable way to work with "has-a" relationships: a field that is guaranteed to exist within a record.

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

#### Customising the Generated Package

By default, generated classes are placed in the same package as the annotated type. You can specify a different package using the `targetPackage` attribute:

```java
// Generated class will be placed in org.example.generated.optics
@GeneratePrisms(targetPackage = "org.example.generated.optics")
public sealed interface JsonValue {}
```

This is useful when you need to avoid name collisions or organise generated code separately.

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

The true power is composing `Prism`s with other optics. When a composition might fail (any time a `Prism` is involved), the result is an `Affine` (or `Traversal` when combining with traversals).

~~~admonish tip title="Direct Composition Methods"
higher-kinded-j provides direct composition methods that automatically return the correct type:
- `Lens.andThen(Prism)` returns `Affine`
- `Prism.andThen(Lens)` returns `Affine`
- `Prism.andThen(Prism)` returns `Prism`
- `Affine.andThen(Affine)` returns `Affine`

See [Composition Rules](composition_rules.md) for the complete reference.
~~~

```java
// Create all the optics we need
Prism<JsonValue, JsonObject> jsonObjectPrism = JsonValuePrisms.jsonObject();
Prism<JsonValue, JsonString> jsonStringPrism = JsonValuePrisms.jsonString();
Lens<JsonObject, Map<String, JsonValue>> fieldsLens = JsonObjectLenses.fields();
Lens<JsonString, String> valueLens = JsonStringLenses.value();

// Direct composition: Prism >>> Lens = Traversal
Traversal<JsonValue, String> jsonStringValue =
    jsonStringPrism.andThen(valueLens);

// The composed optic: safely navigate from JsonObject -> userLogin field -> name field -> string value
Traversal<JsonObject, String> userNameTraversal =
    fieldsLens.asTraversal()                      // JsonObject -> Map<String, JsonValue>
        .andThen(Traversals.forMap("userLogin"))  // -> JsonValue (if "userLogin" key exists)
        .andThen(jsonObjectPrism.asTraversal())   // -> JsonObject (if it's an object)
        .andThen(fieldsLens.asTraversal())        // -> Map<String, JsonValue>
        .andThen(Traversals.forMap("name"))       // -> JsonValue (if "name" key exists)
        .andThen(jsonStringValue);                // -> String (if it's a string)
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

### Don't Do This:


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

### Do This Instead:


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

## Prism Convenience Methods

### _Streamlined Operations for Common Patterns_

Whilst `getOptional()` and `build()` are the core operations, the `Prism` interface provides several convenience methods that make everyday tasks more ergonomic and expressive.

**Quick Reference:**

| Method | Purpose | Returns |
|--------|---------|---------|
| `matches(S source)` | Check if prism matches without extraction | `boolean` |
| `getOrElse(A default, S source)` | Extract value or return default | `A` |
| `mapOptional(Function<A, B> f, S source)` | Transform matched value | `Optional<B>` |
| `modify(Function<A, A> f, S source)` | Modify if matches, else return original | `S` |
| `modifyWhen(Predicate<A> p, Function<A, A> f, S source)` | Modify only when predicate satisfied | `S` |
| `setWhen(Predicate<A> p, A value, S source)` | Set only when predicate satisfied | `S` |
| `orElse(Prism<S, A> other)` | Try this prism, then fallback | `Prism<S, A>` |

### Type Checking with `matches()`

The `matches()` method provides a clean alternative to `getOptional(source).isPresent()`:

```java
Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

// Clear, declarative type checking
if (stringPrism.matches(value)) {
    // Process as string
}

// Useful in streams and filters
List<JsonValue> onlyStrings = values.stream()
    .filter(stringPrism::matches)
    .collect(Collectors.toList());
```

**Real-World Example**: Filtering polymorphic domain events:

```java
@GeneratePrisms
sealed interface DomainEvent permits UserEvent, OrderEvent, PaymentEvent {}

// Business logic: process only payment events
public void processPayments(List<DomainEvent> events) {
    Prism<DomainEvent, PaymentEvent> paymentPrism =
        DomainEventPrisms.paymentEvent();

    long paymentCount = events.stream()
        .filter(paymentPrism::matches)
        .count();

    logger.info("Processing {} payment events", paymentCount);

    events.stream()
        .filter(paymentPrism::matches)
        .map(paymentPrism::getOptional)
        .flatMap(Optional::stream)
        .forEach(this::processPayment);
}
```

### Default Values with `getOrElse()`

When you need fallback values, `getOrElse()` is more concise than `getOptional().orElse()`:

```java
Prism<ApiResponse, SuccessResponse> successPrism =
    ApiResponsePrisms.successResponse();

// Extract success data or use default
String data = successPrism.getOrElse(
    new SuccessResponse("fallback", 200),
    response
).data();

// Particularly useful for configuration
Config config = Prisms.some()
    .getOrElse(Config.DEFAULT, optionalConfig);
```

**Real-World Example**: Parsing user input with graceful degradation:

```java
@GeneratePrisms
sealed interface ParsedValue permits IntValue, StringValue, InvalidValue {}

public int parseUserQuantity(String input, int defaultQty) {
    ParsedValue parsed = parseInput(input);

    Prism<ParsedValue, IntValue> intPrism = ParsedValuePrisms.intValue();

    // Extract integer or use sensible default
    return intPrism.getOrElse(
        new IntValue(defaultQty),
        parsed
    ).value();
}

// Application settings with fallback
public DatabaseConfig getDatabaseConfig(ApplicationConfig config) {
    Prism<ConfigSource, DatabaseConfig> dbConfigPrism =
        ConfigSourcePrisms.databaseConfig();

    return dbConfigPrism.getOrElse(
        DatabaseConfig.DEFAULT_POSTGRES,
        config.source()
    );
}
```

### Transforming Matches with `mapOptional()`

The `mapOptional()` method transforms matched values without building them back into the source type:

```java
Prism<JsonValue, JsonNumber> numberPrism = JsonValuePrisms.jsonNumber();

// Extract and transform in one operation
Optional<String> formatted = numberPrism.mapOptional(
    num -> String.format("%.2f", num.value()),
    jsonValue
);

// Compose transformations
Optional<Boolean> isLarge = numberPrism.mapOptional(
    num -> num.value() > 1000,
    jsonValue
);
```

**Real-World Example**: ETL data transformation pipeline:

```java
@GeneratePrisms
sealed interface SourceData permits CsvRow, JsonObject, XmlNode {}

public List<CustomerRecord> extractCustomers(List<SourceData> sources) {
    Prism<SourceData, CsvRow> csvPrism = SourceDataPrisms.csvRow();

    return sources.stream()
        .map(source -> csvPrism.mapOptional(
            csv -> new CustomerRecord(
                csv.column("customer_id"),
                csv.column("name"),
                csv.column("email")
            ),
            source
        ))
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
}

// Extract business metrics from polymorphic events
public Optional<BigDecimal> extractRevenue(DomainEvent event) {
    Prism<DomainEvent, OrderCompleted> orderPrism =
        DomainEventPrisms.orderCompleted();

    return orderPrism.mapOptional(
        order -> order.lineItems().stream()
            .map(LineItem::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add),
        event
    );
}
```

### Simple Modifications with `modify()`

Instead of manually calling `getOptional().map(f).map(build)`, use `modify()`:

```java
Prism<JsonValue, JsonString> stringPrism = JsonValuePrisms.jsonString();

// Clean modification
JsonValue uppercased = stringPrism.modify(
    str -> new JsonString(str.value().toUpperCase()),
    jsonValue
);

// Verbose alternative
JsonValue verboseResult = stringPrism.getOptional(jsonValue)
    .map(str -> new JsonString(str.value().toUpperCase()))
    .map(stringPrism::build)
    .orElse(jsonValue);
```

If the prism doesn't match, `modify()` safely returns the original structure unchanged.

### Conditional Operations with `modifyWhen()` and `setWhen()`

These methods combine matching with predicate-based filtering:

```java
Prism<ConfigValue, StringConfig> stringConfig =
    ConfigValuePrisms.stringConfig();

// Only modify non-empty strings
ConfigValue sanitised = stringConfig.modifyWhen(
    str -> !str.value().isEmpty(),
    str -> new StringConfig(str.value().trim()),
    configValue
);

// Only update if validation passes
ConfigValue validated = stringConfig.setWhen(
    str -> str.value().length() <= 255,
    new StringConfig("validated"),
    configValue
);
```

**Real-World Example**: Business rule enforcement in order processing:

```java
@GeneratePrisms
sealed interface OrderStatus permits Draft, Submitted, Approved, Rejected {}

public class OrderProcessor {
    private static final Prism<OrderStatus, Submitted> SUBMITTED =
        OrderStatusPrisms.submitted();

    // Only approve orders above minimum value
    public OrderStatus approveIfEligible(
        OrderStatus status,
        BigDecimal orderValue,
        BigDecimal minValue
    ) {
        return SUBMITTED.setWhen(
            submitted -> orderValue.compareTo(minValue) >= 0,
            new Approved(Instant.now(), "AUTO_APPROVED"),
            status
        );
    }

    // Apply discount only to high-value draft orders
    public OrderStatus applyVipDiscount(OrderStatus status, Order order) {
        Prism<OrderStatus, Draft> draftPrism = OrderStatusPrisms.draft();

        return draftPrism.modifyWhen(
            draft -> order.totalValue().compareTo(VIP_THRESHOLD) > 0,
            draft -> draft.withDiscount(VIP_DISCOUNT_RATE),
            status
        );
    }
}
```

**Use Cases:**
- **Conditional validation**: Update only if current value meets criteria
- **Guarded transformations**: Apply changes only to valid states
- **Business rules**: Enforce constraints during updates
- **Workflow automation**: Apply state transitions based on business logic

### Fallback Matching with `orElse()`

The `orElse()` method chains prisms to try multiple matches:

```java
Prism<JsonValue, JsonNumber> intPrism = JsonValuePrisms.jsonInt();
Prism<JsonValue, JsonNumber> doublePrism = JsonValuePrisms.jsonDouble();

// Try int first, fall back to double
Prism<JsonValue, JsonNumber> anyNumber = intPrism.orElse(doublePrism);

Optional<JsonNumber> result = anyNumber.getOptional(jsonValue);
// Matches either integer or double JSON values

// Building always uses the first prism's constructor
JsonValue built = anyNumber.build(new JsonNumber(42)); // Uses intPrism.build
```

**Real-World Example**: Handling multiple error types in API responses:

```java
Prism<ApiResponse, String> errorMessage =
    ApiResponsePrisms.validationError()
        .andThen(ValidationErrorLenses.message())
        .orElse(
            ApiResponsePrisms.serverError()
                .andThen(ServerErrorLenses.message())
        );

// Extracts error message from either error type
Optional<String> message = errorMessage.getOptional(response);
```

~~~admonish tip title="When to Use Convenience Methods"
- **matches()**: Type guards, stream filters, conditional logic
- **getOrElse()**: Configuration, default values, fallback data
- **mapOptional()**: Projections, transformations without reconstruction
- **modify()**: Simple transformations of matching cases
- **modifyWhen()**: Conditional updates based on current state
- **setWhen()**: Guarded updates with validation
- **orElse()**: Handling multiple variants, fallback strategies
~~~

---

## Common Prism Patterns with the Prisms Utility

### _Ready-Made Prisms for Standard Types_

The `Prisms` utility class (in `org.higherkindedj.optics.util`) provides factory methods for common prism patterns, saving you from writing boilerplate for standard Java types.

**Quick Reference:**

| Factory Method | Type Signature | Use Case |
|----------------|----------------|----------|
| `some()` | `Prism<Optional<A>, A>` | Extract present Optional values |
| `left()` | `Prism<Either<L, R>, L>` | Focus on Left case |
| `right()` | `Prism<Either<L, R>, R>` | Focus on Right case |
| `only(A value)` | `Prism<A, Unit>` | Match specific value |
| `notNull()` | `Prism<@Nullable A, A>` | Filter null values |
| `instanceOf(Class<A>)` | `Prism<S, A>` | Safe type-based casting |
| `listHead()` | `Prism<List<A>, A>` | First element (if exists) |
| `listLast()` | `Prism<List<A>, A>` | Last element (if exists) |
| `listAt(int)` | `Prism<List<A>, A>` | Element at index (read-only) |

### Working with Optional: `Prisms.some()`

```java
import org.higherkindedj.optics.util.Prisms;

Prism<Optional<String>, String> somePrism = Prisms.some();

Optional<String> present = Optional.of("hello");
Optional<String> value = somePrism.getOptional(present); // Optional.of("hello")

Optional<String> empty = Optional.empty();
Optional<String> noMatch = somePrism.getOptional(empty); // Optional.empty()

// Useful for nested Optionals
Optional<Optional<Config>> nestedConfig = loadConfig();
Optional<Config> flattened = somePrism.getOptional(nestedConfig)
    .flatMap(Function.identity());
```

### Either Case Handling: `Prisms.left()` and `Prisms.right()`

```java
Prism<Either<String, Integer>, String> leftPrism = Prisms.left();
Prism<Either<String, Integer>, Integer> rightPrism = Prisms.right();

Either<String, Integer> error = Either.left("Failed");
Optional<String> errorMsg = leftPrism.getOptional(error); // Optional.of("Failed")
Optional<Integer> noValue = rightPrism.getOptional(error); // Optional.empty()

// Compose with lenses for deep access
record ValidationError(String code, String message) {}
Lens<ValidationError, String> messageLens = ValidationErrorLenses.message();

Prism<Either<ValidationError, Data>, String> errorMessage =
    Prisms.<ValidationError, Data>left()
        .andThen(messageLens);

Either<ValidationError, Data> result = validate(data);
Optional<String> msg = errorMessage.getOptional(result);
```

### Sentinel Values: `Prisms.only()`

Perfect for matching specific constant values:

```java
Prism<String, Unit> httpOkPrism = Prisms.only("200 OK");

// Check for specific status
if (httpOkPrism.matches(statusCode)) {
    // Handle success case
}

// Filter for specific values
List<String> onlyErrors = statusCodes.stream()
    .filter(Prisms.only("500 ERROR")::matches)
    .collect(Collectors.toList());

// Null sentinel handling
Prism<String, Unit> nullPrism = Prisms.only(null);
boolean isNull = nullPrism.matches(value);
```

### Null Safety: `Prisms.notNull()`

```java
Prism<String, String> notNullPrism = Prisms.notNull();

// Safe extraction
@Nullable String nullable = getDatabaseValue();
Optional<String> safe = notNullPrism.getOptional(nullable);

// Compose to filter null values in pipelines
Traversal<List<String>, String> nonNullStrings =
    Traversals.<String>forList()
        .andThen(Prisms.<String>notNull().asTraversal());

List<@Nullable String> mixedList = List.of("hello", null, "world", null);
List<String> filtered = Traversals.getAll(nonNullStrings, mixedList);
// Result: ["hello", "world"]
```

### Type-Safe Casting: `Prisms.instanceOf()`

Elegant alternative to `instanceof` checks in type hierarchies:

```java
sealed interface Animal permits Dog, Cat, Bird {}
record Dog(String name, String breed) implements Animal {}
record Cat(String name, int lives) implements Animal {}
record Bird(String name, boolean canFly) implements Animal {}

Prism<Animal, Dog> dogPrism = Prisms.instanceOf(Dog.class);

Animal animal = new Dog("Buddy", "Labrador");
Optional<Dog> maybeDog = dogPrism.getOptional(animal); // Optional.of(Dog(...))

// Compose with lenses for deep access
Lens<Dog, String> breedLens = DogLenses.breed();
Traversal<Animal, String> dogBreed =
    dogPrism.asTraversal().andThen(breedLens.asTraversal());

List<Animal> animals = List.of(
    new Dog("Rex", "German Shepherd"),
    new Cat("Whiskers", 9),
    new Dog("Max", "Beagle")
);

List<String> breeds = Traversals.getAll(
    Traversals.<Animal>forList().andThen(dogBreed),
    animals
);
// Result: ["German Shepherd", "Beagle"]
```

### Collection Element Access

```java
// First element (if list is non-empty)
Prism<List<String>, String> headPrism = Prisms.listHead();
List<String> names = List.of("Alice", "Bob", "Charlie");
Optional<String> first = headPrism.getOptional(names); // Optional.of("Alice")

// Last element
Prism<List<String>, String> lastPrism = Prisms.listLast();
Optional<String> last = lastPrism.getOptional(names); // Optional.of("Charlie")

// Element at specific index (read-only for queries)
Prism<List<String>, String> secondPrism = Prisms.listAt(1);
Optional<String> second = secondPrism.getOptional(names); // Optional.of("Bob")

// Safe access patterns
String firstOrDefault = headPrism.getOrElse("Unknown", names);
boolean hasList = headPrism.matches(names);
```

~~~admonish warning title="List Prism Limitations"
The `listHead()` and `listLast()` prisms have limited `build()` operations: they create singleton lists. The `listAt(int)` prism throws `UnsupportedOperationException` on `build()` since there's no meaningful way to construct a complete list from a single indexed element.

**Use these prisms for:**
- Safe element extraction
- Conditional checks (with `matches()`)
- Query operations (with `getOptional()`)

**For list modification**, use `Traversal` or `Lens` instead:
```java
// ✅ For modifications, use proper traversals
Lens<List<String>, String> firstLens = listFirstElementLens();
List<String> updated = firstLens.modify(String::toUpperCase, names);
```
~~~

### Composing Utility Prisms

The real power emerges when composing these utility prisms with your domain optics:

```java
record Config(Optional<Either<String, DatabaseSettings>> database) {}
record DatabaseSettings(String host, int port) {}

// Build a path through Optional -> Either -> Settings -> host
Prism<Config, String> databaseHost =
    ConfigLenses.database()                    // Lens<Config, Optional<Either<...>>>
        .asTraversal()
        .andThen(Prisms.some().asTraversal())  // -> Either<String, DatabaseSettings>
        .andThen(Prisms.right().asTraversal()) // -> DatabaseSettings
        .andThen(DatabaseSettingsLenses.host().asTraversal()); // -> String

Config config = loadConfig();
Optional<String> host = Traversals.getAll(databaseHost, config)
    .stream().findFirst();
```

~~~admonish tip title="Performance Considerations"
Utility prisms are lightweight and stateless; they're safe to create on-demand or cache as constants:

```java
public class AppPrisms {
    public static final Prism<Optional<User>, User> SOME_USER = Prisms.some();
    public static final Prism<Response, SuccessResponse> SUCCESS =
        Prisms.instanceOf(SuccessResponse.class);
}
```
~~~

---

## Why Prisms are Essential

`Lens` handles the "what" and `Prism` handles the "what if." Together, they allow you to model and operate on virtually any immutable data structure you can design. Prisms are essential for:

* **Safety**: Eliminating `instanceof` checks and unsafe casts.
* **Clarity**: Expressing failable focus in a clean, functional way.
* **Composability**: Combining checks for different data shapes into a single, reusable optic.
* **Maintainability**: Creating type-safe paths that won't break when data structures evolve.

By adding Prisms to your toolkit, you can write even more robust, declarative, and maintainable code that gracefully handles the complexity of real-world data structures.

~~~admonish tip title="Ready for More?"
Once you're comfortable with these prism fundamentals, explore [Advanced Prism Patterns](advanced_prism_patterns.md) for production-ready patterns including:
- Configuration management with layered prism composition
- API response handling with type-safe error recovery
- Data validation pipelines and event processing systems
- State machine implementations and plugin architectures
- Performance optimisation and testing strategies
~~~

~~~admonish tip title="For Comprehension Integration"
Prisms integrate with For comprehensions via the `match()` operation, which provides prism-based pattern matching with short-circuit semantics. When the prism match fails, the computation short-circuits using the monad's zero value (empty list, Nothing, etc.). See [For Comprehensions: Pattern Matching with match()](../functional/for_comprehension.md#pattern-matching-with-match).
~~~

~~~admonish info title="Hands-On Learning"
Practice prism basics in [Tutorial 03: Prism Basics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial03_PrismBasics.java) (9 exercises, ~12 minutes).
~~~

---

~~~admonish tip title="Further Reading"
- **Monocle**: [Scala Optics Library](https://www.optics.dev/Monocle/) - Production-ready Scala optics with extensive examples
- **Haskell Lens**: [Canonical Reference](https://hackage.haskell.org/package/lens) - The original comprehensive optics library
- **Lens Tutorial**: [A Little Lens Starter Tutorial](https://www.schoolofhaskell.com/school/to-infinity-and-beyond/pick-of-the-week/a-little-lens-starter-tutorial) - Beginner-friendly introduction
~~~

---

**Previous:** [Lenses: Working with Product Types](lenses.md)
**Next:** [Affines: Working with Optional Fields](affine.md) | [Advanced Prism Patterns](advanced_prism_patterns.md)
