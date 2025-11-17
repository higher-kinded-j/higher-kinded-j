# Ixed Type Class: Safe Indexed Access

## _Zero-or-One Element Traversals_

~~~admonish info title="What You'll Learn"
- How to safely access and update existing elements in indexed structures
- Understanding the `Traversal<S, A>` pattern for partial access
- Factory methods: `mapIx()`, `listIx()`, `fromAt()`
- The key difference between Ixed (read/update only) and At (full CRUD)
- Composing Ixed with Lenses for safe deep access into nested collections
- How Ixed is built on At internally using `Prisms.some()`
- When to use Ixed vs At vs direct collection operations
- Building safe, structure-preserving data pipelines
~~~

~~~admonish title="Example Code"
[IxedUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IxedUsageExample.java)
~~~

In the previous guide on [At](at.md), we explored how to perform full CRUD operations on indexed structures—inserting new entries, deleting existing ones, and updating values. But what if you only need to *read* and *update* elements that already exist, without the ability to insert or delete? What if you want operations that automatically become no-ops when an index is absent, preserving the structure unchanged?

This is where **`Ixed`** shines. It provides a `Traversal` that focuses on zero or one element at a given index—perfect for safe, partial access patterns where you want to modify existing data without changing the structure's shape.

---

## The Scenario: Safe Configuration Reading

Consider a configuration system where you need to read and update existing settings, but deliberately want to avoid accidentally creating new entries:

**The Data Model:**

```java
public record ServerConfig(
    String serverName,
    Map<String, String> environment,
    Map<String, Integer> ports,
    List<String> allowedHosts
) {}

public record DatabaseConfig(
    String connectionString,
    Map<String, String> poolSettings,
    List<String> replicaHosts
) {}

public record ApplicationSettings(
    ServerConfig server,
    DatabaseConfig database,
    Map<String, String> featureToggles
) {}
```

**Common Operations:**
* "Read the current database pool size setting"
* "Update the max connections if it exists"
* "Modify the port number for an existing service"
* "Safely access the nth replica host if it exists"
* "Never accidentally create new configuration keys"

The key requirement here is **safety**: you want to interact with existing data without risk of accidentally expanding the structure. If a key doesn't exist, the operation should simply do nothing rather than insert a new entry.

---

## Think of Ixed Like...

* **A read-only database view with UPDATE privileges** 🔍: You can SELECT and UPDATE existing rows, but cannot INSERT new ones or DELETE existing ones
* **A safe array accessor** 🛡️: Returns nothing for out-of-bounds indices instead of throwing exceptions
* **A peephole in a door** 👁️: You can see what's there and modify it if present, but you can't add or remove anything
* **A library card catalogue lookup** 📚: You can find and update existing book entries, but adding new books requires different permissions
* **A partial function** 🎯: Operates only where defined, silently ignores undefined inputs

---

## Ixed vs At vs Traversal vs Prism: Understanding the Relationships

| Aspect | Ixed | At | Traversal | Prism |
|--------|------|-----|-----------|-------|
| **Focus** | Zero or one element at index | Optional presence at index | Zero or more elements | Zero or one variant case |
| **Can insert?** | ❌ No | ✅ Yes | ❌ No | ✅ Yes (via `build`) |
| **Can delete?** | ❌ No | ✅ Yes | ❌ No | ❌ No |
| **Core operation** | `Traversal<S, A>` | `Lens<S, Optional<A>>` | `modifyF(f, s, app)` | `getOptional`, `build` |
| **Returns** | Traversal (0-or-1 focus) | Lens to Optional | Modified structure | Optional value |
| **Use case** | Safe partial access | Map/List CRUD | Bulk modifications | Sum type handling |
| **Intent** | "Access if exists" | "Manage entry at index" | "Transform all elements" | "Match this case" |
| **Structure change** | ❌ Never changes | ✅ Can change size | ❌ Preserves count | ✅ Can change type |

**Key Insight**: `Ixed` is actually built *on top of* `At`. Internally, it composes `At.at(index)` with `Prisms.some()` to unwrap the Optional layer. This means `Ixed` inherits the precise boundary behaviour of `At` whilst removing the ability to insert or delete entries.

```java
// Ixed is conceptually:
// at.at(index).asTraversal().andThen(Prisms.some().asTraversal())

// At gives:     Lens<S, Optional<A>>
// Prisms.some() gives: Prism<Optional<A>, A>
// Composed:     Traversal<S, A> focusing on 0-or-1 elements
```

---

## A Step-by-Step Walkthrough

### Step 1: Creating Ixed Instances

Like `At`, `Ixed` instances are created using factory methods from `IxedInstances`:

```java
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.ixed.IxedInstances;

// Ixed instance for Map<String, Integer>
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Ixed instance for List<String>
Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();

// Create Ixed from any At instance
At<Map<String, String>, String, String> customAt = AtInstances.mapAt();
Ixed<Map<String, String>, String, String> customIx = IxedInstances.fromAt(customAt);
```

Each factory method returns an `Ixed` instance parameterised by:
* `S` – The structure type (e.g., `Map<String, Integer>`)
* `I` – The index type (e.g., `String` for maps, `Integer` for lists)
* `A` – The element type (e.g., `Integer`)

### Step 2: Safe Read Operations

`Ixed` provides safe reading that returns `Optional.empty()` for missing indices:

```java
Map<String, Integer> ports = new HashMap<>();
ports.put("http", 8080);
ports.put("https", 8443);

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Read existing key
Optional<Integer> httpPort = IxedInstances.get(mapIx, "http", ports);
// Result: Optional[8080]

// Read missing key - no exception, just empty
Optional<Integer> ftpPort = IxedInstances.get(mapIx, "ftp", ports);
// Result: Optional.empty()

// Check existence
boolean hasHttp = IxedInstances.contains(mapIx, "http", ports);
// Result: true

boolean hasFtp = IxedInstances.contains(mapIx, "ftp", ports);
// Result: false
```

Compare this to direct map access which might return `null` or require explicit containment checks.

### Step 3: Update Operations (No Insertion!)

The crucial difference from `At` is that `update` only modifies *existing* entries:

```java
Map<String, Integer> ports = new HashMap<>();
ports.put("http", 8080);
ports.put("https", 8443);

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Update existing key - works as expected
Map<String, Integer> updatedPorts = IxedInstances.update(mapIx, "http", 9000, ports);
// Result: {http=9000, https=8443}

// Attempt to "update" non-existent key - NO-OP!
Map<String, Integer> samePorts = IxedInstances.update(mapIx, "ftp", 21, ports);
// Result: {http=8080, https=8443} - NO ftp key added!

// Original unchanged (immutability)
System.out.println(ports); // {http=8080, https=8443}
```

**This is the defining characteristic of Ixed**: it will never change the structure's shape. If an index doesn't exist, operations silently become no-ops.

### Step 4: Functional Modification

Apply functions to existing elements only:

```java
Map<String, Integer> scores = new HashMap<>();
scores.put("alice", 100);
scores.put("bob", 85);

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Modify existing value
Map<String, Integer> bonusAlice = IxedInstances.modify(mapIx, "alice", x -> x + 10, scores);
// Result: {alice=110, bob=85}

// Modify non-existent key - no-op
Map<String, Integer> unchanged = IxedInstances.modify(mapIx, "charlie", x -> x + 10, scores);
// Result: {alice=100, bob=85} - no charlie key created
```

This pattern is excellent for operations like "increment if exists" or "apply transformation to known entries".

### Step 5: Composition with Other Optics

`Ixed` composes naturally with Lenses for deep, safe access:

```java
record Config(Map<String, Integer> settings) {}

Lens<Config, Map<String, Integer>> settingsLens =
    Lens.of(Config::settings, (c, s) -> new Config(s));

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Compose: Config → Map<String, Integer> → Integer (0-or-1)
Traversal<Config, Integer> maxConnectionsTraversal =
    settingsLens.asTraversal().andThen(mapIx.ix("maxConnections"));

Config config = new Config(new HashMap<>(Map.of("maxConnections", 100, "timeout", 30)));

// Safe access through composed traversal
List<Integer> values = Traversals.getAll(maxConnectionsTraversal, config);
// Result: [100]

// Safe modification through composed traversal
Config updated = Traversals.modify(maxConnectionsTraversal, x -> x * 2, config);
// Result: Config[settings={maxConnections=200, timeout=30}]

// Missing key = empty focus, modification is no-op
Traversal<Config, Integer> missingTraversal =
    settingsLens.asTraversal().andThen(mapIx.ix("nonexistent"));

Config unchanged = Traversals.modify(missingTraversal, x -> x + 1, config);
// Result: Config unchanged, no "nonexistent" key added
```

This composition gives you type-safe, deep access that automatically handles missing intermediate keys.

---

## List Operations: Safe Indexed Access

`Ixed` for lists provides bounds-safe operations:

### Safe Element Access

```java
Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();

List<String> items = new ArrayList<>(List.of("apple", "banana", "cherry"));

// Access valid index
Optional<String> second = IxedInstances.get(listIx, 1, items);
// Result: Optional["banana"]

// Access out-of-bounds - no exception!
Optional<String> tenth = IxedInstances.get(listIx, 10, items);
// Result: Optional.empty()

// Negative index - safely handled
Optional<String> negative = IxedInstances.get(listIx, -1, items);
// Result: Optional.empty()
```

### Safe Element Update

```java
// Update existing index
List<String> updated = IxedInstances.update(listIx, 1, "BANANA", items);
// Result: ["apple", "BANANA", "cherry"]

// Update out-of-bounds - no-op, no exception
List<String> unchanged = IxedInstances.update(listIx, 10, "grape", items);
// Result: ["apple", "banana", "cherry"] - no element added!

// Modify with function
List<String> uppercased = IxedInstances.modify(listIx, 0, String::toUpperCase, items);
// Result: ["APPLE", "banana", "cherry"]

// Original always unchanged
System.out.println(items); // ["apple", "banana", "cherry"]
```

**Important Contrast with At**: `At.insertOrUpdate()` on a list will throw `IndexOutOfBoundsException` for invalid indices (unless using padding), whilst `IxedInstances.update()` simply returns the list unchanged.

---

## When to Use Ixed vs Other Approaches

### ✅ Use `Ixed` When:

* **You want safe partial access**: Operations that become no-ops on missing indices
* **Structure preservation is critical**: You must not accidentally add or remove entries
* **You're reading configuration files**: Accessing known keys without creating defaults
* **You need composable traversals**: Building deep access paths that handle missing intermediates
* **You want to avoid exceptions**: Out-of-bounds list access should be safe, not throw
* **Immutability matters**: All operations return new structures

### ❌ Avoid `Ixed` When:

* **You need to insert new entries**: Use `At.insertOrUpdate()` instead
* **You need to delete entries**: Use `At.remove()` instead
* **You want to set defaults for missing keys**: Use `At` with `Optional.of(defaultValue)`
* **You need bulk operations**: Use `Traversal` for all-element modifications
* **Performance is critical**: Direct collection access may be faster (measure first!)

### Ixed vs At: Choosing the Right Tool

```java
// Scenario: Update user's email if they exist, do nothing if they don't
Map<String, String> users = new HashMap<>(Map.of("alice", "alice@example.com"));

// With At - DANGER: Might accidentally create user!
At<Map<String, String>, String, String> at = AtInstances.mapAt();
Map<String, String> result1 = at.insertOrUpdate("bob", "bob@example.com", users);
// Result: {alice=alice@example.com, bob=bob@example.com} - Bob added!

// With Ixed - SAFE: Will not create if missing
Ixed<Map<String, String>, String, String> ix = IxedInstances.mapIx();
Map<String, String> result2 = IxedInstances.update(ix, "bob", "bob@example.com", users);
// Result: {alice=alice@example.com} - No Bob, as intended!
```

Use **At** when you explicitly want CRUD semantics; use **Ixed** when you want read/update-only with automatic no-ops for missing indices.

---

## Common Pitfalls

### ❌ Don't: Expect Ixed to insert new entries

```java
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
Map<String, Integer> empty = new HashMap<>();

Map<String, Integer> result = IxedInstances.update(mapIx, "key", 100, empty);
// Result: {} - STILL EMPTY! No insertion occurred.

// If you need insertion, use At instead:
At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
Map<String, Integer> withKey = mapAt.insertOrUpdate("key", 100, empty);
// Result: {key=100}
```

### ✅ Do: Use Ixed for safe, non-inserting updates

```java
// Perfect for updating known configuration keys
Map<String, String> config = new HashMap<>(Map.of("theme", "dark", "language", "en"));

Ixed<Map<String, String>, String, String> ix = IxedInstances.mapIx();

// Only updates keys that exist
Map<String, String> updated = IxedInstances.update(ix, "theme", "light", config);
// Result: {theme=light, language=en}

// Typo in key name? No problem - just a no-op
Map<String, String> unchanged = IxedInstances.update(ix, "tehme", "light", config); // typo!
// Result: {theme=dark, language=en} - no new key created
```

---

### ❌ Don't: Assume update failure means an error

```java
Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();
List<String> items = new ArrayList<>(List.of("a", "b", "c"));

List<String> result = IxedInstances.update(listIx, 10, "z", items);
// Result is the same list - but this is SUCCESS, not failure!
// The operation correctly did nothing because index 10 doesn't exist.
```

### ✅ Do: Check for existence first if you need to know

```java
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
Map<String, Integer> scores = new HashMap<>(Map.of("alice", 100));

// If you need to know whether update will succeed:
if (IxedInstances.contains(mapIx, "bob", scores)) {
    Map<String, Integer> updated = IxedInstances.update(mapIx, "bob", 90, scores);
    System.out.println("Updated Bob's score");
} else {
    System.out.println("Bob not found - consider using At to insert");
}
```

---

### ❌ Don't: Forget that Ixed inherits At's null value limitations

```java
Map<String, Integer> map = new HashMap<>();
map.put("nullValue", null);

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
Optional<Integer> result = IxedInstances.get(mapIx, "nullValue", map);
// Result: Optional.empty() - NOT Optional.of(null)!

// Java's Optional cannot hold null values
// This is inherited from the underlying At implementation
```

### ✅ Do: Avoid null values in collections

```java
// Use sentinel values or wrapper types if you need to distinguish null from absent
Map<String, Optional<Integer>> map = new HashMap<>();
map.put("maybeNull", Optional.empty()); // Explicitly absent value
map.put("hasValue", Optional.of(42));   // Present value

// Or use At directly if you need to distinguish presence from null
At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();
// at.contains("key", map) checks key presence, not value
```

---

## Performance Considerations

### Immutability Overhead

Like `At`, all `Ixed` operations create new collection instances:

```java
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Each operation creates a new HashMap copy - O(n)
Map<String, Integer> step1 = IxedInstances.update(mapIx, "a", 1, original);   // Copy
Map<String, Integer> step2 = IxedInstances.modify(mapIx, "b", x -> x + 1, step1); // Copy
Map<String, Integer> step3 = IxedInstances.update(mapIx, "c", 3, step2);     // Copy
```

**Best Practice**: Batch modifications when possible, or accept the immutability overhead for correctness:

```java
// For multiple updates, consider direct immutable construction
Map<String, Integer> result = new HashMap<>(original);
result.put("a", 1);  // Mutable during construction
result.compute("b", (k, v) -> v != null ? v + 1 : v);
result.put("c", 3);
// Now use Ixed for subsequent safe operations
```

### Composition Overhead

Composed traversals have minimal overhead since they're just function compositions:

```java
// Composition is cheap - just wraps functions
Traversal<Config, Integer> deep = lens.asTraversal().andThen(mapIx.ix("key"));

// The overhead is in the actual modification, not the composition
Config result = Traversals.modify(deep, x -> x + 1, config);
```

### Compared to At

`Ixed` has essentially the same performance characteristics as `At` since it's built on top of it. The additional Prism composition adds negligible overhead.

---

## Real-World Example: Safe Feature Toggle Reader

Consider a system where feature toggles are read from external configuration but should never be accidentally created:

```java
public class SafeFeatureReader {

    private final Ixed<Map<String, Boolean>, String, Boolean> featureIx =
        IxedInstances.mapIx();
    private final Map<String, Boolean> features;

    public SafeFeatureReader(Map<String, Boolean> initialFeatures) {
        // Create immutable snapshot
        this.features = new HashMap<>(initialFeatures);
    }

    public boolean isEnabled(String featureName) {
        // Safe read - returns false for unknown features
        return IxedInstances.get(featureIx, featureName, features).orElse(false);
    }

    public boolean isKnownFeature(String featureName) {
        return IxedInstances.contains(featureIx, featureName, features);
    }

    public Map<String, Boolean> withFeatureUpdated(String featureName, boolean enabled) {
        // Safe update - only modifies existing features
        // Will NOT add new features even if called with unknown name
        return IxedInstances.update(featureIx, featureName, enabled, features);
    }

    public Map<String, Boolean> withFeatureToggled(String featureName) {
        // Safe toggle - flips value if exists, no-op if missing
        return IxedInstances.modify(featureIx, featureName, current -> !current, features);
    }

    public Set<String> getKnownFeatures() {
        return Collections.unmodifiableSet(features.keySet());
    }
}

// Usage
Map<String, Boolean> config = Map.of(
    "dark_mode", true,
    "new_dashboard", false,
    "beta_features", true
);

SafeFeatureReader reader = new SafeFeatureReader(config);

// Safe reads
System.out.println(reader.isEnabled("dark_mode"));      // true
System.out.println(reader.isEnabled("unknown"));        // false (default)
System.out.println(reader.isKnownFeature("unknown"));   // false

// Safe updates - won't create new features
Map<String, Boolean> updated = reader.withFeatureUpdated("new_dashboard", true);
// Result: {dark_mode=true, new_dashboard=true, beta_features=true}

Map<String, Boolean> unchanged = reader.withFeatureUpdated("typo_feature", true);
// Result: {dark_mode=true, new_dashboard=false, beta_features=true}
// No "typo_feature" added!

// Safe toggle
Map<String, Boolean> toggled = reader.withFeatureToggled("beta_features");
// Result: {dark_mode=true, new_dashboard=false, beta_features=false}
```

This pattern ensures configuration integrity—you can never accidentally pollute your feature flags with typos or unknown keys.

---

## Complete, Runnable Example

Here's a comprehensive example demonstrating all major Ixed features:

```java
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.ixed.IxedInstances;
import org.higherkindedj.optics.util.Traversals;

public class IxedUsageExample {

    @GenerateLenses
    public record ServerConfig(
        String name,
        Map<String, Integer> ports,
        Map<String, String> environment,
        List<String> allowedOrigins
    ) {}

    public static void main(String[] args) {
        System.out.println("=== Ixed Type Class Usage Examples ===\n");

        // 1. Basic Map Operations - Safe Access Only
        System.out.println("--- Map Safe Access (No Insertion) ---");

        Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
        Map<String, Integer> ports = new HashMap<>(Map.of("http", 8080, "https", 8443));

        System.out.println("Initial ports: " + ports);

        // Safe read
        System.out.println("HTTP port: " + IxedInstances.get(mapIx, "http", ports));
        System.out.println("FTP port (missing): " + IxedInstances.get(mapIx, "ftp", ports));

        // Safe update - only existing keys
        Map<String, Integer> updatedPorts = IxedInstances.update(mapIx, "http", 9000, ports);
        System.out.println("After update 'http': " + updatedPorts);

        // Attempted update of non-existent key - NO-OP!
        Map<String, Integer> samePorts = IxedInstances.update(mapIx, "ftp", 21, ports);
        System.out.println("After 'update' non-existent 'ftp': " + samePorts);
        System.out.println("FTP was NOT added (Ixed doesn't insert)");

        // Modify with function
        Map<String, Integer> doubled = IxedInstances.modify(mapIx, "https", x -> x * 2, ports);
        System.out.println("After doubling 'https': " + doubled);

        System.out.println("Original unchanged: " + ports);
        System.out.println();

        // 2. Contrast with At (CRUD)
        System.out.println("--- Ixed vs At: Insertion Behaviour ---");

        At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

        Map<String, Integer> empty = new HashMap<>();

        // At CAN insert
        Map<String, Integer> withNew = mapAt.insertOrUpdate("newKey", 42, empty);
        System.out.println("At.insertOrUpdate on empty map: " + withNew);

        // Ixed CANNOT insert
        Map<String, Integer> stillEmpty = IxedInstances.update(mapIx, "newKey", 42, empty);
        System.out.println("Ixed.update on empty map: " + stillEmpty);
        System.out.println("Ixed preserves structure - no insertion occurred");
        System.out.println();

        // 3. List Safe Indexed Access
        System.out.println("--- List Safe Indexed Access ---");

        Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();
        List<String> origins = new ArrayList<>(List.of("localhost", "example.com", "api.example.com"));

        System.out.println("Initial origins: " + origins);

        // Safe bounds checking
        System.out.println("Index 1: " + IxedInstances.get(listIx, 1, origins));
        System.out.println("Index 10 (out of bounds): " + IxedInstances.get(listIx, 10, origins));
        System.out.println("Index -1 (negative): " + IxedInstances.get(listIx, -1, origins));

        // Safe update within bounds
        List<String> updated = IxedInstances.update(listIx, 1, "www.example.com", origins);
        System.out.println("After update index 1: " + updated);

        // Update out of bounds - no-op, no exception!
        List<String> unchanged = IxedInstances.update(listIx, 10, "invalid.com", origins);
        System.out.println("After 'update' out-of-bounds index 10: " + unchanged);
        System.out.println("No exception thrown, list unchanged");

        // Functional modification
        List<String> uppercased = IxedInstances.modify(listIx, 0, String::toUpperCase, origins);
        System.out.println("After uppercase index 0: " + uppercased);

        System.out.println("Original unchanged: " + origins);
        System.out.println();

        // 4. Composition with Lenses
        System.out.println("--- Deep Composition: Lens + Ixed ---");

        // Use generated lenses from @GenerateLenses annotation
        Lens<ServerConfig, Map<String, Integer>> portsLens = ServerConfigLenses.ports();
        Lens<ServerConfig, Map<String, String>> envLens = ServerConfigLenses.environment();

        ServerConfig config = new ServerConfig(
            "production",
            new HashMap<>(Map.of("http", 8080, "https", 8443, "ws", 8765)),
            new HashMap<>(Map.of("NODE_ENV", "production", "LOG_LEVEL", "info")),
            new ArrayList<>(List.of("*.example.com"))
        );

        System.out.println("Initial config: " + config);

        // Compose: ServerConfig → Map<String, Integer> → Integer (0-or-1)
        Ixed<Map<String, Integer>, String, Integer> portIx = IxedInstances.mapIx();
        Traversal<ServerConfig, Integer> httpPortTraversal =
            portsLens.asTraversal().andThen(portIx.ix("http"));

        // Safe access through composition
        List<Integer> httpPorts = Traversals.getAll(httpPortTraversal, config);
        System.out.println("HTTP port via traversal: " + httpPorts);

        // Safe modification through composition
        ServerConfig updatedConfig = Traversals.modify(httpPortTraversal, p -> p + 1000, config);
        System.out.println("After incrementing HTTP port: " + updatedConfig.ports());

        // Non-existent key = empty focus
        Traversal<ServerConfig, Integer> ftpPortTraversal =
            portsLens.asTraversal().andThen(portIx.ix("ftp"));

        List<Integer> ftpPorts = Traversals.getAll(ftpPortTraversal, config);
        System.out.println("FTP port (missing): " + ftpPorts);

        ServerConfig stillSameConfig = Traversals.modify(ftpPortTraversal, p -> p + 1, config);
        System.out.println("After 'modify' missing FTP: " + stillSameConfig.ports());
        System.out.println("Config unchanged - Ixed didn't insert FTP");
        System.out.println();

        // 5. Checking existence
        System.out.println("--- Existence Checking ---");

        System.out.println("Contains 'http': " + IxedInstances.contains(portIx, "http", config.ports()));
        System.out.println("Contains 'ftp': " + IxedInstances.contains(portIx, "ftp", config.ports()));

        // Pattern: Check before deciding on operation
        String keyToUpdate = "ws";
        if (IxedInstances.contains(portIx, keyToUpdate, config.ports())) {
            Map<String, Integer> newPorts = IxedInstances.update(portIx, keyToUpdate, 9999, config.ports());
            System.out.println("WebSocket port updated to 9999: " + newPorts);
        } else {
            System.out.println(keyToUpdate + " not found - would need At to insert");
        }
        System.out.println();

        // 6. Building Ixed from At
        System.out.println("--- Creating Ixed from At ---");

        At<Map<String, String>, String, String> stringMapAt = AtInstances.mapAt();
        Ixed<Map<String, String>, String, String> envIx = IxedInstances.fromAt(stringMapAt);

        Map<String, String> env = config.environment();
        System.out.println("Initial environment: " + env);

        // Use derived Ixed for safe operations
        Map<String, String> updatedEnv = IxedInstances.update(envIx, "LOG_LEVEL", "debug", env);
        System.out.println("After update LOG_LEVEL: " + updatedEnv);

        Map<String, String> unchangedEnv = IxedInstances.update(envIx, "NEW_VAR", "value", env);
        System.out.println("After 'update' non-existent NEW_VAR: " + unchangedEnv);
        System.out.println("NEW_VAR not added - Ixed from At still can't insert");

        System.out.println("\n=== All operations maintain immutability and structure ===");
    }
}
```

**Expected Output:**

```
=== Ixed Type Class Usage Examples ===

--- Map Safe Access (No Insertion) ---
Initial ports: {http=8080, https=8443}
HTTP port: Optional[8080]
FTP port (missing): Optional.empty
After update 'http': {http=9000, https=8443}
After 'update' non-existent 'ftp': {http=8080, https=8443}
FTP was NOT added (Ixed doesn't insert)
After doubling 'https': {http=8080, https=16886}
Original unchanged: {http=8080, https=8443}

--- Ixed vs At: Insertion Behaviour ---
At.insertOrUpdate on empty map: {newKey=42}
Ixed.update on empty map: {}
Ixed preserves structure - no insertion occurred

--- List Safe Indexed Access ---
Initial origins: [localhost, example.com, api.example.com]
Index 1: Optional[example.com]
Index 10 (out of bounds): Optional.empty
Index -1 (negative): Optional.empty
After update index 1: [localhost, www.example.com, api.example.com]
After 'update' out-of-bounds index 10: [localhost, example.com, api.example.com]
No exception thrown, list unchanged
After uppercase index 0: [LOCALHOST, example.com, api.example.com]
Original unchanged: [localhost, example.com, api.example.com]

--- Deep Composition: Lens + Ixed ---
Initial config: ServerConfig[name=production, ports={http=8080, https=8443, ws=8765}, environment={NODE_ENV=production, LOG_LEVEL=info}, allowedOrigins=[*.example.com]]
HTTP port via traversal: [8080]
After incrementing HTTP port: {http=9080, https=8443, ws=8765}
FTP port (missing): []
After 'modify' missing FTP: {http=8080, https=8443, ws=8765}
Config unchanged - Ixed didn't insert FTP

--- Existence Checking ---
Contains 'http': true
Contains 'ftp': false
WebSocket port updated to 9999: {http=8080, https=8443, ws=9999}

--- Creating Ixed from At ---
Initial environment: {NODE_ENV=production, LOG_LEVEL=info}
After update LOG_LEVEL: {NODE_ENV=production, LOG_LEVEL=debug}
After 'update' non-existent NEW_VAR: {NODE_ENV=production, LOG_LEVEL=info}
NEW_VAR not added - Ixed from At still can't insert

=== All operations maintain immutability and structure ===
```

---

## Further Reading

* [Haskell Lens Library - Ixed Type Class](https://hackage.haskell.org/package/lens/docs/Control-Lens-At.html#t:Ixed)
* [Optics By Example](https://leanpub.com/optics-by-example) - Comprehensive optics guide
* [At Type Class Guide](at.md) - Full CRUD operations with insert/delete
* [Traversals Guide](traversals.md) - Bulk operations on collections
* [Prisms Guide](prisms.md) - Understanding the `some()` Prism used internally

---

## Summary

The **Ixed** type class provides a powerful abstraction for safe, partial access to indexed structures:

* **Traversal to existing elements**: `ix(index)` returns `Traversal<S, A>` focusing on 0-or-1 elements
* **No insertion or deletion**: Operations become no-ops for missing indices
* **Structure preservation**: The shape of your data never changes unexpectedly
* **Built on At**: Inherits precise semantics whilst removing CRUD mutations
* **Composable**: Chains naturally with other optics for safe deep access
* **Exception-free**: Out-of-bounds access returns empty, doesn't throw

Ixed complements At by providing read/update-only semantics when you need safe partial access without the risk of accidentally modifying your data structure's shape. Use Ixed when correctness and structure preservation matter more than the ability to insert or delete entries.

---

[Previous: At Type Class](at.md) | [Next: Profunctor Optics](profunctor_optics.md)
