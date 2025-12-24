# Optics: Traversals & Practice Journey

~~~admonish info title="What You'll Learn"
- Working with multiple targets simultaneously using Traversals
- The rules for composing different optic types
- Using annotation-driven code generation
- Applying optics to realistic production scenarios
~~~

**Duration**: ~40 minutes | **Tutorials**: 4 | **Exercises**: 27

**Prerequisites**: [Optics: Lens & Prism Journey](lens_prism_journey.md)

## Journey Overview

This journey takes you from single-element optics to collections and real-world applications. You'll learn to modify multiple elements at once and understand how different optics compose.

```
Traversal (bulk ops) → Composition Rules → Generated Optics → Real World
```

---

## Tutorial 05: Traversal Basics (~10 minutes)
**File**: `Tutorial05_TraversalBasics.java` | **Exercises**: 7

Learn to work with multiple targets simultaneously using Traversals.

**What you'll learn**:
- Operating on all elements in a collection with `modify`, `set`, `getAll`
- Using `@GenerateTraversals` for automatic List traversals
- Manual traversal creation for custom containers
- Filtering traversals with predicates
- Composing traversals to reach nested collections

**Key insight**: A Traversal is like a Lens that can focus on zero, one, or many targets at once.

**Example**:
```java
// Update all player scores in a league
Traversal<League, Integer> allScores = leagueToTeams
    .andThen(teamToPlayers)
    .andThen(playerToScore);

League updated = Traversals.modify(allScores, score -> score + 10, league);
```

**Real-world application**: Bulk price updates, applying discounts to cart items, sanitising user input across forms, batch data transformation.

**Links to documentation**: [Traversals Guide](../../optics/traversals.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial05_TraversalBasics.java)

---

## Tutorial 06: Optics Composition (~10 minutes)
**File**: `Tutorial06_OpticsComposition.java` | **Exercises**: 7

Learn the rules and patterns for composing different optic types.

**What you'll learn**:
- Lens + Lens → Lens (both always succeed)
- Lens + Prism → Affine (might fail, so result is more precise than Traversal)
- Prism + Lens → Affine (might fail, so result is more precise than Traversal)
- Affine + Affine → Affine (chained optional access)
- Lens + Traversal → Traversal
- Prism + Prism → Prism
- When the result type "generalises" based on composition

**Key insight**: Composition follows intuitive rules. If any step might fail, the result reflects that uncertainty. If at most one element can be focused, you get an Affine; if potentially many, a Traversal.

**Composition Rules**:
| First     | Second    | Result    |
|-----------|-----------|-----------|
| Lens      | Lens      | Lens      |
| Lens      | Prism     | Affine    |
| Prism     | Lens      | Affine    |
| Affine    | Affine    | Affine    |
| Lens      | Traversal | Traversal |
| Prism     | Prism     | Prism     |
| Traversal | Lens      | Traversal |

**Real-world application**: Complex domain model navigation, API response processing, configuration validation.

**Links to documentation**: [Composition Rules](../../optics/composition_rules.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial06_OpticsComposition.java)

---

## Tutorial 07: Generated Optics (~8 minutes)
**File**: `Tutorial07_GeneratedOptics.java` | **Exercises**: 7

Learn to leverage annotation-driven code generation for zero-boilerplate optics.

**What you'll learn**:
- Using `@GenerateLenses` to create lens classes automatically
- Using `@GeneratePrisms` for sealed interface prisms
- Using `@GenerateTraversals` for collection traversals
- Understanding scope limitations of generated code (local vs class-level)
- Combining multiple annotations on one class

**Key insight**: The annotation processor eliminates 90% of the boilerplate. You write the data model, the processor writes the optics.

**Example**:
```java
@GenerateLenses
public record User(String name, String email, Address address) {}

// Generated: UserLenses.name(), UserLenses.email(), UserLenses.address()
```

**Generated code location**: Same package, with `Lenses`/`Prisms`/`Traversals` suffix.

**Links to documentation**: [Lenses](../../optics/lenses.md) | [Prisms](../../optics/prisms.md) | [Traversals](../../optics/traversals.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial07_GeneratedOptics.java)

---

## Tutorial 08: Real World Optics (~12 minutes)
**File**: `Tutorial08_RealWorldOptics.java` | **Exercises**: 6

Apply optics to realistic scenarios that mirror production code.

**What you'll learn**:
- User profile management with deep nesting
- API response processing with sum types
- E-commerce order processing with collections
- Data validation pipelines
- Form state management
- Configuration updates

**Key insight**: Real applications combine Lens, Prism, Affine, and Traversal in sophisticated ways. The patterns you've learned compose beautifully.

**Scenarios include**:
1. **User Management**: Update user profiles with nested address and contact info
2. **API Integration**: Process API responses with multiple variant types
3. **E-commerce**: Apply discounts, update inventory, modify order status
4. **Validation**: Build reusable validators that navigate data structures

**Links to documentation**: [Auditing Complex Data](../../optics/auditing_complex_data_example.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial08_RealWorldOptics.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial05_TraversalBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial06_OpticsComposition*"
./gradlew :hkj-examples:test --tests "*Tutorial07_GeneratedOptics*"
./gradlew :hkj-examples:test --tests "*Tutorial08_RealWorldOptics*"
```

---

## Common Pitfalls

### 1. Generated Code Not Available
**Problem**: Can't find `UserLenses` even though you annotated the class.

**Solution**:
- Rebuild the project to trigger annotation processing
- Check that the annotation is on a top-level or static class, not a local class
- Verify annotation processor is configured in build.gradle

### 2. Confusing getAll with get
**Problem**: Using `get()` on a Traversal expecting a single value.

**Solution**: Traversals can have multiple targets. Use `getAll()` for a List:
```java
List<Integer> allScores = Traversals.getAll(scoresTraversal, team);
```

### 3. Forgetting Filter Order Matters
**Problem**: Filtering after traversal gives unexpected results.

**Solution**: Apply filters at the right point in composition:
```java
// Filter THEN traverse
var activeUserEmails = usersTraversal
    .filter(User::isActive)
    .andThen(emailLens);
```

---

## What's Next?

After completing this journey:

1. **Continue to Fluent & Free DSL**: Learn ergonomic APIs and the Free Monad DSL
2. **Jump to Focus DSL**: Use the type-safe path navigation API
3. **Explore Real Examples**: See production patterns in the examples

---

**Previous**: [Optics: Lens & Prism](lens_prism_journey.md)
**Next Journey**: [Optics: Fluent & Free DSL](fluent_free_journey.md)
