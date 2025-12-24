# Optics: Lens & Prism Journey

~~~admonish info title="What You'll Learn"
- Accessing and updating fields in immutable records with Lenses
- Composing lenses for deep nested access
- Working with sum types (sealed interfaces) using Prisms
- Handling optional fields precisely with Affines
~~~

**Duration**: ~40 minutes | **Tutorials**: 4 | **Exercises**: 30

## Journey Overview

This journey teaches the fundamental optics: Lens, Prism, and Affine. By the end, you'll never write verbose immutable update code again.

```
Lens (product types) → Lens Composition → Prism (sum types) → Affine (optional)
```

---

## The Optics Hierarchy (Preview)

```
         Lens (single required field)
              ↓
         Prism (one variant of sum type)
              ↓
         Affine (zero or one element)
              ↓
         Traversal (zero or more elements)
```

When you compose a Lens with a Prism, you get an Affine. This journey builds that intuition.

---

## Tutorial 01: Lens Basics (~8 minutes)
**File**: `Tutorial01_LensBasics.java` | **Exercises**: 7

Learn immutable field access and modification with Lenses, the foundation of the optics library.

**What you'll learn**:
- The three core operations: `get`, `set`, `modify`
- Using `@GenerateLenses` to auto-generate lenses for records
- Manual lens creation with `Lens.of()`
- Lens composition with `andThen`

**Key insight**: A Lens is a first-class getter/setter. You can pass it around, compose it, and reuse it across your codebase.

**Before and After**:
```java
// Without lenses (verbose, error-prone)
var updated = new User(user.name(), newEmail, user.address());

// With lenses (clear, composable)
var updated = UserLenses.email().set(newEmail, user);
```

**Real-world application**: User profile updates, configuration management, any nested record manipulation.

**Links to documentation**: [Lenses Guide](../../optics/lenses.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial01_LensBasics.java)

---

## Tutorial 02: Lens Composition (~10 minutes)
**File**: `Tutorial02_LensComposition.java` | **Exercises**: 7

Learn to access deeply nested structures by composing simple lenses into powerful paths.

**What you'll learn**:
- Composing lenses with `andThen` to create deep paths
- Updating nested fields in a single expression
- Creating reusable composed lenses
- The associative property: `(a.andThen(b)).andThen(c) == a.andThen(b.andThen(c))`

**Key insight**: Composition is the superpower of optics. Combine small, reusable pieces into complex transformations.

**Before and After**:
```java
// Without lenses (nightmare)
var newUser = new User(
    user.name(),
    user.email(),
    new Address(
        new Street("New St", user.address().street().number()),
        user.address().city()
    )
);

// With lenses (one line)
var newUser = userToStreetName.set("New St", user);
```

**Real-world application**: Updating deeply nested JSON, modifying complex domain models, configuration tree manipulation.

**Links to documentation**: [Composing Optics](../../optics/composing_optics.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial02_LensComposition.java)

---

## Tutorial 03: Prism Basics (~10 minutes)
**File**: `Tutorial03_PrismBasics.java` | **Exercises**: 9

Learn to work with sum types (sealed interfaces) safely using Prisms.

**What you'll learn**:
- The three core operations: `getOptional`, `build`, `modify`
- Pattern matching on sealed interfaces
- Using `@GeneratePrisms` for automatic generation
- Using `matches()` for type checking and `doesNotMatch()` for exclusion filtering
- The `nearly` prism for predicate-based matching
- Prism composition

**Key insight**: Prisms are like type-safe `instanceof` checks with built-in modification capability.

**Example scenario**: An `OrderStatus` can be `Pending`, `Processing`, or `Shipped`. A Prism lets you safely operate on just the `Shipped` variant.

```java
// Safely extract tracking number only if Shipped
Optional<String> tracking = shippedPrism
    .andThen(trackingLens)
    .getOptional(orderStatus);
```

**Real-world application**: State machine handling, discriminated unions, API response variants, event processing.

**Links to documentation**: [Prisms Guide](../../optics/prisms.md) | [Advanced Prism Patterns](../../optics/advanced_prism_patterns.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial03_PrismBasics.java)

---

## Tutorial 04: Affine Basics (~12 minutes)
**File**: `Tutorial04_AffineBasics.java` | **Exercises**: 7

Learn to work with optional fields and nullable properties using Affines.

**What you'll learn**:
- The core operations: `getOptional`, `set`, `modify`
- Using `Affines.some()` for `Optional<T>` fields
- Why `Lens.andThen(Prism)` produces an Affine, not a Traversal
- Using `matches()` and `getOrElse()` convenience methods
- Composing Affines for deep optional access
- When to use Affine vs Lens vs Prism vs Traversal

**Key insight**: An Affine is more precise than a Traversal when you know there's at most one element. It's what you get when you compose a guaranteed path (Lens) with an uncertain one (Prism).

**Decision guide**:
| Optic | Focus Count | Use Case |
|-------|-------------|----------|
| Lens | Exactly 1 | Required field |
| Prism | 0 or 1 (variant) | Sum type case |
| Affine | 0 or 1 (optional) | Optional field |
| Traversal | 0 to many | Collection |

**Real-world application**: User profiles with optional contact info, configuration with optional sections, nullable legacy fields.

**Links to documentation**: [Affines Guide](../../optics/affine.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial04_AffineBasics.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial01_LensBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial02_LensComposition*"
./gradlew :hkj-examples:test --tests "*Tutorial03_PrismBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial04_AffineBasics*"
```

---

## Common Pitfalls

### 1. Forgetting andThen for Composition
**Problem**: Trying to access nested fields without composing lenses.

**Solution**: Chain lenses with `andThen`:
```java
var userToStreetName = UserLenses.address()
    .andThen(AddressLenses.street())
    .andThen(StreetLenses.name());
```

### 2. Using Prism.get Instead of getOptional
**Problem**: Expecting `get()` on a Prism when the variant doesn't match.

**Solution**: Prisms return `Optional`. Always use `getOptional()`:
```java
Optional<Shipped> shipped = shippedPrism.getOptional(orderStatus);
```

### 3. Expecting Traversal When You Get Affine
**Problem**: Thinking Lens + Prism = Traversal.

**Solution**: Lens + Prism = Affine (zero-or-one, not zero-or-many). Use `asTraversal()` if needed.

---

## What's Next?

After completing this journey:

1. **Continue to Traversals & Practice**: Learn bulk operations on collections
2. **Jump to Focus DSL**: Use the ergonomic path-based API
3. **Explore Real Examples**: See [Auditing Complex Data](../../optics/auditing_complex_data_example.md)

---

**Next Journey**: [Optics: Traversals & Practice](traversals_journey.md)
