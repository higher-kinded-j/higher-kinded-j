# Setters: A Practical Guide

_Change values across a structure, one field or a whole collection, without reading them out._

~~~admonish info title="What You'll Learn"
- Generate setters with `@GenerateSetters`, or build one with `Setter.fromGetSet`
- Compose setters with `andThen`, and with `Setter.forList()` or `forMapValues()`, to change every value in a nested collection
- Run a validating update through `modifyF`, and fix the `UnsupportedOperationException` a `Setter.of` setter throws there
- Decide between a setter, a lens and a traversal for an update
~~~

~~~admonish example title="See Example Code"
[SetterUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/SetterUsageExample.java)
~~~

In the previous guide, we explored **`Getter`** for composable read-only access. Now we turn to its dual: **`Setter`**, a write-only optic that modifies data without necessarily reading it first.

A **`Setter`** is an optic that focuses on transforming elements within a structure. Unlike a `Lens`, which provides both getting and setting, a `Setter` concentrates solely on modification, making it ideal for batch updates, data normalisation, and transformation pipelines where read access isn't required.

It plays the part of `stream().map(f).toList()` put back with a wither, with nothing read out first. Unlike the stream, it composes with other setters, `Setter.forList()` among them, and does the putting back at any depth. [Choosing an optic](optics_intro.md#choosing-an-optic) sets it beside the other optic types.

---

## The Scenario: User Management System

Consider a user management system where you need to perform various modifications:

**The Data Model:**

<!-- verify -->
```java
@GenerateSetters
public record User(String username, String email, int loginCount, UserSettings settings) {}

@GenerateSetters
public record UserSettings(
    String theme, boolean notifications, int fontSize, Map<String, String> preferences) {}

@GenerateSetters
public record Product(String name, BigDecimal price, int stock, List<String> tags) {}

@GenerateSetters
public record Inventory(List<Product> products, String warehouseId) {}
```

**Common Modification Needs:**
* "Normalise all usernames to lowercase"
* "Increment login count after authentication"
* "Apply 10% discount to all products"
* "Restock all items by 10 units"
* "Convert all product names to title case"
* "Set all user themes to dark mode"

A `Setter` makes these modifications type-safe, composable, and expressive.

---

## Setter vs Lens vs Traversal: Understanding the Differences

| Aspect | Setter | Lens | Traversal |
|--------|--------|------|-----------|
| **Focus** | One or more elements | Exactly one element | Zero or more elements |
| **Can read?** | No (typically) | Yes | Yes |
| **Can modify?** | Yes | Yes | Yes |
| **Core operations** | `modify`, `set` | `get`, `set`, `modify` | `modifyF`, `getAll` |
| **Use case** | Write-only pipelines | Read-write field access | Collection traversals |
| **Intent** | "Transform these values" | "Get or set this field" | "Update all these elements" |

**Key Insight**: A `Setter` can be viewed as the write-only half of a `Lens`. It extends `Optic`, enabling composition with other optics and supporting effectful modifications via `modifyF`. Choose `Setter` when you want to emphasise write-only intent or when read access isn't needed.

---

## A Step-by-Step Walkthrough

### Step 1: Creating Setters

#### Using `@GenerateSetters` Annotation

Annotating a record with **`@GenerateSetters`** creates a companion class (e.g., `UserSetters`) containing a `Setter` for each field:

<!-- verify -->
```java
import org.higherkindedj.optics.annotations.GenerateSetters;

@GenerateSetters
public record User(String username, String email, int loginCount, UserSettings settings) {}
```

This generates:
* `UserSetters.username()` → `Setter<User, String>`
* `UserSetters.email()` → `Setter<User, String>`
* `UserSetters.loginCount()` → `Setter<User, Integer>`
* `UserSetters.settings()` → `Setter<User, UserSettings>`

Plus convenience methods:
* `UserSetters.withUsername(user, newUsername)` → `User`
* `UserSetters.withEmail(user, newEmail)` → `User`
* etc.

As with every generator in this chapter, a `targetPackage` attribute relocates the generated class; see [Customising the Generated Package](traversals.md#customising-the-generated-package).

#### Using Factory Methods

Create Setters programmatically:

<!-- verify -->
```java
// Using fromGetSet for single-element focus
Setter<User, String> usernameSetter = Setter.fromGetSet(
    User::username,
    (user, newUsername) -> new User(newUsername, user.email(), user.loginCount(), user.settings()));

// Using of for transformation-based definition
Setter<Person, String> nameSetter = Setter.of(
    f -> person -> new Person(f.apply(person.name()), person.age()));

// Built-in collection setters
Setter<List<Integer>, Integer> listSetter = Setter.forList();
Setter<Map<String, Double>, Double> mapValuesSetter = Setter.forMapValues();
```

### Step 2: Core Setter Operations

#### **`modify(function, source)`**: Transform the Focused Value

Applies a function to modify the focused element:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:modify}}
```

#### **`set(value, source)`**: Replace the Focused Value

Sets all focused elements to a specific value:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:set}}
```

### Step 3: Composing Setters

Chain Setters together for deep modifications:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:compose}}
```

#### Deep Composition Chain

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:deep_chain}}
```

### Step 4: Collection Setters

Higher-Kinded-J provides built-in Setters for collections:

#### **`forList()`**: Modify All List Elements

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:for_list}}
```

#### **`forMapValues()`**: Modify All Map Values

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:for_map_values}}
```

### Step 5: Nested Collection Setters

Compose Setters for complex nested modifications:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:nested}}
```

### Step 6: Effectful Modifications

Setters support effectful modifications via `modifyF`, allowing you to compose modifications that might fail or have side effects:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:modify_f}}
```

#### Sequencing Effects in Collections {#sequencing-effects-in-collections}

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:sequencing}}
```

Each time the effect from `modifyF` runs, it builds a fresh list. So an `IO` gives an equal list on every run, and the `List` applicative gives one list per combination of choices. `forMapValues()` does the same with a map.

### Step 7: Converting to Traversal

Setters can be viewed as Traversals, enabling integration with other optics:

<!-- verify -->
```java
Setter<User, String> nameSetter = Setter.fromGetSet(
    User::username,
    (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

Traversal<User, String> nameTraversal = nameSetter.asTraversal();

// Now you can use Traversal operations
Function<String, Kind<OptionalKind.Witness, String>> toUpper =
    s -> OptionalKindHelper.OPTIONAL.widen(Optional.of(s.toUpperCase()));

Kind<OptionalKind.Witness, User> result =
    nameTraversal.modifyF(toUpper, user, Instances.monadError(optional()));
```

---

## Built-in Helper Setters

### **`identity()`**: Modifies the Source Itself

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:identity}}
```

Useful as a base case in composition or for direct value transformation.

---

## When to Use Setter vs Other Approaches

### Use Setter When

* You need **write-only access** without reading
* You're building **batch transformation** pipelines
* You want **clear modification intent** in your code
* You need **effectful modifications** with validation
* You're performing **data normalisation** across structures

<!-- verify -->
```java
// Good: Batch normalisation
Setter<List<String>, String> listSetter = Setter.forList();
List<String> normalised = listSetter.modify(String::trim, rawStrings);

// Good: Composable deep modification
Setter<Company, String> employeeNamesSetter = companySetter
    .andThen(employeesSetter)
    .andThen(personNameSetter);
```

### Use Lens When

* You need **both reading and writing**
* You want to **get and set** the same field

<!-- verify -->
```java
// Use Lens when you need to read
Lens<User, String> usernameLens = Lens.of(
    User::username,
    (u, name) -> new User(name, u.email(), u.loginCount(), u.settings()));

String current = usernameLens.get(user); // Read
User updated = usernameLens.set("new_name", user); // Write
```

### Use Traversal When

* You need **read operations** (`getAll`) on collections
* You're working with **optional** or multiple focuses

<!-- verify -->
```java
// Use Traversal when you need to extract values too
List<Product> all = Traversals.getAll(productTraversal, inventory); // Read
```

### Use Direct Mutation When

* You're working with **mutable objects** (not recommended in FP)
* **Performance** is absolutely critical

```java
// Direct mutation (only for mutable objects)
user.setUsername("new_name"); // Avoid in functional programming
```

---

## Real-World Use Cases

### Data Normalisation Pipeline

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SettersBook.java:normalise}}
```

### Currency Conversion

<!-- verify -->
```java
// priceSetter, built with fromGetSet as in Step 5
BigDecimal exchangeRate = new BigDecimal("0.92"); // USD to EUR

List<Product> euroProducts = productSetter.modify(
    product -> priceSetter.modify(
        price -> price.multiply(exchangeRate).setScale(2, RoundingMode.HALF_EVEN), product),
    usdProducts);
```

### Batch User Updates

<!-- verify -->
```java
Setter<List<User>, User> usersSetter = Setter.forList();
// loginCountSetter, built with fromGetSet as in Step 2

// Reset all login counts
List<User> resetUsers = usersSetter.modify(
    user -> loginCountSetter.set(0, user),
    users);

// Increment all login counts
List<User> incremented = usersSetter.modify(
    user -> loginCountSetter.modify(count -> count + 1, user),
    users);
```

### Theme Migration

<!-- verify -->
```java
Setter<User, String> userThemeSetter = settingsSetter.andThen(themeSetter);

// Migrate all users to dark mode
List<User> darkModeUsers = usersSetter.modify(
    user -> userThemeSetter.set("dark", user),
    users);
```

---

## Common Pitfalls

### Don't Use `Setter.of()` for Effectful Operations

<!-- verify -->
```java
// Warning: Setter.of() doesn't support modifyF properly
Setter<Person, String> nameSetter = Setter.of(
    f -> person -> new Person(f.apply(person.name()), person.age()));

// This will throw UnsupportedOperationException!
nameSetter.modifyF(validateFn, person, applicative);
```

### Use `fromGetSet()` for Effectful Support

<!-- verify -->
```java
// Correct: fromGetSet supports modifyF
Setter<Person, String> nameSetter = Setter.fromGetSet(
    Person::name,
    (p, name) -> new Person(name, p.age()));

// Works correctly
nameSetter.modifyF(validateFn, person, applicative);
```

### Don't Forget Immutability

```java
// Wrong: Modifying in place (if mutable)
setter.modify(obj -> { obj.setValue(newValue); return obj; }, source);
```

### Always Return New Instances

<!-- verify -->
```java
// Correct: Return new immutable instance
Setter<Product, BigDecimal> priceSetter = Setter.fromGetSet(
    Product::price,
    (p, price) -> new Product(p.name(), price, p.stock(), p.tags()));
```

---

## Complete, Runnable Example

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SetterExample.java:imports}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/setters/SetterExample.java:setter_example}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-setters-example-output.txt.golden}}
```

---

~~~admonish info title="Key Takeaways"
* **A setter is write-only by construction**: the modification function sees each focused value, but the caller never reads one out
* **Compose for depth, `forList`/`forMapValues` for breadth**: deep nested updates and bulk collection rewrites use the same `andThen` chains
* **Build with `fromGetSet` when effects matter**: `Setter.of` cannot support `modifyF`; the get-set form can
* **Normalisation pipelines are the sweet spot**: trimming, lower-casing, and currency conversion across a whole structure in one pass
* **Reach for `Lens` or `Traversal` when you also read**: a Setter documents pure write intent in the type
~~~

~~~admonish tip title="See Also"
- [Getters](getters.md): the read-only mirror of this page
- [Traversals](traversals.md): when bulk modification also needs `getAll` and queries
- [Lenses](lenses.md): when the same field needs reading and writing
- [Production Readiness](production_readiness.md#collection-optics): what `Setter.forList()` and `forMapValues()` build, and when to cache a composed optic
~~~

---

**Previous:** [Getters](getters.md)
**Next:** [Common Data Structures](common_data_structure_traversals.md)
