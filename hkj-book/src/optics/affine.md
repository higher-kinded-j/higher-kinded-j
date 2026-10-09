# Affines: A Practical Guide

_Read and update a field that may be absent, such as an `Optional` component, without `flatMap` chains._

~~~admonish info title="What You'll Learn"
- Build an affine with `Affines.some()`, `Affines.nullable()` or `Affines.listAt`, or by composing a lens with a prism
- Predict why `Lens.andThen(Prism)` gives an `Affine` rather than a `Traversal`
- Update a nested optional field with `modify` and `modifyWhen`, which leave an absent value alone
- Predict what `set` does on an absent focus, and check an affine with `AffineLaws.assertAffineLaws`
- Choose between an affine, a prism, a lens and a traversal for a field
~~~

~~~admonish example title="See Example Code"
[AffineUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/AffineUsageExample.java)
~~~

We've seen how a **Lens** focuses on exactly one value that is guaranteed to exist, and how a **Prism** focuses on a value that may or may not exist depending on the variant.

But what about fields that are *sometimes* there? Optional fields in records, nullable properties in legacy APIs, or the result of composing a Lens with a Prism? This is the domain of the **Affine**.

An affine plays the part of an accessor that returns `Optional`, paired with a copy that writes the value back. Unlike the hand-written pair, it composes with other optics, and `modify` leaves an absent value alone. [Choosing an optic](optics_intro.md#choosing-an-optic) sets it beside the other optic types.

---

## The Scenario: Optional Fields in Records

Modern Java applications frequently use `Optional<T>` to represent values that may be absent. The chapter's customer profile has an optional nickname and an optional second email:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/CustomerProfile.java:customer_profile}}
```

A product review may be anonymous, so its author is optional too:

<!-- verify -->
```java
record Review(String sku, Optional<CustomerProfile> author) {}
```

**Our Goal:** We need to safely access and update the reviewer's nickname, which is doubly optional: the review might be anonymous, and even if it is not, its author might have no nickname.

---

## Where an Affine Comes From {#understanding-the-optic-hierarchy}

In Java the optic types are siblings, not subtypes ([How the optic types relate](ch_intro.md#how-the-optic-types-relate)), so a method that takes an `Affine` will not accept a `Lens`. You *compose* your way to an Affine.

**Key insight:** When you compose a Lens (exactly one element) with a Prism (zero or one element), the result focuses on zero or one element, which is an Affine.

| Optic | Focus | Get | Set |
|-------|-------|-----|-----|
| **Lens** | Exactly one | Always succeeds | Always succeeds |
| **Prism** | Zero or one | May fail | No `set`: `build` makes a whole from a part, `modify` changes a match |
| **Affine** | Zero or one | May fail | Writes into a structure you already have |
| **Traversal** | Zero or more | Multiple values | Multiple values |

---

## A Step-by-Step Walkthrough

### Step 1: Creating an Affine Manually

An Affine is defined by two operations:

* **`getOptional(source)`**: Returns `Optional<A>` containing the focus if present
* **`set(value, source)`**: Returns a new source with the focus updated

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:manual}}
```

### Step 2: Using the Affines Utility Class

The `Affines` utility class provides ready-made affines for common patterns:

<!-- verify -->
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

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:lens_prism}}
```

The affine's path ends inside an `Optional` that may hold nothing:

<pre class="hkj-ascii-diagram" role="img" aria-label="The affine runs from Config through its database field into the Optional. When the Optional holds settings, set replaces them and rebuilds the Config. When it is empty, getOptional is empty, and set builds the Optional, because some() can build one.">
Config ●
└─ database ●          databaseLens
   └─ Optional ◇       some()
      DatabaseSettings[...]

● on the path: rebuilt by set
◇ may hold nothing: getOptional
  is then empty, and set builds
  the Optional: some() is a
  prism, so it can
</pre>

~~~admonish tip title="Why Affine, not Traversal?"
You might wonder why `Lens.andThen(Prism)` returns an Affine rather than a Traversal. The answer is precision:

- **Traversal** focuses on *zero or more* elements
- **Affine** focuses on *exactly zero or one* element

Since a Lens always provides one element and a Prism may match zero or one, the composition can never produce *more* than one element. Affine captures this constraint precisely, giving you stronger type guarantees.
~~~

---

## Affine vs Prism: The Key Difference

Both Affine and Prism focus on zero-or-one elements, but they differ in what they can write:

| Operation | Prism | Affine |
|-----------|-------|--------|
| **getOptional** | ✅ Yes | ✅ Yes |
| **set** | ❌ No (`modify` changes a match) | ✅ Yes |
| **build** | ✅ Yes (construct from part) | ❌ No |

A **Prism** can *construct* a complete structure from just the focused part (via `build`). An **Affine** cannot; it writes into a structure you already have.

<!-- verify -->
```java
// Prism: can build from scratch
Prism<Shape, Circle> circlePrism = ShapePrisms.circle();
Shape newCircle = circlePrism.build(new Circle(5.0, "red"));

// Affine: cannot build, only update
Affine<Config, DatabaseSettings> dbAffine = databaseLens.andThen(somePrism);
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

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:presence}}
```

### Default Values

<!-- verify -->
```java
Affine<Optional<Config>, Config> configAffine = Affines.some();

Optional<Config> maybeConfig = loadConfig();

// Get value or use default
Config config = configAffine.getOrElse(Config.DEFAULT, maybeConfig);
```

### Conditional Modification

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:conditional}}
```

### Removal Support

Some affines support the `remove` operation to clear the focused element:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:removal}}
```

~~~admonish warning title="Remove Support"
Not all affines support the `remove` operation. Calling `remove` on an affine that doesn't support it throws an `UnsupportedOperationException`. Use `Affines.someWithRemove()` instead of `Affines.some()` when you need removal support.
~~~

---

## Composing Affines

Affines compose with other optics following precise rules:

<!-- verify -->
```java
// Affine.andThen(Affine) = Affine
Affine<A, C> withAffine = affineAB.andThen(affineBC);

// Affine.andThen(Lens) = Affine
Affine<A, C> withLens = affineAB.andThen(lensBC);

// Affine.andThen(Prism) = Affine
Affine<A, C> withPrism = affineAB.andThen(prismBC);

// Affine.andThen(Iso) = Affine
Affine<A, C> withIso = affineAB.andThen(isoBC);

// Affine.andThen(Traversal) = Traversal
Traversal<A, C> withTraversal = affineAB.andThen(traversalBC);
```

### Deep Optional Access Example

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/AffineBook.java:deep_optional}}
```

---

## Factory Methods

The `Affine` interface provides factory methods for common construction patterns:

### From Getter and Setter

<!-- verify -->
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

<!-- verify -->
```java
// Compose a Lens and Prism into an Affine
Affine<A, C> fromLens = Affine.fromLensAndPrism(
    lensAB,   // Lens<A, B>
    prismBC   // Prism<B, C>
);

// Compose a Prism and Lens into an Affine
Affine<A, C> fromPrism = Affine.fromPrismAndLens(
    prismAB,  // Prism<A, B>
    lensBC    // Lens<B, C>
);
```

---

## When to Use Affines vs Other Optics

### Use Affine When

* **Optional fields** in records or classes (`Optional<T>`)
* **Nullable properties** in legacy or interop code
* **Conditional field access** that may or may not exist
* **Lens + Prism compositions** where you need the precise type

<!-- verify -->
```java
// Perfect for optional record fields
@GenerateLenses
record ApiConfig(Optional<String> apiKey) {}

Affine<ApiConfig, String> apiKeyAffine =
    ApiConfigLenses.apiKey().andThen(Prisms.some());

Optional<String> key = apiKeyAffine.getOptional(new ApiConfig(Optional.of("secret")));
```

### Use Lens When

* The field is **always present** (guaranteed to exist)
* You're working with **product types** (records, classes)

<!-- verify -->
```java
// Field always exists
record Point(int x, int y) {}
Lens<Point, Integer> xLens = Lens.of(Point::x, (p, x) -> new Point(x, p.y()));
```

### Use Prism When

* Working with **sum types** (sealed interfaces, enums)
* You need to **construct** the whole from a part
* Type-safe **variant matching**

<!-- verify -->
```java
// Sum type handling
@GeneratePrisms
sealed interface Shape permits Circle, Rectangle {}

Prism<Shape, Circle> circlePrism = ShapePrisms.circle();
Shape circle = circlePrism.build(new Circle(5.0, "red"));  // Can construct!
```

### Use Traversal When

* Focusing on **multiple elements** (lists, sets)
* You need to work with **collections**

<!-- verify -->
```java
// Multiple elements
Traversal<List<String>, String> listTraversal = Traversals.forList();
List<String> upper = Traversals.modify(listTraversal, String::toUpperCase, names);
```

---

## Common Pitfalls

### Don't Do This

<!-- verify -->
```java
// Overly complex: manual Optional handling
Optional<String> getNestedValue(Config config) {
    return config.database()
        .flatMap(db -> db.connection())
        .flatMap(conn -> conn.timeout())
        .map(Object::toString);
}

// Unsafe: assuming presence without checking
String hostOf(Config config) {
    return config.database().get().host();  // NoSuchElementException!
}

// Verbose: repeated null checks, on a legacy model whose fields may be null
String nicknameOf(LegacyReview review) {
    if (review.author() != null && review.author().nickname() != null) {
        return review.author().nickname();
    }
    return "";
}
```

### Do This Instead

<!-- verify -->
```java
// Clean: compose affines for deep access
Affine<Config, Integer> timeoutAffine =
    databaseAffine
        .andThen(connectionAffine)
        .andThen(timeoutLens)
        .andThen(Affines.some());

Optional<String> timeout = timeoutAffine.mapOptional(Object::toString, config);

// Safe: affine handles absence gracefully
String value = databaseAffine.getOrElse(defaultSettings, config).host();

// Composable: build reusable optics
Affine<Review, String> nicknameAffine = ReviewOptics.AUTHOR_NICKNAME;
Optional<String> nickname = nicknameAffine.getOptional(review);
```

---

## The Affine Laws

Every affine the library builds satisfies these three laws, with one exception: `TraversalPath.headOption()` reads the first element and writes every element, so Get-Set fails when the elements differ.

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

### Set-Get Law
When the focus is present, setting a value and then getting returns that value:
```java
// Whenever affine.getOptional(s) is present:
affine.getOptional(affine.set(a, s)) == Optional.of(a)
```

### When the focus is absent {#when-the-focus-is-absent}
Neither Get-Set nor Set-Get covers `set` on an absent focus, and affines differ there. One whose last step can build the value writes it, provided every step before that is present: `Affines.some().set("x", Optional.empty())` returns `Optional.of("x")`. A `Lens.andThen(Prism)` affine likewise replaces whatever variant is present with the one the prism builds. One that cannot build, such as an index past the end of a list, returns the structure unchanged, and so does a path whose earlier step is absent. The [Focus DSL page](focus_dsl.md#affinepath-zero-or-one-element) gives the rule by position along a path. When you mean "only if it is there", use `modify`: it never writes to an absent focus.

Two weaker laws do hold on an absent focus for every affine: `modify` changes nothing, and `set` either changes nothing or writes a value that reads back. Set-Set holds there too. `hkj-test`'s `AffineLaws.assertAffineLaws` checks all of these as well as the three laws on a present focus. For an affine that must also leave an absent focus alone on `set`, add `AffineLaws.assertSetNoOpWhenAbsent`.

---

## Real-World Example: Configuration Management

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/ConfigOptics.java:config_model}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/affine/ConfigOptics.java:config_optics}}
```

---

~~~admonish info title="Key Takeaways"
* **An Affine focuses on exactly zero or one value**: `getOptional` may come back empty, and updates go through an existing structure (there is no `build` from nothing)
* **It is the honest type for Lens-then-Prism**: the composition can never produce more than one focus, and Affine says so more precisely than Traversal would
* **Optional fields without the flatMap chains**: compose affines through nested `Optional`s and the absence handling is done once, in the optic
* **Prism constructs, Affine only updates**: reach for a Prism on sum types you can build; reach for an Affine on optional fields inside product types
~~~

~~~admonish tip title="See Also"
- [Prisms](prisms.md): the constructing sibling for sum types
- [Composition Rules](composition_rules.md): why `Lens.andThen(Prism) = Affine`, and everything else
- [Coupled Fields](coupled_fields.md): when sibling fields must change together
- [Production Readiness](production_readiness.md#prisms-and-affines): what an affine costs when its focus is absent, and when to cache a composed optic
~~~

---

~~~admonish tip title="Further Reading"
- **Monocle Optional**: [Scala's Affine](https://www.optics.dev/Monocle/docs/optics/optional) - Monocle uses "Optional" for the same concept
- **Baeldung**: [Handling Optionality in Java](https://www.baeldung.com/java-optional) - Guide to Java Optional, the underlying type Affine often works with
~~~

~~~admonish note title="Terminology Note"
In some functional programming libraries (notably Scala's Monocle), the Affine optic is called an **Optional**. This can cause confusion with Java's `java.util.Optional`. In higher-kinded-j, we use the term "Affine" to avoid this ambiguity whilst maintaining mathematical precision.
~~~

~~~admonish info title="Hands-On Learning"
Practise affine basics in [Tutorial 04: Affine Basics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial04_AffineBasics.java) (7 exercises).
~~~

---

**Previous:** [Advanced Prism Patterns: Recipes](advanced_prism_patterns_recipes.md)
**Next:** [Isomorphisms](iso.md)
