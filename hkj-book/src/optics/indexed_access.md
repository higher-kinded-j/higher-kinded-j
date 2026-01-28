# Indexed Access: At and Ixed Type Classes

## _CRUD and Safe Partial Access for Collections_

~~~admonish info title="What You'll Learn"
- How to insert, update, and delete entries in indexed structures using `At`
- How to safely access and update existing elements using `Ixed`
- The key difference: At provides full CRUD, Ixed provides read/update only
- Factory methods: `mapAt()`, `listAt()`, `mapIx()`, `listIx()`
- Composing with Lenses for deep access into nested collections
- Using `Prisms.some()` to unwrap Optional for chained modifications
- When to use At vs Ixed vs direct collection operations
~~~

~~~admonish title="Example Code"
- [AtUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/AtUsageExample.java)
- [IxedUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IxedUsageExample.java)
~~~

In previous guides, we explored **Lens** for accessing product fields and **Traversal** for operating over collections. But what happens when you need to *insert* a new entry into a map, *delete* an existing key, or *safely update* a specific list index without risking exceptions?

This is where **At** and **Ixed** fill crucial gaps:

- **At** provides full CRUD (Create, Read, Update, Delete) operations via `Lens<S, Optional<A>>`
- **Ixed** provides safe read/update-only access via `Traversal<S, A>` that never changes structure

---

## At vs Ixed: Choosing the Right Tool

| Aspect | At | Ixed |
|--------|-----|------|
| **Focus** | Optional presence at index | Zero or one existing element |
| **Can insert?** | Yes | No |
| **Can delete?** | Yes | No |
| **Core operation** | `Lens<S, Optional<A>>` | `Traversal<S, A>` |
| **Missing index** | Returns `Optional.empty()` | Focus is empty (no-op) |
| **Use case** | Map/List CRUD | Safe partial access |
| **Structure change** | Can change size | Never changes shape |

~~~admonish tip title="When to Use Which"
Use **At** when you need to add new entries or remove existing ones.

Use **Ixed** when you want safe read/update that never accidentally modifies structure: operations become no-ops for missing indices.
~~~

---

## Part 1: At – Full CRUD Operations

### Creating At Instances

```java
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.at.AtInstances;

// At instance for Map<String, Integer>
At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

// At instance for List<String>
At<List<String>, Integer, String> listAt = AtInstances.listAt();

// At instance for List with auto-padding
At<List<String>, Integer, String> paddedListAt = AtInstances.listAtWithPadding(null);
```

### CRUD Operations

#### Create / Insert

```java
Map<String, Integer> scores = new HashMap<>();
scores.put("alice", 100);

At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();

// Insert a new entry
Map<String, Integer> withBob = mapAt.insertOrUpdate("bob", 85, scores);
// Result: {alice=100, bob=85}

// Original unchanged (immutability)
System.out.println(scores); // {alice=100}
```

#### Read / Query

```java
Optional<Integer> aliceScore = mapAt.get("alice", withBob);
// Result: Optional[100]

Optional<Integer> charlieScore = mapAt.get("charlie", withBob);
// Result: Optional.empty()

boolean hasAlice = mapAt.contains("alice", withBob);
// Result: true
```

#### Update / Modify

```java
// Update existing value
Map<String, Integer> updatedScores = mapAt.insertOrUpdate("alice", 110, withBob);
// Result: {alice=110, bob=85}

// Modify with function
Map<String, Integer> bonusScores = mapAt.modify("bob", x -> x + 10, updatedScores);
// Result: {alice=110, bob=95}

// Modify non-existent key is a no-op
Map<String, Integer> unchanged = mapAt.modify("charlie", x -> x + 10, bonusScores);
// Result: {alice=110, bob=95} (no charlie key)
```

#### Delete / Remove

```java
Map<String, Integer> afterRemove = mapAt.remove("alice", bonusScores);
// Result: {bob=95}

// Remove non-existent key is a no-op
Map<String, Integer> stillSame = mapAt.remove("charlie", afterRemove);
// Result: {bob=95}
```

### The Lens to Optional Pattern

The core of At is its `at(index)` method, which returns a `Lens<S, Optional<A>>`:

```java
Lens<Map<String, Integer>, Optional<Integer>> aliceLens = mapAt.at("alice");

Map<String, Integer> scores = new HashMap<>(Map.of("alice", 100));

// Get: Returns Optional
Optional<Integer> score = aliceLens.get(scores);
// Result: Optional[100]

// Set with Optional.of(): Insert or update
Map<String, Integer> updated = aliceLens.set(Optional.of(150), scores);
// Result: {alice=150}

// Set with Optional.empty(): Delete
Map<String, Integer> deleted = aliceLens.set(Optional.empty(), scores);
// Result: {}
```

### Deep Composition with At

```java
record Config(Map<String, String> settings) {}

Lens<Config, Map<String, String>> settingsLens =
    Lens.of(Config::settings, (c, s) -> new Config(s));

At<Map<String, String>, String, String> mapAt = AtInstances.mapAt();

// Compose: Config → Map<String, String> → Optional<String>
Lens<Config, Optional<String>> debugSettingLens =
    settingsLens.andThen(mapAt.at("debug"));

Config config = new Config(new HashMap<>());

// Insert new setting through composed lens
Config withDebug = debugSettingLens.set(Optional.of("true"), config);
// Result: Config[settings={debug=true}]

// Delete setting through composed lens
Config withoutDebug = debugSettingLens.set(Optional.empty(), withDebug);
// Result: Config[settings={}]
```

---

## Part 2: Ixed – Safe Partial Access

### Creating Ixed Instances

```java
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Ixed;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.ixed.IxedInstances;

// Ixed instance for Map<String, Integer>
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Ixed instance for List<String>
Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();

// Create Ixed from any At instance
At<Map<String, String>, String, String> customAt = AtInstances.mapAt();
Ixed<Map<String, String>, String, String> customIx = IxedInstances.fromAt(customAt);
```

### Safe Read Operations

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
```

### Update Operations (No Insertion!)

The crucial difference from At: `update` only modifies *existing* entries:

```java
// Update existing key - works as expected
Map<String, Integer> updatedPorts = IxedInstances.update(mapIx, "http", 9000, ports);
// Result: {http=9000, https=8443}

// Attempt to "update" non-existent key - NO-OP!
Map<String, Integer> samePorts = IxedInstances.update(mapIx, "ftp", 21, ports);
// Result: {http=8080, https=8443} - NO ftp key added!
```

~~~admonish warning title="Key Difference"
**At** will insert new entries with `insertOrUpdate`.

**Ixed** will silently do nothing for missing keys; it never changes the structure's shape.
~~~

### Safe List Access

```java
Ixed<List<String>, Integer, String> listIx = IxedInstances.listIx();
List<String> items = new ArrayList<>(List.of("apple", "banana", "cherry"));

// Access valid index
Optional<String> second = IxedInstances.get(listIx, 1, items);
// Result: Optional["banana"]

// Access out-of-bounds - no exception!
Optional<String> tenth = IxedInstances.get(listIx, 10, items);
// Result: Optional.empty()

// Update out-of-bounds - no-op, no exception
List<String> unchanged = IxedInstances.update(listIx, 10, "grape", items);
// Result: ["apple", "banana", "cherry"] - no element added!
```

### Composition with Ixed

```java
record Config(Map<String, Integer> settings) {}

Lens<Config, Map<String, Integer>> settingsLens =
    Lens.of(Config::settings, (c, s) -> new Config(s));

Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();

// Compose: Config → Map<String, Integer> → Integer (0-or-1)
Traversal<Config, Integer> maxConnectionsTraversal =
    settingsLens.asTraversal().andThen(mapIx.ix("maxConnections"));

Config config = new Config(new HashMap<>(Map.of("maxConnections", 100)));

// Safe modification through composed traversal
Config updated = Traversals.modify(maxConnectionsTraversal, x -> x * 2, config);
// Result: Config[settings={maxConnections=200}]

// Missing key = empty focus, modification is no-op
Traversal<Config, Integer> missingTraversal =
    settingsLens.asTraversal().andThen(mapIx.ix("nonexistent"));

Config unchanged = Traversals.modify(missingTraversal, x -> x + 1, config);
// Result: Config unchanged, no "nonexistent" key added
```

---

## List-Specific Considerations

### Deletion Shifts Indices (At only)

~~~admonish warning title="Index Shifting"
When using `At.remove()` on a list, subsequent indices shift:

```java
At<List<String>, Integer, String> at = AtInstances.listAt();
List<String> items = new ArrayList<>(List.of("a", "b", "c", "d"));

List<String> step1 = at.remove(1, items); // ["a", "c", "d"]

// "d" is now at index 2, not index 3!
Optional<String> result = at.get(3, step1);
// Result: Optional.empty() - index 3 is now out of bounds
```

When removing multiple elements, iterate backwards to preserve indices.
~~~

### Bounds Checking

```java
At<List<String>, Integer, String> listAt = AtInstances.listAt();

// Update at invalid index throws exception
var _ = assertThrowsExactly(IndexOutOfBoundsException.class, () ->
    listAt.insertOrUpdate(10, "oops", items));

// Use listAtWithPadding for auto-expansion
At<List<String>, Integer, String> paddedAt = AtInstances.listAtWithPadding(null);

List<String> sparse = new ArrayList<>(List.of("a"));
List<String> expanded = paddedAt.insertOrUpdate(4, "e", sparse);
// Result: ["a", null, null, null, "e"]
```

---

## Common Pitfalls

~~~admonish failure title="Avoid: Null values in maps"
```java
Map<String, Integer> map = new HashMap<>();
map.put("nullKey", null);

At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();
Optional<Integer> result = at.get("nullKey", map);
// Result: Optional.empty() - NOT Optional.of(null)!

// Java's Optional cannot hold null values
```
~~~

~~~admonish failure title="Avoid: Expecting Ixed to insert"
```java
Ixed<Map<String, Integer>, String, Integer> mapIx = IxedInstances.mapIx();
Map<String, Integer> empty = new HashMap<>();

Map<String, Integer> result = IxedInstances.update(mapIx, "key", 100, empty);
// Result: {} - STILL EMPTY! No insertion occurred.

// If you need insertion, use At instead
```
~~~

~~~admonish success title="Do: Use Prisms.some() for value-level operations with At"
```java
Lens<Config, Optional<String>> settingLens =
    settingsLens.andThen(mapAt.at("theme"));

Prism<Optional<String>, String> some = Prisms.some();
Traversal<Config, String> valueTraversal =
    settingLens.asTraversal().andThen(some.asTraversal());

// Now you can work with the actual String, not Optional<String>
Config result = Traversals.modify(valueTraversal, String::trim, config);
```
~~~

---

## Performance Considerations

Both At and Ixed create new collection instances on every modification:

```java
// Each operation copies the entire map - O(n)
Map<String, Integer> step1 = at.insertOrUpdate("a", 1, map);   // Copy
Map<String, Integer> step2 = at.insertOrUpdate("b", 2, step1); // Copy
Map<String, Integer> step3 = at.remove("c", step2);            // Copy
```

~~~admonish tip title="Batch Modifications"
For multiple updates, consider direct bulk construction then switch to optics for subsequent immutable operations:

```java
Map<String, Integer> result = new HashMap<>(original);
result.put("a", 1);
result.put("b", 2);
result.remove("c");
// Now use At/Ixed for subsequent immutable operations
```
~~~

---

## Summary

| Use Case | Tool | Why |
|----------|------|-----|
| Add new map entry | `At.insertOrUpdate()` | Only At can insert |
| Delete map entry | `At.remove()` | Only At can delete |
| Update if exists, else no-op | `Ixed.update()` | Safe, structure-preserving |
| Safe list access without exceptions | `Ixed.get()` | Returns Optional.empty() for invalid indices |
| Deep nested CRUD | At + Lens composition | Full control over nested maps |
| Deep nested read/update | Ixed + Lens composition | Safe partial access |

Both type classes maintain immutability and compose naturally with the rest of the optics ecosystem.

---

[Previous: String Traversals](string_traversals.md) | [Next: Advanced Prism Patterns](advanced_prism_patterns.md)
