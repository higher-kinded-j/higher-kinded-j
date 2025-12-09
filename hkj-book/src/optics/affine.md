# Affines: A Practical Guide

## _Working with Optional Fields_

~~~admonish info title="What You'll Learn"
- How to safely work with optional fields and nullable properties
- The difference between Affine, Lens, and Prism
- Why `Lens.andThen(Prism)` produces an Affine, not a Traversal
- Composing affines with other optics for deep optional access
- Handling zero-or-one element focus without boilerplate
- When to use Affines vs Prisms vs Lenses
~~~

~~~admonish title="Example Code"
[AffineUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/AffineUsageExample.java)
~~~

We've seen how a **Lens** focuses on exactly one value that is guaranteed to exist, and how a **Prism** focuses on a value that may or may not exist depending on the variant.

But what about fields that are *sometimes* there? Optional fields in records, nullable properties in legacy APIs, or the result of composing a Lens with a Prism? This is the domain of the **Affine**.

---

## The Scenario: Optional Fields in Records

Modern Java applications frequently use `Optional<T>` to represent values that may be absent. Consider a user profile with optional contact information:

```java
record UserProfile(String username, Optional<ContactInfo> contact) {}
record ContactInfo(String email, Optional<String> phone) {}
```

**Our Goal:** We need to safely access and update the phone number, which is doubly optional: the contact info might not exist, and even if it does, the phone number might be absent.

---

## Think of Affines Like...

- **A Lens with uncertainty**: Gets exactly one value *if* it exists
- **A Prism without construction**: Can update but not build from scratch
- **An optional field accessor**: Perfect for `Optional<T>` fields
- **A Lens + Prism composition**: The natural result of combining them

---

## Understanding the Optic Hierarchy

An Affine sits between Lens and Traversal in the optic hierarchy:

```
         Iso
        /   \
     Lens   Prism
        \   /
        Affine
          |
       Traversal
```

**Key insight:** When you compose a Lens (exactly one element) with a Prism (zero or one element), the result focuses on zero or one element, which is an Affine.

| Optic | Focus | Get | Set |
|-------|-------|-----|-----|
| **Lens** | Exactly one | Always succeeds | Always succeeds |
| **Prism** | Zero or one | May fail | Can build from scratch |
| **Affine** | Zero or one | May fail | Requires existing structure |
| **Traversal** | Zero or more | Multiple values | Multiple values |

---

## A Step-by-Step Walkthrough

### Step 1: Creating an Affine Manually

An Affine is defined by two operations:

* **`getOptional(source)`**: Returns `Optional<A>` containing the focus if present
* **`set(value, source)`**: Returns a new source with the focus updated

```java
import org.higherkindedj.optics.Affine;
import java.util.Optional;

// Affine for accessing the value inside an Optional field
Affine<Optional<String>, String> someAffine = Affine.of(
    Function.identity(),                    // getOptional: Optional<String> -> Optional<String>
    (opt, value) -> Optional.of(value)      // set: always wrap in Optional.of
);

// Usage
Optional<String> present = Optional.of("hello");
Optional<String> result = someAffine.getOptional(present);  // Optional.of("hello")

Optional<String> empty = Optional.empty();
Optional<String> noMatch = someAffine.getOptional(empty);   // Optional.empty()

// Setting always wraps the value
Optional<String> updated = someAffine.set("world", empty);  // Optional.of("world")
```

### Step 2: Using the Affines Utility Class

The `Affines` utility class provides ready-made affines for common patterns:

```java
import org.higherkindedj.optics.util.Affines;

// For Optional<T> fields
Affine<Optional<String>, String> someAffine = Affines.some();

// For Maybe<T> (higher-kinded-j's Maybe type)
Affine<Maybe<String>, String> justAffine = Affines.just();

// For nullable fields (legacy code)
Affine<@Nullable String, String> nullableAffine = Affines.nullable();

// For list element access
Affine<List<String>, String> headAffine = Affines.listHead();
Affine<List<String>, String> lastAffine = Affines.listLast();
Affine<List<String>, String> thirdAffine = Affines.listAt(2);
```

### Step 3: Affine from Lens + Prism Composition

The most common way to obtain an Affine is through composition:

```java
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Prisms;

// Domain model
record Config(Optional<DatabaseSettings> database) {}
record DatabaseSettings(String host, int port) {}

// Lens to the Optional field
Lens<Config, Optional<DatabaseSettings>> databaseLens =
    Lens.of(Config::database, (c, db) -> new Config(db));

// Prism to extract from Optional
Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

// Composition: Lens >>> Prism = Affine
Affine<Config, DatabaseSettings> databaseAffine =
    databaseLens.andThen(somePrism);

// Usage
Config config1 = new Config(Optional.of(new DatabaseSettings("localhost", 5432)));
Optional<DatabaseSettings> result1 = databaseAffine.getOptional(config1);
// result1 = Optional[DatabaseSettings[host=localhost, port=5432]]

Config config2 = new Config(Optional.empty());
Optional<DatabaseSettings> result2 = databaseAffine.getOptional(config2);
// result2 = Optional.empty()

// Setting through the affine
Config updated = databaseAffine.set(new DatabaseSettings("newhost", 3306), config2);
// updated = Config[database=Optional[DatabaseSettings[host=newhost, port=3306]]]
```

~~~admonish tip title="Why Affine, not Traversal?"
You might wonder why `Lens.andThen(Prism)` returns an Affine rather than a Traversal. The answer is precision:

- **Traversal** focuses on *zero or more* elements
- **Affine** focuses on *exactly zero or one* element

Since a Lens always provides one element and a Prism may match zero or one, the composition can never produce *more* than one element. Affine captures this constraint precisely, giving you stronger type guarantees.
~~~

---

## Affine vs Prism: The Key Difference

Both Affine and Prism focus on zero-or-one elements, but they differ in one crucial way:

| Operation | Prism | Affine |
|-----------|-------|--------|
| **getOptional** | ✅ Yes | ✅ Yes |
| **set** | ✅ Yes | ✅ Yes |
| **build** | ✅ Yes (construct from part) | ❌ No |

A **Prism** can *construct* a complete structure from just the focused part (via `build`). An **Affine** cannot; it can only *modify* an existing structure.

```java
// Prism: can build from scratch
Prism<Shape, Circle> circlePrism = ...;
Shape newCircle = circlePrism.build(new Circle(5.0, "red"));  // Works!

// Affine: cannot build, only update
Affine<Config, DatabaseSettings> dbAffine = ...;
// No build() method available; must have an existing Config to work with
Config updated = dbAffine.set(newSettings, existingConfig);
```

**When to use which:**
- Use **Prism** for sum types where you can construct variants
- Use **Affine** for optional fields in product types

---

## Convenience Methods

The `Affine` interface provides several convenience methods for common operations:

**Quick Reference:**

| Method | Purpose | Returns |
|--------|---------|---------|
| `matches(S source)` | Check if affine focuses on a value | `boolean` |
| `doesNotMatch(S source)` | Check if affine has no focus | `boolean` |
| `getOrElse(A default, S source)` | Extract value or return default | `A` |
| `mapOptional(Function<A, B> f, S source)` | Transform focused value | `Optional<B>` |
| `modify(Function<A, A> f, S source)` | Modify if present, else return original | `S` |
| `modifyWhen(Predicate<A> p, Function<A, A> f, S source)` | Modify only when predicate satisfied | `S` |
| `setWhen(Predicate<A> p, A value, S source)` | Set only when predicate satisfied | `S` |
| `remove(S source)` | Remove the focused element (if supported) | `S` |

### Checking for Presence

```java
Affine<Optional<String>, String> someAffine = Affines.some();

Optional<String> present = Optional.of("hello");
Optional<String> empty = Optional.empty();

// Using matches()
if (someAffine.matches(present)) {
    System.out.println("Value present");
}

// Using doesNotMatch()
if (someAffine.doesNotMatch(empty)) {
    System.out.println("No value");
}

// Useful in streams
List<Optional<String>> values = List.of(
    Optional.of("a"),
    Optional.empty(),
    Optional.of("b")
);

long presentCount = values.stream()
    .filter(someAffine::matches)
    .count();  // 2
```

### Default Values

```java
Affine<Optional<Config>, Config> configAffine = Affines.some();

Optional<Config> maybeConfig = loadConfig();

// Get value or use default
Config config = configAffine.getOrElse(Config.DEFAULT, maybeConfig);
```

### Conditional Modification

```java
Affine<Optional<String>, String> someAffine = Affines.some();

Optional<String> value = Optional.of("hello world");

// Only modify if predicate is satisfied
Optional<String> result = someAffine.modifyWhen(
    s -> s.length() > 5,
    String::toUpperCase,
    value
);
// result = Optional.of("HELLO WORLD")

// Set only when condition is met
Optional<String> guarded = someAffine.setWhen(
    s -> s.startsWith("hello"),
    "goodbye",
    value
);
// guarded = Optional.of("goodbye")
```

### Removal Support

Some affines support the `remove` operation to clear the focused element:

```java
// Create an affine that supports removal
Affine<Optional<String>, String> removableAffine = Affines.someWithRemove();

Optional<String> present = Optional.of("hello");
Optional<String> cleared = removableAffine.remove(present);
// cleared = Optional.empty()
```

~~~admonish warning title="Remove Support"
Not all affines support the `remove` operation. Calling `remove` on an affine that doesn't support it will return the source unchanged. Use `Affines.someWithRemove()` instead of `Affines.some()` when you need removal support.
~~~

---

## Composing Affines

Affines compose with other optics following precise rules:

```java
// Affine >>> Affine = Affine
Affine<A, C> result = affineAB.andThen(affineBC);

// Affine >>> Lens = Affine
Affine<A, C> result = affineAB.andThen(lensBC);

// Affine >>> Prism = Affine
Affine<A, C> result = affineAB.andThen(prismBC);

// Affine >>> Iso = Affine
Affine<A, C> result = affineAB.andThen(isoBC);

// Affine >>> Traversal = Traversal
Traversal<A, C> result = affineAB.asTraversal().andThen(traversalBC);
```

### Deep Optional Access Example

```java
record User(String name, Optional<Address> address) {}
record Address(String street, Optional<String> postcode) {}

// Build affines for each optional field
Lens<User, Optional<Address>> addressLens =
    Lens.of(User::address, (u, a) -> new User(u.name(), a));

Lens<Address, Optional<String>> postcodeLens =
    Lens.of(Address::postcode, (a, p) -> new Address(a.street(), p));

Prism<Optional<Address>, Address> addressPrism = Prisms.some();
Prism<Optional<String>, String> postcodePrism = Prisms.some();

// Compose to access nested optional
Affine<User, String> userPostcode =
    addressLens
        .andThen(addressPrism)           // Lens >>> Prism = Affine
        .andThen(postcodeLens)           // Affine >>> Lens = Affine
        .andThen(postcodePrism);         // Affine >>> Prism = Affine

// Usage
User user1 = new User("Alice", Optional.of(
    new Address("123 Main St", Optional.of("SW1A 1AA"))
));
User user2 = new User("Bob", Optional.empty());

Optional<String> postcode1 = userPostcode.getOptional(user1);
// Optional.of("SW1A 1AA")

Optional<String> postcode2 = userPostcode.getOptional(user2);
// Optional.empty()

// Update deeply nested optional
User updated = userPostcode.set("EC1A 1BB", user1);
// User[name=Alice, address=Optional[Address[street=123 Main St, postcode=Optional[EC1A 1BB]]]]
```

---

## Factory Methods

The `Affine` interface provides factory methods for common construction patterns:

### From Getter and Setter

```java
// Basic construction
Affine<S, A> affine = Affine.of(
    s -> getOptional(s),           // S -> Optional<A>
    (s, a) -> setInSource(s, a)    // (S, A) -> S
);

// With removal support
Affine<S, A> removable = Affine.of(
    s -> getOptional(s),           // S -> Optional<A>
    (s, a) -> setInSource(s, a),   // (S, A) -> S
    s -> removeFromSource(s)       // S -> S
);
```

### From Lens and Prism

```java
// Compose a Lens and Prism into an Affine
Affine<S, B> affine = Affine.fromLensAndPrism(
    lensAB,   // Lens<S, A>
    prismBC   // Prism<A, B>
);

// Compose a Prism and Lens into an Affine
Affine<S, B> affine = Affine.fromPrismAndLens(
    prismAB,  // Prism<S, A>
    lensBC    // Lens<A, B>
);
```

---

## When to Use Affines vs Other Optics

### Use Affine When:

* **Optional fields** in records or classes (`Optional<T>`)
* **Nullable properties** in legacy or interop code
* **Conditional field access** that may or may not exist
* **Lens + Prism compositions** where you need the precise type

```java
// Perfect for optional record fields
record Config(Optional<String> apiKey) {}

Affine<Config, String> apiKeyAffine =
    ConfigLenses.apiKey().andThen(Prisms.some());

Optional<String> key = apiKeyAffine.getOptional(config);
```

### Use Lens When:

* The field is **always present** (guaranteed to exist)
* You're working with **product types** (records, classes)

```java
// Field always exists
record Point(int x, int y) {}
Lens<Point, Integer> xLens = Lens.of(Point::x, (p, x) -> new Point(x, p.y()));
```

### Use Prism When:

* Working with **sum types** (sealed interfaces, enums)
* You need to **construct** the whole from a part
* Type-safe **variant matching**

```java
// Sum type handling
sealed interface Shape permits Circle, Rectangle {}
Prism<Shape, Circle> circlePrism = ...;
Shape circle = circlePrism.build(new Circle(5.0));  // Can construct!
```

### Use Traversal When:

* Focusing on **multiple elements** (lists, sets)
* You need to work with **collections**

```java
// Multiple elements
Traversal<List<String>, String> listTraversal = Traversals.forList();
List<String> upper = Traversals.modify(listTraversal, String::toUpperCase, names);
```

---

## Common Pitfalls

### Don't Do This:

```java
// Overly complex: manual Optional handling
Optional<String> getNestedValue(Config config) {
    return config.database()
        .flatMap(db -> db.connection())
        .flatMap(conn -> conn.timeout())
        .map(Object::toString);
}

// Unsafe: assuming presence without checking
String value = config.database().get().host();  // NoSuchElementException!

// Verbose: repeated null checks
if (user.address() != null && user.address().postcode() != null) {
    return user.address().postcode();
}
```

### Do This Instead:

```java
// Clean: compose affines for deep access
Affine<Config, String> timeoutAffine =
    databaseAffine
        .andThen(connectionAffine)
        .andThen(timeoutLens)
        .andThen(Affines.some());

Optional<String> timeout = timeoutAffine.mapOptional(Object::toString, config);

// Safe: affine handles absence gracefully
String value = databaseAffine.getOrElse(defaultSettings, config).host();

// Composable: build reusable optics
Affine<User, String> postcodeAffine = UserOptics.postcode();
Optional<String> postcode = postcodeAffine.getOptional(user);
```

---

## The Affine Laws

Well-behaved affines satisfy these laws:

### Get-Set Law
If a value is present, getting and then setting returns the original:
```java
affine.getOptional(s).map(a -> affine.set(a, s)).orElse(s) == s
```

### Set-Set Law
Setting twice is equivalent to setting once with the final value:
```java
affine.set(b, affine.set(a, s)) == affine.set(b, s)
```

### GetOptional-Set Law
Setting a value and then getting returns that value (if the structure allows):
```java
// When getOptional returns a value after set:
affine.getOptional(affine.set(a, s)) == Optional.of(a)
// (or Optional.empty() if the structure doesn't support the focus)
```

---

## Real-World Example: Configuration Management

```java
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.Affines;
import org.higherkindedj.optics.util.Prisms;

import java.util.Optional;

// Domain model with nested optionals
record AppConfig(
    String appName,
    Optional<DatabaseConfig> database,
    Optional<CacheConfig> cache
) {}

record DatabaseConfig(
    String host,
    int port,
    Optional<PoolConfig> pool
) {}

record PoolConfig(int minSize, int maxSize) {}

record CacheConfig(String provider, int ttlSeconds) {}

public class ConfigOptics {
    // Lenses for required fields
    public static final Lens<AppConfig, String> appName =
        Lens.of(AppConfig::appName, (c, n) -> new AppConfig(n, c.database(), c.cache()));

    public static final Lens<AppConfig, Optional<DatabaseConfig>> database =
        Lens.of(AppConfig::database, (c, db) -> new AppConfig(c.appName(), db, c.cache()));

    public static final Lens<DatabaseConfig, String> host =
        Lens.of(DatabaseConfig::host, (db, h) -> new DatabaseConfig(h, db.port(), db.pool()));

    public static final Lens<DatabaseConfig, Optional<PoolConfig>> pool =
        Lens.of(DatabaseConfig::pool, (db, p) -> new DatabaseConfig(db.host(), db.port(), p));

    public static final Lens<PoolConfig, Integer> maxSize =
        Lens.of(PoolConfig::maxSize, (p, m) -> new PoolConfig(p.minSize(), m));

    // Affines for optional access
    public static final Affine<AppConfig, DatabaseConfig> databaseAffine =
        database.andThen(Prisms.some());

    public static final Affine<AppConfig, String> databaseHost =
        databaseAffine.andThen(host);

    public static final Affine<AppConfig, PoolConfig> poolConfig =
        databaseAffine
            .andThen(pool)
            .andThen(Prisms.some());

    public static final Affine<AppConfig, Integer> poolMaxSize =
        poolConfig.andThen(maxSize);

    public static void main(String[] args) {
        // Create a config with nested optionals
        AppConfig config = new AppConfig(
            "MyApp",
            Optional.of(new DatabaseConfig(
                "localhost",
                5432,
                Optional.of(new PoolConfig(5, 20))
            )),
            Optional.empty()
        );

        // Read nested values safely
        System.out.println("Host: " + databaseHost.getOptional(config));
        // Host: Optional[localhost]

        System.out.println("Pool max: " + poolMaxSize.getOptional(config));
        // Pool max: Optional[20]

        // Update deeply nested value
        AppConfig updated = poolMaxSize.set(50, config);
        System.out.println("Updated pool max: " + poolMaxSize.getOptional(updated));
        // Updated pool max: Optional[50]

        // Conditional modification
        AppConfig doubled = poolMaxSize.modify(n -> n * 2, config);
        System.out.println("Doubled pool max: " + poolMaxSize.getOptional(doubled));
        // Doubled pool max: Optional[40]

        // Safe operation on missing config
        AppConfig emptyConfig = new AppConfig("EmptyApp", Optional.empty(), Optional.empty());
        System.out.println("Missing host: " + databaseHost.getOptional(emptyConfig));
        // Missing host: Optional.empty

        // Modification on missing does nothing
        AppConfig unchanged = poolMaxSize.modify(n -> n * 2, emptyConfig);
        System.out.println("Empty config unchanged: " + (unchanged == emptyConfig));
        // Empty config unchanged: true
    }
}
```

---

## Performance Notes

Affines are designed for both safety and efficiency:

* **Zero allocation for absent values**: `getOptional` returns `Optional.empty()` without allocating
* **Short-circuit evaluation**: Composed affines stop at the first absent value
* **Immutable by design**: All operations return new values, enabling safe concurrent use
* **Inlinable**: Simple affines are candidates for JVM inlining

**Best Practice**: Create composed affines once and reuse them:

```java
public class UserOptics {
    // Create once, use everywhere
    public static final Affine<User, String> EMAIL =
        addressLens.andThen(addressPrism).andThen(emailLens);

    public static final Affine<User, String> POSTCODE =
        addressLens.andThen(addressPrism).andThen(postcodeLens).andThen(postcodePrism);
}
```

---

## Why Affines are Essential

Affines fill an important gap in the optic hierarchy:

* **Precision**: More precise than Traversal for zero-or-one access
* **Composability**: Natural result of Lens + Prism composition
* **Safety**: Eliminate null checks and `Optional.flatMap` chains
* **Expressiveness**: Clearly communicate "optional field" intent

By adding Affines to your toolkit, you can write cleaner, safer code that handles optional data with the same elegance as required fields.

---

~~~admonish tip title="Further Reading"
- **Monocle Optional**: [Scala's Affine](https://www.optics.dev/Monocle/docs/optics/optional) - Monocle uses "Optional" for the same concept
- **Baeldung**: [Handling Optionality in Java](https://www.baeldung.com/java-optional) - Guide to Java Optional, the underlying type Affine often works with
~~~

~~~admonish tip title="Terminology Note"
In some functional programming libraries (notably Scala's Monocle), the Affine optic is called an **Optional**. This can cause confusion with Java's `java.util.Optional`. In higher-kinded-j, we use the term "Affine" to avoid this ambiguity whilst maintaining mathematical precision.
~~~

---

**Previous:** [Prisms: Working with Sum Types](prisms.md)
**Next:** [Isomorphisms: Data Equivalence](iso.md)
