# Optics: Focus DSL Journey

~~~admonish info title="What You'll Learn"
- Type-safe path navigation with automatic type transitions
- The FocusPath, AffinePath, and TraversalPath types
- Effectful modifications with Applicative and Monad
- Aggregating values with Monoid and foldMap
- Kind field support and type class integration
~~~

**Duration**: ~22 minutes | **Tutorials**: 2 | **Exercises**: 18

**Prerequisites**: [Optics: Lens & Prism Journey](lens_prism_journey.md)

## Journey Overview

The Focus DSL provides an ergonomic, type-safe way to navigate nested data structures. Path types automatically widen as you navigate through optional values and collections.

```
FocusPath → via(Prism) → AffinePath → via(Traversal) → TraversalPath
```

This is often the most practical way to work with optics in day-to-day code.

---

## Tutorial 12: Focus DSL Basics (~12 minutes)
**File**: `Tutorial12_FocusDSL.java` | **Exercises**: 10

Learn the Focus DSL for ergonomic, type-safe path navigation through nested data structures.

**What you'll learn**:
- Creating `FocusPath` from a Lens with `FocusPath.of()`
- Composing paths with `via()` for deep navigation
- `AffinePath` for optional values using `some()`
- `TraversalPath` for collections using `each()`
- Accessing specific elements with `at(index)` and `atKey(key)`
- Filtering traversals with `filter()`
- Converting paths with `toLens()`, `asAffine()`, `asTraversal()`

**Key insight**: Path types automatically widen as you navigate. `FocusPath` becomes `AffinePath` through optional values, and becomes `TraversalPath` through collections.

**Path type transitions**:
```
FocusPath (Lens-like: exactly 1)
    │
    ├── via(Lens)     → FocusPath
    ├── via(Prism)    → AffinePath
    ├── via(Affine)   → AffinePath
    └── each()        → TraversalPath

AffinePath (0 or 1)
    │
    ├── via(Lens)     → AffinePath
    ├── via(Prism)    → AffinePath
    └── each()        → TraversalPath

TraversalPath (0 to many)
    │
    └── via(anything) → TraversalPath
```

**Example**:
```java
// Build a path through nested structure
var path = FocusPath.of(companyLens)       // FocusPath<Root, Company>
    .via(departmentsLens)                   // FocusPath<Root, List<Dept>>
    .each()                                 // TraversalPath<Root, Dept>
    .via(managerLens)                       // TraversalPath<Root, Manager>
    .via(emailLens);                        // TraversalPath<Root, String>

// Get all manager emails
List<String> emails = path.getAll(root);

// Update all manager emails
Root updated = path.modify(String::toLowerCase, root);
```

**Links to documentation**: [Focus DSL](../../optics/focus_dsl.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java)

---

## Tutorial 13: Advanced Focus DSL (~10 minutes)
**File**: `Tutorial13_AdvancedFocusDSL.java` | **Exercises**: 8

Master advanced Focus DSL features including type class integration, monoid aggregation, and Kind field navigation.

**What you'll learn**:
- `modifyF()` for effectful modifications with Applicative/Monad
- `foldMap()` for aggregating values using Monoid
- `traverseOver()` for generic collection traversal via Traverse type class
- `modifyWhen()` for conditional modifications
- `instanceOf()` for sum type navigation
- `traced()` for debugging path navigation

**Key insight**: `traverseOver()` bridges the HKT Traverse type class with optics, letting you navigate into `Kind<F, A>` wrapped collections. This is the foundation for automatic Kind field support in `@GenerateFocus`.

**Effectful modifications**:
```java
// Validate while modifying
Either<Error, User> result = path.modifyF(
    EitherMonad.instance(),
    value -> validateAndTransform(value),
    user
);

// Async modification
CompletableFuture<User> futureUser = path.modifyF(
    CFMonad.INSTANCE,
    value -> fetchAndUpdate(value),
    user
);
```

**Aggregation with Monoid**:
```java
// Sum all salaries
Integer total = salaryPath.foldMap(
    IntSumMonoid.INSTANCE,
    salary -> salary,
    company
);

// Collect all names
String allNames = namePath.foldMap(
    StringMonoid.INSTANCE,
    name -> name + ", ",
    team
);
```

**Kind field support**:
```java
// Manual traverseOver for Kind<ListKind.Witness, Role> field
FocusPath<User, Kind<ListKind.Witness, Role>> rolesKindPath = FocusPath.of(userRolesLens);
TraversalPath<User, Role> allRolesPath = rolesKindPath
    .<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

// With @GenerateFocus, this is generated automatically:
// TraversalPath<User, Role> roles = UserFocus.roles();
```

**Links to documentation**: [Kind Field Support](../../optics/kind_field_support.md) | [Foldable and Traverse](../../functional/foldable_and_traverse.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial12_FocusDSL*"
./gradlew :hkj-examples:test --tests "*Tutorial13_AdvancedFocusDSL*"
```

---

## Focus DSL Cheat Sheet

### Path Types

| Type | Focus Count | Created By |
|------|-------------|------------|
| `FocusPath<S,A>` | Exactly 1 | `FocusPath.of(lens)` |
| `AffinePath<S,A>` | 0 or 1 | `.via(prism)`, `.some()` |
| `TraversalPath<S,A>` | 0 to many | `.each()`, `.via(traversal)` |

### Common Operations

| Operation | Available On | Description |
|-----------|--------------|-------------|
| `get(s)` | FocusPath | Get the single value |
| `getOptional(s)` | AffinePath | Get optional value |
| `getAll(s)` | TraversalPath | Get all values as List |
| `set(a, s)` | All | Set value(s) |
| `modify(f, s)` | All | Transform value(s) |
| `modifyF(m, f, s)` | All | Effectful modification |
| `foldMap(m, f, s)` | TraversalPath | Aggregate with Monoid |

### Navigation

| Method | Effect |
|--------|--------|
| `via(lens)` | Navigate through required field |
| `via(prism)` | Navigate to sum type variant (widens to Affine) |
| `some()` | Navigate into Optional (widens to Affine) |
| `each()` | Navigate into collection (widens to Traversal) |
| `at(index)` | Navigate to specific index (widens to Affine) |
| `atKey(key)` | Navigate to map key (widens to Affine) |
| `filter(pred)` | Filter traversal targets |

---

## Common Pitfalls

### 1. Expecting get() on AffinePath
**Problem**: Calling `get()` on an AffinePath when you need `getOptional()`.

**Solution**: AffinePath might have zero elements. Use `getOptional()`:
```java
Optional<String> value = affinePath.getOptional(source);
```

### 2. Type Inference Issues with modifyF
**Problem**: Java can't infer type parameters for `modifyF`.

**Solution**: Explicitly specify the monad instance:
```java
path.<EitherKind.Witness<Error>>modifyF(EitherMonad.instance(), ...)
```

### 3. Forgetting traverseOver for Kind Fields
**Problem**: Can't navigate into `Kind<ListKind.Witness, A>` field.

**Solution**: Use `traverseOver` with the appropriate Traverse instance:
```java
path.traverseOver(ListTraverse.INSTANCE)
```

---

## What's Next?

Congratulations! You've completed the Optics track. You now understand:
- Lens, Prism, Affine, and Traversal
- Optic composition rules
- Generated optics with annotations
- The Fluent API and Free Monad DSL
- The Focus DSL for type-safe navigation

**Recommended next steps**:

1. **Effect API Journey**: Combine optics with Effect paths
2. **Use @GenerateFocus**: Annotate your records for automatic path generation
3. **Study Production Examples**: See [Draughts Game](../../hkts/draughts.md)
4. **Explore Core Types**: Understand the HKT foundation powering `modifyF`

---

**Previous**: [Optics: Fluent & Free DSL](fluent_free_journey.md)
**Next**: [Effect API Journey](../effect/effect_journey.md)
