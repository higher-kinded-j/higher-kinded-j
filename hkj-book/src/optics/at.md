# At Type Class: A Practical Guide

## _Indexed CRUD Operations on Collections_

~~~admonish info title="What You'll Learn"
- How to insert, update, and delete entries in indexed structures using optics
- Understanding the `Lens<S, Optional<A>>` pattern for CRUD operations
- Factory methods: `mapAt()`, `listAt()`, `listAtWithPadding()`
- Composing At with Lenses for deep access into nested collections
- Using `Prisms.some()` to unwrap Optional for chained modifications
- When to use At vs `Traversals.forMap()` vs direct Map operations
- Handling Java's Optional limitations with null values
- Building immutable configuration management systems
~~~

~~~admonish title="Example Code"
[AtUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/AtUsageExample.java)
~~~

In previous guides, we explored **`Lens`** for accessing product fields and **`Traversal`** for operating over collections. But what happens when you need to *insert* a new entry into a map, *delete* an existing key, or *update* a specific list index? Standard lenses can't express these operations because they focus on values that already exist.

This is where **`At`** fills a crucial gap. It provides a `Lens` that focuses on the *optional presence* of a value at a given index—enabling full CRUD (Create, Read, Update, Delete) operations whilst maintaining immutability and composability.

---

## The Scenario: Application Configuration Management

Consider a configuration management system where settings are stored in nested maps and lists:

**The Data Model:**

```java
public record AppConfig(
    String appName,
    Map<String, String> settings,
    Map<String, Map<String, Integer>> featureFlags,
    List<String> enabledModules
) {}

public record UserPreferences(
    String userId,
    Map<String, String> preferences,
    List<String> favouriteFeatures
) {}

public record SystemState(
    AppConfig config,
    Map<String, UserPreferences> userPrefs,
    Map<String, Integer> metrics
) {}
```

**Common Operations:**
* "Add a new setting to the configuration"
* "Remove an outdated feature flag"
* "Update a specific user's preference"
* "Check if a metric exists before incrementing it"
* "Delete a user's preferences entirely"

Traditional optics struggle with these operations. `Traversals.forMap(key)` can modify existing entries but cannot insert new ones or delete them. Direct map manipulation breaks composability. **`At`** solves this elegantly.

---

## Think of At Like...

* **A key to a lockbox** 🔑: You can open it (read), put something in (insert), replace the contents (update), or empty it (delete)
* **An index card in a filing cabinet** 📇: You can retrieve the card, file a new one, update its contents, or remove it entirely
* **A dictionary entry** 📖: Looking up a word gives you its definition (if present) or nothing (if absent)
* **A database row accessor** 🗃️: SELECT, INSERT, UPDATE, and DELETE operations on a specific key
* **A nullable field lens** 🎯: Focusing on presence itself, not just the value

---

## At vs Lens vs Traversal: Understanding the Differences

| Aspect | At | Lens | Traversal |
|--------|-----|------|-----------|
| **Focus** | Optional presence at index | Exactly one value | Zero or more values |
| **Can insert?** | ✅ Yes | ❌ No | ❌ No |
| **Can delete?** | ✅ Yes | ❌ No | ❌ No |
| **Core operation** | `Lens<S, Optional<A>>` | `get(s)`, `set(a, s)` | `modifyF(f, s, app)` |
| **Returns** | `Lens` to Optional | Direct value | Modified structure |
| **Use case** | Map/List CRUD | Product field access | Bulk modifications |
| **Intent** | "Manage entry at this index" | "Access this field" | "Transform all elements" |

**Key Insight**: `At` returns a `Lens` that focuses on `Optional<A>`, not `A` directly. This means setting `Optional.empty()` *removes* the entry, whilst setting `Optional.of(value)` *inserts or updates* it. This simple abstraction enables powerful CRUD semantics within the optics framework.

---

## A Step-by-Step Walkthrough

### Step 1: Creating At Instances

Unlike `Lens` which can be generated with annotations, `At` instances are created using factory methods from `AtInstances`:

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

Each factory method returns an `At` instance parameterised by:
* `S` – The structure type (e.g., `Map<String, Integer>`)
* `I` – The index type (e.g., `String` for maps, `Integer` for lists)
* `A` – The element type (e.g., `Integer`)

### Step 2: Basic CRUD Operations

Once you have an `At` instance, you can perform full CRUD operations:

#### **Create / Insert**

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

#### **Read / Query**

```java
Optional<Integer> aliceScore = mapAt.get("alice", withBob);
// Result: Optional[100]

Optional<Integer> charlieScore = mapAt.get("charlie", withBob);
// Result: Optional.empty()

boolean hasAlice = mapAt.contains("alice", withBob);
// Result: true
```

#### **Update / Modify**

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

#### **Delete / Remove**

```java
Map<String, Integer> afterRemove = mapAt.remove("alice", bonusScores);
// Result: {bob=95}

// Remove non-existent key is a no-op
Map<String, Integer> stillSame = mapAt.remove("charlie", afterRemove);
// Result: {bob=95}
```

### Step 3: The Lens to Optional Pattern

The core of `At` is its `at(index)` method, which returns a `Lens<S, Optional<A>>`:

```java
At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
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

This pattern is powerful because the lens composes naturally with other optics:

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

### Step 4: Deep Composition with Prisms

When you need to access the actual value (not the `Optional` wrapper), compose with `Prisms.some()`:

```java
import org.higherkindedj.optics.util.Prisms;

Lens<Config, Optional<String>> debugLens =
    settingsLens.andThen(mapAt.at("debug"));

// Prism that unwraps Optional
Prism<Optional<String>, String> somePrism = Prisms.some();

// Compose into a Traversal (0-or-1 focus)
Traversal<Config, String> debugValueTraversal =
    debugLens.asTraversal().andThen(somePrism.asTraversal());

Config config = new Config(new HashMap<>(Map.of("debug", "false")));

// Modify the actual string value
Config modified = Traversals.modify(debugValueTraversal, String::toUpperCase, config);
// Result: Config[settings={debug=FALSE}]

// Get all focused values (0 or 1)
List<String> values = Traversals.getAll(debugValueTraversal, config);
// Result: ["FALSE"]

// If key is absent, traversal focuses on zero elements
Config empty = new Config(new HashMap<>());
List<String> noValues = Traversals.getAll(debugValueTraversal, empty);
// Result: []
```

This composition creates an "affine" optic—focusing on zero or one element—which correctly models the semantics of optional map access.

---

## List Operations: Special Considerations

`At` for lists has important behavioural differences from maps:

### Basic List Operations

```java
At<List<String>, Integer, String> listAt = AtInstances.listAt();

List<String> items = new ArrayList<>(List.of("apple", "banana", "cherry"));

// Read element at index
Optional<String> second = listAt.get(1, items);
// Result: Optional["banana"]

// Out of bounds returns empty
Optional<String> outOfBounds = listAt.get(10, items);
// Result: Optional.empty()

// Update element at valid index
List<String> updated = listAt.insertOrUpdate(1, "BANANA", items);
// Result: ["apple", "BANANA", "cherry"]
```

### Deletion Shifts Indices

**Important**: Removing a list element shifts all subsequent indices:

```java
List<String> afterRemove = listAt.remove(1, items);
// Result: ["apple", "cherry"]
// Note: "cherry" is now at index 1, not 2!

// Original list unchanged
System.out.println(items); // ["apple", "banana", "cherry"]
```

This behaviour differs from map deletion, where keys remain stable. Consider this carefully when chaining operations.

### Bounds Checking

```java
// Update at invalid index throws exception
assertThrows(IndexOutOfBoundsException.class, () ->
    listAt.insertOrUpdate(10, "oops", items));

// Use listAtWithPadding for auto-expansion
At<List<String>, Integer, String> paddedAt = AtInstances.listAtWithPadding(null);

List<String> sparse = new ArrayList<>(List.of("a"));
List<String> expanded = paddedAt.insertOrUpdate(4, "e", sparse);
// Result: ["a", null, null, null, "e"]
```

---

## When to Use At vs Other Approaches

### ✅ Use `At` When:

* **You need CRUD semantics**: Insert, update, or delete operations on indexed structures
* **Composability matters**: You want to chain At with Lenses for deep nested access
* **Immutability is required**: You need functional, side-effect-free operations
* **You're building configuration systems**: Dynamic settings management
* **You want consistent optics patterns**: Keeping your codebase uniformly functional

### ❌ Avoid `At` When:

* **You only modify existing values**: Use `Traversals.forMap(key)` instead
* **You need bulk operations**: Use `Traversal` for all-element modifications
* **Performance is critical**: Direct Map operations may be faster (measure first!)
* **You never delete entries**: A simple Lens might suffice

### Comparison with Direct Map Operations

```java
// Direct Map manipulation (imperative)
Map<String, Integer> scores = new HashMap<>();
scores.put("alice", 100);           // Mutates!
scores.remove("bob");                // Mutates!
Integer value = scores.get("alice"); // May be null

// At approach (functional)
At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();
Map<String, Integer> scores = new HashMap<>();
Map<String, Integer> with = at.insertOrUpdate("alice", 100, scores);  // New map
Map<String, Integer> without = at.remove("bob", with);                  // New map
Optional<Integer> value = at.get("alice", without);                     // Safe Optional
// Original 'scores' unchanged throughout
```

---

## Common Pitfalls

### ❌ Don't: Assume null map values are distinguishable from absent keys

```java
Map<String, Integer> map = new HashMap<>();
map.put("nullKey", null);

At<Map<String, Integer>, String, Integer> at = AtInstances.mapAt();
Optional<Integer> result = at.get("nullKey", map);
// Result: Optional.empty() - NOT Optional.of(null)!

// Java's Optional cannot hold null values
// Optional.ofNullable(null) returns Optional.empty()
```

### ✅ Do: Avoid null values in maps, or use wrapper types

```java
// Option 1: Use sentinel values
Map<String, Integer> map = new HashMap<>();
map.put("unset", -1); // Sentinel for "not set"

// Option 2: Use Optional as the value type
Map<String, Optional<Integer>> map = new HashMap<>();
map.put("maybeNull", Optional.empty()); // Explicitly absent
```

---

### ❌ Don't: Forget that list removal shifts indices

```java
At<List<String>, Integer, String> at = AtInstances.listAt();
List<String> items = new ArrayList<>(List.of("a", "b", "c", "d"));

// Remove "b" at index 1
List<String> step1 = at.remove(1, items); // ["a", "c", "d"]

// Now try to get what was at index 3 ("d")
Optional<String> result = at.get(3, step1);
// Result: Optional.empty() - index 3 is now out of bounds!
// "d" is now at index 2
```

### ✅ Do: Recalculate indices or iterate from end

```java
// When removing multiple elements, iterate backwards
List<String> items = new ArrayList<>(List.of("a", "b", "c", "d"));
List<Integer> indicesToRemove = List.of(1, 3); // Remove "b" and "d"

// Sort descending and remove from end
List<String> result = items;
for (int i : indicesToRemove.stream().sorted(Comparator.reverseOrder()).toList()) {
    result = at.remove(i, result);
}
// Result: ["a", "c"]
```

---

### ❌ Don't: Ignore Optional composition when you need the actual value

```java
Lens<Config, Optional<String>> settingLens = ...;

// This gives you Optional, not the actual value
Optional<String> optValue = settingLens.get(config);

// To modify the actual string, you need to compose with a Prism
// Otherwise you're stuck wrapping/unwrapping manually
```

### ✅ Do: Use `Prisms.some()` for value-level operations

```java
Prism<Optional<String>, String> some = Prisms.some();
Traversal<Config, String> valueTraversal =
    settingLens.asTraversal().andThen(some.asTraversal());

// Now you can work with the actual String
Config result = Traversals.modify(valueTraversal, String::trim, config);
```

---

## Performance Considerations

### HashMap Operations

`mapAt()` creates a new `HashMap` on every modification:

```java
// Each operation copies the entire map
Map<String, Integer> step1 = at.insertOrUpdate("a", 1, map);   // O(n) copy
Map<String, Integer> step2 = at.insertOrUpdate("b", 2, step1); // O(n) copy
Map<String, Integer> step3 = at.remove("c", step2);            // O(n) copy
```

**Best Practice**: Batch modifications when possible:

```java
// ❌ Multiple At operations (3 map copies)
Map<String, Integer> result = at.insertOrUpdate("a", 1,
    at.insertOrUpdate("b", 2,
        at.remove("c", original)));

// ✅ Single bulk operation
Map<String, Integer> result = new HashMap<>(original);
result.put("a", 1);
result.put("b", 2);
result.remove("c");
// Then use At for subsequent immutable operations
```

### List Operations

List modifications involve array copying:

```java
At<List<String>, Integer, String> at = AtInstances.listAt();

// Update is O(n) - copies entire list
List<String> updated = at.insertOrUpdate(0, "new", original);

// Remove is O(n) - copies and shifts
List<String> removed = at.remove(0, original);
```

For large lists with frequent modifications, consider alternative data structures (persistent collections, tree-based structures) or batch operations.

---

## Real-World Example: Feature Flag Management

Consider a feature flag system where different environments have different configurations:

```java
public class FeatureFlagManager {

    private final At<Map<String, Boolean>, String, Boolean> flagAt = AtInstances.mapAt();
    private Map<String, Boolean> flags;

    public FeatureFlagManager(Map<String, Boolean> initialFlags) {
        this.flags = new HashMap<>(initialFlags);
    }

    public void enableFeature(String featureName) {
        flags = flagAt.insertOrUpdate(featureName, true, flags);
    }

    public void disableFeature(String featureName) {
        flags = flagAt.insertOrUpdate(featureName, false, flags);
    }

    public void removeFeature(String featureName) {
        flags = flagAt.remove(featureName, flags);
    }

    public boolean isEnabled(String featureName) {
        return flagAt.get(featureName, flags).orElse(false);
    }

    public Map<String, Boolean> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
}

// Usage
var manager = new FeatureFlagManager(Map.of("dark_mode", true));
manager.enableFeature("new_dashboard");
manager.disableFeature("legacy_api");
manager.removeFeature("deprecated_feature");

System.out.println(manager.isEnabled("dark_mode"));        // true
System.out.println(manager.isEnabled("new_dashboard"));    // true
System.out.println(manager.isEnabled("nonexistent"));      // false
```

This pattern ensures all flag operations maintain immutability internally whilst providing a clean mutable-style API externally.

---

## Complete, Runnable Example

Here's a comprehensive example demonstrating all major At features:

```java
package org.higherkindedj.example.optics;

import java.util.*;
import org.higherkindedj.optics.At;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.at.AtInstances;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

public class AtUsageExample {

    @GenerateLenses
    public record UserProfile(
        String username,
        Map<String, String> settings,
        Map<String, Integer> scores,
        List<String> tags
    ) {}

    public static void main(String[] args) {
        System.out.println("=== At Type Class Usage Examples ===\n");

        // 1. Map CRUD Operations
        System.out.println("--- Map CRUD Operations ---");

        At<Map<String, Integer>, String, Integer> mapAt = AtInstances.mapAt();
        Map<String, Integer> scores = new HashMap<>(Map.of("maths", 95, "english", 88));

        System.out.println("Initial scores: " + scores);

        // Insert
        Map<String, Integer> withScience = mapAt.insertOrUpdate("science", 92, scores);
        System.out.println("After insert 'science': " + withScience);

        // Update
        Map<String, Integer> updatedMaths = mapAt.insertOrUpdate("maths", 98, withScience);
        System.out.println("After update 'maths': " + updatedMaths);

        // Read
        System.out.println("Physics score (absent): " + mapAt.get("physics", updatedMaths));
        System.out.println("Maths score (present): " + mapAt.get("maths", updatedMaths));

        // Delete
        Map<String, Integer> afterRemove = mapAt.remove("english", updatedMaths);
        System.out.println("After remove 'english': " + afterRemove);

        // Modify
        Map<String, Integer> bonusMaths = mapAt.modify("maths", x -> x + 5, afterRemove);
        System.out.println("After modify 'maths' (+5): " + bonusMaths);

        System.out.println("Original unchanged: " + scores);
        System.out.println();

        // 2. Lens Composition
        System.out.println("--- Lens Composition with At ---");

        // Use generated lenses from @GenerateLenses annotation
        Lens<UserProfile, Map<String, String>> settingsLens = UserProfileLenses.settings();

        At<Map<String, String>, String, String> stringMapAt = AtInstances.mapAt();

        UserProfile profile = new UserProfile(
            "alice",
            new HashMap<>(Map.of("theme", "dark", "language", "en")),
            new HashMap<>(Map.of("maths", 95)),
            new ArrayList<>(List.of("developer"))
        );

        System.out.println("Initial profile: " + profile);

        // Compose: UserProfile → Map → Optional<String>
        Lens<UserProfile, Optional<String>> themeLens =
            settingsLens.andThen(stringMapAt.at("theme"));

        System.out.println("Current theme: " + themeLens.get(profile));

        // Update through composed lens
        UserProfile lightTheme = themeLens.set(Optional.of("light"), profile);
        System.out.println("After setting theme: " + lightTheme.settings());

        // Add new setting
        Lens<UserProfile, Optional<String>> notifLens =
            settingsLens.andThen(stringMapAt.at("notifications"));
        UserProfile withNotif = notifLens.set(Optional.of("enabled"), lightTheme);
        System.out.println("After adding notification: " + withNotif.settings());

        // Remove setting
        Lens<UserProfile, Optional<String>> langLens =
            settingsLens.andThen(stringMapAt.at("language"));
        UserProfile noLang = langLens.set(Optional.empty(), withNotif);
        System.out.println("After removing language: " + noLang.settings());
        System.out.println();

        // 3. Deep Composition with Prism
        System.out.println("--- Deep Composition: At + Prism ---");

        Lens<UserProfile, Map<String, Integer>> scoresLens = UserProfileLenses.scores();

        At<Map<String, Integer>, String, Integer> scoresAt = AtInstances.mapAt();
        Prism<Optional<Integer>, Integer> somePrism = Prisms.some();

        // Compose into Traversal (0-or-1 focus)
        Lens<UserProfile, Optional<Integer>> mathsLens = scoresLens.andThen(scoresAt.at("maths"));
        Traversal<UserProfile, Integer> mathsTraversal =
            mathsLens.asTraversal().andThen(somePrism.asTraversal());

        UserProfile bob = new UserProfile(
            "bob",
            new HashMap<>(),
            new HashMap<>(Map.of("maths", 85, "science", 90)),
            new ArrayList<>()
        );

        System.out.println("Bob's profile: " + bob);

        // Get via traversal
        List<Integer> mathsScores = Traversals.getAll(mathsTraversal, bob);
        System.out.println("Maths score via traversal: " + mathsScores);

        // Modify via traversal
        UserProfile boostedBob = Traversals.modify(mathsTraversal, x -> x + 10, bob);
        System.out.println("After boosting maths by 10: " + boostedBob.scores());

        // Missing key = empty traversal
        Traversal<UserProfile, Integer> historyTraversal =
            scoresLens.andThen(scoresAt.at("history"))
                      .asTraversal().andThen(somePrism.asTraversal());

        List<Integer> historyScores = Traversals.getAll(historyTraversal, bob);
        System.out.println("History score (absent): " + historyScores);

        System.out.println("\n=== All operations maintain immutability ===");
    }
}
```

**Expected Output:**

```
=== At Type Class Usage Examples ===

--- Map CRUD Operations ---
Initial scores: {maths=95, english=88}
After insert 'science': {maths=95, science=92, english=88}
After update 'maths': {maths=98, science=92, english=88}
Physics score (absent): Optional.empty
Maths score (present): Optional[98]
After remove 'english': {maths=98, science=92}
After modify 'maths' (+5): {maths=103, science=92}
Original unchanged: {maths=95, english=88}

--- Lens Composition with At ---
Initial profile: UserProfile[username=alice, settings={theme=dark, language=en}, scores={maths=95}, tags=[developer]]
Current theme: Optional[dark]
After setting theme: {theme=light, language=en}
After adding notification: {theme=light, language=en, notifications=enabled}
After removing language: {theme=light, notifications=enabled}

--- Deep Composition: At + Prism ---
Bob's profile: UserProfile[username=bob, settings={}, scores={maths=85, science=90}, tags=[]]
Maths score via traversal: [85]
After boosting maths by 10: {maths=95, science=90}
History score (absent): []

=== All operations maintain immutability ===
```

---

## Further Reading

* [Haskell Lens Library - At Type Class](https://hackage.haskell.org/package/lens/docs/Control-Lens-At.html)
* [Optics By Example](https://leanpub.com/optics-by-example) - Comprehensive optics guide
* [Lenses Guide](lenses.md) - Foundation for At composition
* [Prisms Guide](prisms.md) - Unwrapping Optional with Prisms
* [Traversals Guide](traversals.md) - Bulk operations on collections

---

## Summary

The **At** type class provides a powerful abstraction for indexed CRUD operations on collections:

* **Lens to Optional**: `at(index)` returns `Lens<S, Optional<A>>` enabling insert/update/delete
* **Immutable by design**: All operations return new structures
* **Composable**: Chains naturally with other optics for deep access
* **Type-safe**: Leverages Java's type system for safety

At bridges the gap between pure functional optics and practical collection manipulation, enabling you to build robust, immutable data pipelines that handle the full lifecycle of indexed data.

**Related**: For read/update-only operations without insert/delete semantics, see the [Ixed Type Class](ixed.md) guide, which provides safe partial access via `Traversal<S, A>`.

---

[Previous: Setters](setters.md) | [Next: Ixed Type Class](ixed.md)
