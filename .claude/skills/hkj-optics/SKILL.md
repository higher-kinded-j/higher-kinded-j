---
name: hkj-optics
description: "Generate and compose HKJ optics: @GenerateLenses, @GenerateFocus, @GenerateTraversals, @GeneratePrisms, @ImportOptics, @OpticsSpec. Focus DSL navigation, FocusPath, AffinePath, TraversalPath, Lens, Prism, Iso, deep immutable record updates, collection navigation"
---

# Higher-Kinded-J Optics

You are helping a developer use HKJ's optics system for type-safe immutable data navigation. The library generates optics from annotations on Java records and sealed interfaces.

## When to load supporting files

- If the user asks about **which optics compose with which**, load `reference/composition-rules.md`
- If the user asks about **recipes for specific data patterns** (nested optionals, sum types, collections), load `reference/cookbook.md`
- If the user asks about **custom container types, SPI, TraversableGenerator**, load `reference/container-types.md`
- If the user asks about **indexed optics** (position-aware transforms), load `reference/indexed-optics.md`
- For **bridging optics with effects** (.focus() on paths, toEitherPath), suggest `/hkj-bridge`
- For **choosing Path types or Effect Path API**, suggest `/hkj-guide`

---

## Annotations Reference

| Annotation | Place On | Generates |
|------------|----------|-----------|
| `@GenerateLenses` | `record` | `{Record}Lenses` class with `Lens<S, A>` for each field |
| `@GenerateFocus` | `record` (requires `@GenerateLenses`) | `{Record}Focus` class with `FocusPath`/`AffinePath`/`TraversalPath` builders |
| `@GenerateTraversals` | `record` with collection fields | `{Record}Traversals` with `Traversal<S, A>` for collection fields |
| `@GeneratePrisms` | `sealed interface` | `{Interface}Prisms` class with `Prism<S, A>` for each permitted record |
| `@GenerateIsos` | `record` with single field | `{Record}Isos` class with `Iso<S, A>` |
| `@GenerateGetters` | `record` | `{Record}Getters` class with `Getter<S, A>` for each field |
| `@GenerateSetters` | `record` | `{Record}Setters` class with `Setter<S, A>` for each field |
| `@GenerateFolds` | `record` | `{Record}Folds` class with `Fold<S, A>` for each field |
| `@GenerateForComprehensions` | `record` | For-comprehension-aware traversals |
| `@ImportOptics` | `package-info.java` or `interface extends OpticsSpec<S>` | Lenses for external types (JDK, Jackson, etc.) via auto-detection |
| `OpticsSpec<S>` | Marker interface on type with `@ImportOptics` | Fine-grained optics for external types with custom copy strategies |

### Copy Strategy Annotations (for `OpticsSpec`)

| Annotation | When To Use | Example |
|------------|-------------|---------|
| `@ViaBuilder` | Type has a builder pattern | `@ViaBuilder("toBuilder")` |
| `@Wither` | Type has `withX()` methods | `@Wither` (default, autodetected) |
| `@ViaCopyAndSet` | Mutable type with setters | `@ViaCopyAndSet(copy = "clone")` |
| `@ViaConstructor` | Reconstruct via constructor | `@ViaConstructor` |

---

## Step-by-Step: Setting Up Optics

### 1. Annotate your records

```java
@GenerateLenses
@GenerateFocus
public record User(String name, Address address) {}

@GenerateLenses
@GenerateFocus
public record Address(String city, String postcode, Optional<String> floor) {}
```

### 2. Use generated Focus classes

```java
// FocusPath: exactly one focus (wraps Lens)
FocusPath<User, String> namePath = UserFocus.name();
String name = namePath.get(user);
User renamed = namePath.set("Alice", user);

// Chain through nested records
FocusPath<User, String> cityPath = UserFocus.address().city();
String city = cityPath.get(user);

// AffinePath: zero or one (Optional/nullable fields)
AffinePath<User, String> floorPath = UserFocus.address().floor();
Optional<String> floor = floorPath.getOptional(user);
```

### 3. Chain for deep navigation

```java
// Cross-type navigation with generated navigators (no .via() needed)
TraversalPath<Company, String> allNames =
    CompanyFocus.departments()    // TraversalPath<Company, Department>
        .employees()              // TraversalPath<Company, Employee>
        .name();                  // TraversalPath<Company, String>

List<String> names = allNames.getAll(company);
Company updated = allNames.modifyAll(String::toUpperCase, company);
```

---

## The Three Focus Path Types

```
     FocusPath<S, A>          Exactly one focus (Lens)
           |
     AffinePath<S, A>         Zero or one focus (Affine)
           |
     TraversalPath<S, A>      Zero or more focuses (Traversal)
```

Path types widen automatically when navigating through optional/collection fields.

### FocusPath (Exactly One)

| Method | Return | Description |
|--------|--------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(fn, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract underlying optic |

### AffinePath (Zero or One)

| Method | Return | Description |
|--------|--------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Replace (no-op if absent) |
| `modify(fn, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check if value exists |
| `toAffine()` | `Affine<S, A>` | Extract underlying optic |

### TraversalPath (Zero or More)

| Method | Return | Description |
|--------|--------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `modifyAll(fn, S)` | `S` | Transform all focused values |
| `setAll(A, S)` | `S` | Set all to same value |
| `count(S)` | `int` | Count focused elements |
| `toTraversal()` | `Traversal<S, A>` | Extract underlying optic |

---

## Collection Navigation Methods

| Method | On Path Type | Produces | Description |
|--------|-------------|----------|-------------|
| `.each()` | FocusPath on `List`/`Set` | TraversalPath | Traverse all elements |
| `.each(Each)` | FocusPath on custom container | TraversalPath | Traverse with custom Each instance |
| `.at(index)` | FocusPath on `List` | AffinePath | Access specific index |
| `.some()` | FocusPath on `Optional` | AffinePath | Unwrap Optional |
| `.some(Affine)` | FocusPath on `Either`/`Try`/etc | AffinePath | Unwrap with custom Affine |
| `.nullable()` | FocusPath on `@Nullable` field | AffinePath | Handle null safely |
| `.filter(predicate)` | TraversalPath | TraversalPath | Keep only matching elements |
| `AffinePath.instanceOf(Class)` | Static factory | AffinePath | Safe downcast; compose via `.via()` |

---

## Importing Optics for External Types

### Quick: `@ImportOptics`

```java
// package-info.java
@ImportOptics(java.time.LocalDate.class)
package com.myapp.optics;
import org.higherkindedj.optics.annotations.ImportOptics;
```

Generates `LocalDateLenses` with `year()`, `monthValue()`, `dayOfMonth()` by auto-detecting wither methods.

### Full Control: `OpticsSpec`

`OpticsSpec<S>` is a marker interface. Declare an interface that extends it, annotate the interface with `@ImportOptics`, and declare the optics you want via abstract methods with `@InstanceOf` prisms:

```java
@ImportOptics
public interface JsonNodeOpticSpec extends OpticsSpec<JsonNode> {
    @InstanceOf(ObjectNode.class)
    Prism<JsonNode, ObjectNode> asObject();

    @InstanceOf(ArrayNode.class)
    Prism<JsonNode, ArrayNode> asArray();

    @InstanceOf(TextNode.class)
    Prism<JsonNode, TextNode> asText();
}
```

The processor generates a companion class with the requested prisms and lenses.

---

## Container Type Support

Fields of these types are automatically recognised by `@GenerateFocus`:

**Zero-or-one (-> AffinePath):** `Optional`, `Either`, `Try`, `Validated`, `Maybe`
**Zero-or-more (-> TraversalPath):** `List`, `Set`, `Map`, `T[]`, plus Eclipse Collections, Guava, Vavr, Apache Commons types (23 total via SPI)

Nested containers like `Optional<List<String>>` are detected automatically with composed widening.

---

## Manual Lens Composition

When not using `@GenerateFocus`, compose optics manually with `andThen`:

```java
Lens<User, Address> addressLens = UserLenses.address();
Lens<Address, String> cityLens = AddressLenses.city();

// Compose: User -> Address -> String
Lens<User, String> userCityLens = addressLens.andThen(cityLens);
String city = userCityLens.get(user);
User updated = userCityLens.set("Paris", user);
```

Optics compose according to the hierarchy: Lens + Lens = Lens, Lens + Prism = Affine, Lens + Traversal = Traversal, etc. See `reference/composition-rules.md` for the full matrix.

---

## Common Mistakes

1. **Missing `@GenerateLenses` alongside `@GenerateFocus`**: Focus generation requires lenses. Always use both annotations together.
2. **Wrong composition order**: `A.andThen(B)` means "first focus with A, then within that focus with B." Order matters.
3. **Using `map` instead of `focus`**: On Effect Paths, use `.focus(optic)` to navigate structure; `.map()` transforms the value. See `/hkj-bridge`.
4. **Forgetting to rebuild after adding annotations**: Generated classes appear in `build/generated/sources/annotationProcessor/`.
5. **Using `@GeneratePrisms` on a record**: Prisms are for sealed interfaces (sum types). Use `@GenerateLenses` for records (product types).
