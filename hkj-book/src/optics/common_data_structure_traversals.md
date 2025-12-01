# Common Data Structure Traversals

## _Extending Traversal Power to Optional, Map, and Tuple Types_

~~~admonish info title="What You'll Learn"
- Traversing Optional values with affine traversals (0-1 cardinality)
- Bulk transformations on Map values whilst preserving keys
- Parallel operations on Tuple2 pairs when elements share a type
- Composing structure traversals with lenses and filtered optics
- Real-world patterns: configuration management, feature flags, coordinate transforms
- When to use structure traversals vs direct access vs Stream API
~~~

~~~admonish title="Example Code"
[OptionalMapTraversalsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/OptionalMapTraversalsExample.java)

[TupleTraversalsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TupleTraversalsExample.java)
~~~

So far, we've explored traversals for collections: lists, sets, and arrays. But Java applications work with many other data structures that benefit from traversal operations: Optional values that might be empty, Map collections where we need to transform values whilst preserving keys, and Tuple pairs that represent related data.

These structures share a common need: **apply a transformation uniformly across their contents whilst maintaining structural integrity**. Higher-kinded-j's traversal combinators make this declarative, composable, and type-safe.

---

## Think of Structure Traversals Like...

* **Java Stream's `Optional.map()`**: Like `optional.map(transform)` but composable with other optics
* **Scala's for-comprehensions**: Similar to `for { x <- option } yield transform(x)`, but integrated into optic pipelines
* **Database UPDATE statements**: Like `UPDATE config SET value = transform(value)`, preserving structure
* **Functional map operations**: Like `fmap` in Haskell, lifting pure functions into wrapped contexts

The key insight: these aren't special cases; they're **traversals with specific cardinality**:
- `Optional<A>`: 0 or 1 element (affine traversal)
- `Map<K, V>`: 0 to N values, preserving keys
- `Tuple2<A, A>`: Exactly 2 elements (when same type)

---

## Three Categories of Structure Traversals

Higher-kinded-j provides factory methods in `Traversals` and dedicated utility classes:

| Structure | Method | Cardinality | Use Case |
|-----------|--------|-------------|----------|
| **Optional** | `Traversals.forOptional()` | 0 or 1 | Nullable fields, configuration values |
| **Map Values** | `Traversals.forMapValues()` | 0 to N | Bulk value transforms, preserving keys |
| **Tuple2 Pairs** | `TupleTraversals.both()` | Exactly 2 | Coordinate systems, min/max pairs |

---

## Optional Traversals: Handling Absent Values Declaratively

### The Problem with Nested Optionals

Traditional Optional handling becomes verbose when working with nested structures:

```java
@GenerateLenses
public record ServerConfig(
    String hostname,
    Optional<Integer> port,
    Optional<String> sslCertPath
) {}

@GenerateLenses
public record ApplicationConfig(
    String appName,
    Optional<ServerConfig> server
) {}

// Traditional: Nested map() calls
ApplicationConfig updated = config.server()
    .map(server -> server.port()
        .map(p -> server.withPort(Optional.of(p + 1000)))  // Offset ports
        .orElse(server)
    )
    .map(newServer -> config.withServer(Optional.of(newServer)))
    .orElse(config);
```

This pattern doesn't compose with other optics and mixes traversal logic with transformation logic.

### The Solution: `forOptional()` Traversal

The `forOptional()` method creates an **affine traversal**, focusing on 0 or 1 element.

```java
import org.higherkindedj.optics.util.Traversals;

// Create an Optional traversal
Traversal<Optional<Integer>, Integer> optTraversal = Traversals.forOptional();

// Modify the value if present
Optional<Integer> maybePort = Optional.of(8080);
Optional<Integer> offsetPort = Traversals.modify(optTraversal, p -> p + 1000, maybePort);
// Result: Optional.of(9080)

// Empty Optional remains empty
Optional<Integer> empty = Optional.empty();
Optional<Integer> stillEmpty = Traversals.modify(optTraversal, p -> p + 1000, empty);
// Result: Optional.empty()

// Extract value as a list
List<Integer> values = Traversals.getAll(optTraversal, maybePort);
// Result: [8080]  (or [] for empty)
```

### Composing with Lenses for Nested Optionals

```java
// Compose Optional traversal with lens traversal
Traversal<ApplicationConfig, Integer> serverPorts =
    ApplicationConfigLenses.server().asTraversal()
        .andThen(Traversals.forOptional())
        .andThen(ServerConfigLenses.port().asTraversal())
        .andThen(Traversals.forOptional());

// Offset all server ports in one operation
ApplicationConfig updated = Traversals.modify(serverPorts, p -> p + 1000, config);
// Works whether server and port are present or absent
```

### Real-World Example: Feature Flag Management

```java
@GenerateLenses
public record FeatureFlags(Map<String, Optional<Boolean>> flags) {}

public class FeatureFlagService {

    // Enable all flags that are currently set (respect absent flags)
    public static FeatureFlags enableAllSet(FeatureFlags config) {
        Traversal<Map<String, Optional<Boolean>>, Optional<Boolean>> allFlagValues =
            Traversals.forMapValues();

        Traversal<Map<String, Optional<Boolean>>, Boolean> presentFlags =
            allFlagValues.andThen(Traversals.forOptional());

        Map<String, Optional<Boolean>> updated = Traversals.modify(
            presentFlags,
            flag -> true,  // Enable all present flags
            config.flags()
        );

        return new FeatureFlags(updated);
    }
}
```

---

## Map Value Traversals: Bulk Transformations Preserving Keys

### The Problem with Map Streams

Transforming Map values whilst preserving keys requires ceremony:

```java
Map<String, Double> prices = Map.of(
    "widget", 10.0,
    "gadget", 25.0,
    "gizmo", 15.0
);

// Traditional: Stream + collect
Map<String, Double> inflated = prices.entrySet().stream()
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> e.getValue() * 1.1  // 10% price increase
    ));
```

This pattern doesn't compose and requires reconstructing the entire map.

### The Solution: `forMapValues()` Traversal

The `forMapValues()` method creates a traversal focusing on **all values** whilst preserving key structure.

```java
// Create a Map values traversal
Traversal<Map<String, Double>, Double> priceTraversal = Traversals.forMapValues();

// Apply 10% increase to all values
Map<String, Double> inflated = Traversals.modify(priceTraversal, price -> price * 1.1, prices);
// Result: {widget=11.0, gadget=27.5, gizmo=16.5}

// Extract all values
List<Double> allPrices = Traversals.getAll(priceTraversal, prices);
// Result: [10.0, 25.0, 15.0]

// Compose with filtered for conditional updates
Traversal<Map<String, Double>, Double> expensiveItems =
    priceTraversal.filtered(price -> price > 20.0);

Map<String, Double> discounted = Traversals.modify(
    expensiveItems,
    price -> price * 0.9,  // 10% discount on expensive items only
    prices
);
// Result: {widget=10.0, gadget=22.5, gizmo=15.0}
```

### Real-World Example: Configuration Value Normalisation

```java
@GenerateLenses
public record DatabaseConfig(
    Map<String, String> connectionProperties
) {}

public class ConfigNormaliser {

    // Trim all connection property values
    public static DatabaseConfig normaliseProperties(DatabaseConfig config) {
        Traversal<Map<String, String>, String> allPropertyValues =
            Traversals.forMapValues();

        Map<String, String> trimmed = Traversals.modify(
            allPropertyValues,
            String::trim,
            config.connectionProperties()
        );

        return new DatabaseConfig(trimmed);
    }

    // Redact sensitive values (password, token)
    public static DatabaseConfig redactSensitive(DatabaseConfig config) {
        // Use an indexed traversal to access both key and value during modification
        IndexedTraversal<String, Map<String, String>, String> allProperties =
            IndexedTraversals.forMap();

        Map<String, String> redacted = IndexedTraversals.imodify(
            allProperties,
            (key, value) -> {
                if (key.toLowerCase().contains("password") || key.toLowerCase().contains("token")) {
                    return "***REDACTED***";
                }
                return value;
            },
            config.connectionProperties()
        );

        return new DatabaseConfig(redacted);
    }
}
```

### Composing Map Traversals with Nested Structures

```java
@GenerateLenses
public record ServiceRegistry(
    Map<String, ServerConfig> services
) {}

// Transform all server ports across all services
Traversal<ServiceRegistry, Integer> allServicePorts =
    ServiceRegistryLenses.services().asTraversal()
        .andThen(Traversals.forMapValues())
        .andThen(ServerConfigLenses.port().asTraversal())
        .andThen(Traversals.forOptional());

ServiceRegistry updated = Traversals.modify(
    allServicePorts,
    port -> port + 1000,  // Offset all ports
    registry
);
```

---

## Tuple Traversals: Parallel Transformations on Pairs

### The Problem with Manual Tuple Updates

Applying the same operation to both elements of a tuple requires duplication:

```java
Tuple2<Integer, Integer> range = new Tuple2<>(10, 20);

// Traditional: Manual, repetitive
Tuple2<Integer, Integer> doubled = new Tuple2<>(
    range._1() * 2,
    range._2() * 2
);
```

When tuples represent related data (coordinates, ranges, min/max pairs), we want to express "apply this transformation to both elements" declaratively.

### The Solution: `TupleTraversals.both()`

The `both()` method creates a traversal that focuses on **both elements** when they share a type.

```java
import org.higherkindedj.optics.util.TupleTraversals;
import org.higherkindedj.hkt.tuple.Tuple2;

// Create a tuple traversal (when both elements are same type)
Traversal<Tuple2<Integer, Integer>, Integer> bothInts = TupleTraversals.both();

// Double both elements
Tuple2<Integer, Integer> range = new Tuple2<>(10, 20);
Tuple2<Integer, Integer> doubled = Traversals.modify(bothInts, x -> x * 2, range);
// Result: Tuple2(20, 40)

// Extract both elements
List<Integer> values = Traversals.getAll(bothInts, range);
// Result: [10, 20]

// Works with any shared type
Traversal<Tuple2<String, String>, String> bothStrings = TupleTraversals.both();
Tuple2<String, String> names = new Tuple2<>("alice", "bob");
Tuple2<String, String> capitalised = Traversals.modify(
    bothStrings,
    s -> s.substring(0, 1).toUpperCase() + s.substring(1),
    names
);
// Result: Tuple2("Alice", "Bob")
```

### Real-World Example: Geographic Coordinate Transformations

```java
@GenerateLenses
public record Location(
    String name,
    Tuple2<Double, Double> coordinates  // (latitude, longitude)
) {}

public class CoordinateTransforms {

    // Apply precision rounding to both lat/lon
    public static Location roundCoordinates(Location location, int decimals) {
        Traversal<Tuple2<Double, Double>, Double> bothCoords = TupleTraversals.both();

        double factor = Math.pow(10, decimals);
        Tuple2<Double, Double> rounded = Traversals.modify(
            bothCoords,
            coord -> Math.round(coord * factor) / factor,
            location.coordinates()
        );

        return new Location(location.name(), rounded);
    }

    // Offset coordinates by a fixed delta
    public static Location offsetCoordinates(Location location, double delta) {
        Traversal<Tuple2<Double, Double>, Double> bothCoords = TupleTraversals.both();

        Tuple2<Double, Double> offset = Traversals.modify(
            bothCoords,
            coord -> coord + delta,
            location.coordinates()
        );

        return new Location(location.name(), offset);
    }
}
```

### Composing with Nested Structures

```java
@GenerateLenses
public record BoundingBox(
    Tuple2<Integer, Integer> topLeft,
    Tuple2<Integer, Integer> bottomRight
) {}

// Scale coordinates in the top-left corner
Traversal<BoundingBox, Integer> topLeftCoords =
    BoundingBoxLenses.topLeft().asTraversal()
        .andThen(TupleTraversals.both());

BoundingBox scaled = Traversals.modify(topLeftCoords, coord -> coord * 2, box);

// To scale all coordinates, you would compose traversals for each field separately
// or create a custom traversal that focuses on all four coordinates
```

---

## When to Use Structure Traversals vs Other Approaches

### Use Structure Traversals When:

* **Reusable transformations** - Define once, compose with other optics
* **Nested optionals** - Avoiding `.map().map().map()` chains
* **Bulk map updates** - Transforming all values whilst preserving keys
* **Parallel tuple operations** - Same transformation to both elements
* **Immutable updates** - Structure preserved, only focused elements transformed

```java
// Perfect: Declarative, composable, reusable
Traversal<ServiceConfig, Integer> allTimeouts =
    ServiceConfigLenses.endpoints().asTraversal()
        .andThen(Traversals.forMapValues())
        .andThen(EndpointLenses.timeout().asTraversal())
        .andThen(Traversals.forOptional());

ServiceConfig increased = Traversals.modify(allTimeouts, t -> t + 1000, config);
```

### Use Direct Access When:

* **Single Optional** - Simple `map()` or `orElse()` is clearer
* **Specific Map key** - `map.get(key)` is more direct
* **Type-specific logic** - Different transformations per tuple element

```java
// Better with direct access: Single Optional
Optional<Integer> port = config.port().map(p -> p + 1000);

// Better with get: Specific key
Double price = prices.getOrDefault("widget", 0.0) * 1.1;

// Better with manual: Different operations per element
Tuple2<Integer, String> result = new Tuple2<>(
    tuple._1() * 2,        // Double the integer
    tuple._2().toUpperCase()  // Uppercase the string
);
```

### Use Stream API When:

* **Complex filtering** - Multiple conditions
* **Aggregations** - Collecting to new structures
* **No structural preservation** - Extracting or transforming to different shape

```java
// Better with streams: Complex filtering
List<Integer> values = map.values().stream()
    .filter(v -> v > 10)
    .filter(v -> v < 100)
    .collect(toList());
```

---

## Common Pitfalls

### Don't Do This:

```java
// Inefficient: Creating traversals in loops
for (Map.Entry<String, Double> entry : prices.entrySet()) {
    Traversal<Map<String, Double>, Double> values = Traversals.forMapValues();
    // Process each value... inefficient!
}

// Over-engineering: Using traversals for simple cases
Traversal<Optional<String>, String> opt = Traversals.forOptional();
String result = optional.map(s -> s.toUpperCase()).orElse("default");
// Just use: optional.map(String::toUpperCase).orElse("default")

// Type confusion: Trying to use both() with different types
Tuple2<Integer, String> mixed = new Tuple2<>(42, "hello");
// TupleTraversals.both() won't work here; types must match!
```

### Do This Instead:

```java
// Efficient: Create traversal once, apply to entire structure
Traversal<Map<String, Double>, Double> priceTraversal = Traversals.forMapValues();
Map<String, Double> updated = Traversals.modify(priceTraversal, p -> p * 1.1, prices);

// Right tool: Use direct methods for simple cases
String result = optional.map(String::toUpperCase).orElse("default");

// Correct types: Use separate lenses for mixed tuples
Lens<Tuple2<Integer, String>, Integer> first = Tuple2Lenses._1();
Lens<Tuple2<Integer, String>, String> second = Tuple2Lenses._2();
Tuple2<Integer, String> updated = new Tuple2<>(
    first.get(mixed) * 2,
    second.get(mixed).toUpperCase()
);
```

---

## Performance Notes

Structure traversals are optimised for immutability:

* **Single pass**: No intermediate collections
* **Structural sharing**: Unchanged portions reuse original references
* **No boxing overhead**: Direct map operations without streams
* **Lazy evaluation**: Short-circuits on empty optionals

**Best Practice**: Store commonly-used structure traversals as constants:

```java
public class ConfigOptics {
    // Reusable structure traversals
    public static final Traversal<Optional<String>, String> OPTIONAL_STRING =
        Traversals.forOptional();

    public static final Traversal<Map<String, Integer>, Integer> MAP_INT_VALUES =
        Traversals.forMapValues();

    public static final Traversal<Tuple2<Double, Double>, Double> COORDINATE_PAIR =
        TupleTraversals.both();

    // Domain-specific compositions
    public static final Traversal<ServerConfig, Integer> ALL_PORTS =
        ServerConfigLenses.endpoints().asTraversal()
            .andThen(MAP_INT_VALUES);
}
```

---

## Integration with Functional Java Ecosystem

### Vavr Integration

```java
import io.vavr.control.Option;

// Note: higher-kinded-j uses java.util.Optional
// For Vavr's Option, you'd need custom traversals
// But the pattern is the same:

Traversal<Option<Integer>, Integer> vavrOption = new Traversal<>() {
    @Override
    public <F> Kind<F, Option<Integer>> modifyF(
        Function<Integer, Kind<F, Integer>> f,
        Option<Integer> source,
        Applicative<F> applicative
    ) {
        return source.isDefined()
            ? applicative.map(Option::of, f.apply(source.get()))
            : applicative.of(source);
    }
};
```

### Cyclops Integration

```java
import cyclops.control.Maybe;

// Similar pattern for Cyclops Maybe
// Higher-kinded-j's patterns extend naturally to other monadic types
```

---

## Related Resources

**Functional Java Libraries**:
- [Vavr](https://www.vavr.io/) - Immutable collections with Option, Try, Either
- [Cyclops](https://github.com/aol/cyclops) - Functional control structures
- [Functional Java](https://www.functionaljava.org/) - Classic FP utilities

**Further Reading**:
- *Functional Programming in Java* by Venkat Subramaniam - Optional and immutable patterns
- *Modern Java in Action* by Raoul-Gabriel Urma - Functional data processing
- [Optics By Example](https://leanpub.com/optics-by-example) - Comprehensive optics guide (Haskell)

**Type Theory Background**:
- **Affine Traversals**: Focusing on 0 or 1 element (Optional)
- **Bitraversable**: Traversing structures with two type parameters (Tuple2, Either)
- [Profunctor Optics: Modular Data Accessors](https://arxiv.org/abs/1703.10857) - Theoretical foundation

---

## Summary

Structure traversals extend the traversal pattern to common Java data types:

| Structure | Traversal | Key Benefit |
|-----------|-----------|-------------|
| **Optional** | `forOptional()` | Null-safe composition without `.map()` chains |
| **Map Values** | `forMapValues()` | Bulk value transformation preserving keys |
| **Tuple2 Pairs** | `both()` | Parallel operations on homogeneous pairs |

These tools transform how you work with wrapped and paired values:

**Before** (Imperative):
- Manual Optional chaining
- Stream + collect for Maps
- Repetitive tuple updates

**After** (Declarative):
- Composable Optional traversals
- Direct map value transformations
- Unified tuple operations

By incorporating structure traversals into your optics toolkit, you gain the ability to express complex transformations declaratively, compose them seamlessly with other optics, and maintain type safety throughout, all whilst preserving the immutability and referential transparency that make functional programming powerful.

---

**Previous:** [String Traversals: Declarative Text Processing](string_traversals.md)
**Next:** [Indexed Optics: Position-Aware Operations](indexed_optics.md)
