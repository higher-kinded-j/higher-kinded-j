# Optics Tutorial Track

**Duration**: ~90 minutes | **Tutorials**: 9 | **Exercises**: 58

## What You'll Master

By the end of this track, you'll confidently use Optics to:
- Perform deep updates on nested immutable data structures without boilerplate
- Work with collections elegantly using Traversals
- Handle sum types (sealed interfaces) safely with Prisms
- Build sophisticated data transformation pipelines with optic composition
- Create reusable, testable data access patterns

This track teaches you practical techniques for working with complex domain models, JSON structures, and immutable data in Java. You'll write less code and make fewer mistakes.

## The Optics Hierarchy

```
         Optic (base)
           ├── Iso (reversible conversion)
           ├── Lens (product type field access)
           ├── Prism (sum type case matching)
           ├── Traversal (multiple targets)
           ├── Fold (read-only aggregation)
           ├── Getter (read-only access)
           └── Setter (write-only modification)
```

### Tutorial 01: Lens Basics (~8 minutes)
**File**: `Tutorial01_LensBasics.java` | **Exercises**: 7

Learn immutable field access and modification with Lenses—the foundation of the optics library.

**What you'll learn**:
- The three core operations: `get`, `set`, `modify`
- Using `@GenerateLenses` to auto-generate lenses for records
- Manual lens creation with `Lens.of()`
- Lens composition with `andThen`

**Key insight**: A Lens is a first-class getter/setter. You can pass it around, compose it, and reuse it across your codebase.

**Real-world application**: User profile updates, configuration management, any nested record manipulation.

**Links to documentation**: [Lenses Guide](../optics/lenses.md)

---

### Tutorial 02: Lens Composition (~10 minutes)
**File**: `Tutorial02_LensComposition.java` | **Exercises**: 7

Learn to access deeply nested structures by composing simple lenses into powerful paths.

**What you'll learn**:
- Composing lenses with `andThen` to create deep paths
- Updating nested fields in a single expression
- Creating reusable composed lenses
- The associative property: `(a.andThen(b)).andThen(c) == a.andThen(b.andThen(c))`

**Key insight**: Composition is the superpower of optics. Combine small, reusable pieces into complex transformations.

**Real-world application**: Updating deeply nested JSON, modifying complex domain models, configuration tree manipulation.

**Before and After**:
```java
// Without lenses (error-prone, verbose)
var newUser = new User(
    user.name(),
    new Address(
        new Street("New St", user.address().street().number()),
        user.address().city()
    )
);

// With lenses (clear, safe, composable)
var newUser = userToStreetName.set("New St", user);
```

**Links to documentation**: [Composing Optics](../optics/composing_optics.md)

---

### Tutorial 03: Prism Basics (~8 minutes)
**File**: `Tutorial03_PrismBasics.java` | **Exercises**: 7

Learn to work with sum types (sealed interfaces) safely using Prisms.

**What you'll learn**:
- The three core operations: `getOptional`, `build`, `modify`
- Pattern matching on sealed interfaces
- Using `@GeneratePrisms` for automatic generation
- Prism composition

**Key insight**: Prisms are like type-safe `instanceof` checks with built-in modification capability.

**Real-world application**: State machine handling, discriminated unions, API response variants, event processing.

**Example scenario**: An `OrderStatus` can be `Pending`, `Processing`, or `Shipped`. A Prism lets you safely operate on just the `Shipped` variant.

**Links to documentation**: [Prisms Guide](../optics/prisms.md) | [Advanced Prism Patterns](../optics/advanced_prism_patterns.md)

---

### Tutorial 04: Traversal Basics (~10 minutes)
**File**: `Tutorial04_TraversalBasics.java` | **Exercises**: 7

Learn to work with multiple targets simultaneously using Traversals.

**What you'll learn**:
- Operating on all elements in a collection with `modify`, `set`, `getAll`
- Using `@GenerateTraversals` for automatic List traversals
- Manual traversal creation for custom containers
- Filtering traversals with predicates
- Composing traversals to reach nested collections

**Key insight**: A Traversal is like a Lens that can focus on zero, one, or many targets at once.

**Real-world application**: Bulk price updates, applying discounts to cart items, sanitising user input across forms, batch data transformation.

**Example**:
```java
// Update all player scores in a league
Traversal<League, Integer> allScores = leagueToTeams
    .andThen(teamToPlayers)
    .andThen(playerToScore);

League updated = Traversals.modify(allScores, score -> score + 10, league);
```

**Links to documentation**: [Traversals Guide](../optics/traversals.md)

---

### Tutorial 05: Optics Composition (~10 minutes)
**File**: `Tutorial05_OpticsComposition.java` | **Exercises**: 7

Learn the rules and patterns for composing different optic types.

**What you'll learn**:
- Lens + Prism → Optic (not Prism!)
- Lens + Traversal → Traversal
- Traversal + Lens → Traversal
- When the result type "downgrades" to a more general optic

**Key insight**: Composition follows intuitive rules. If any step might fail or have multiple targets, the result reflects that uncertainty.

**Composition Rules**:
| First  | Second    | Result    |
|--------|-----------|-----------|
| Lens   | Lens      | Lens      |
| Lens   | Prism     | Optic     |
| Lens   | Traversal | Traversal |
| Prism  | Prism     | Prism     |
| Traversal | Lens   | Traversal |

**Real-world application**: Complex domain model navigation, API response processing, configuration validation.

**Links to documentation**: [Composing Optics](../optics/composing_optics.md)

---

### Tutorial 06: Generated Optics (~8 minutes)
**File**: `Tutorial06_GeneratedOptics.java` | **Exercises**: 7

Learn to leverage annotation-driven code generation for zero-boilerplate optics.

**What you'll learn**:
- Using `@GenerateLenses` to create lens classes automatically
- Using `@GeneratePrisms` for sealed interface prisms
- Using `@GenerateTraversals` for collection traversals
- Understanding scope limitations of generated code (local vs class-level)
- Combining multiple annotations on one class

**Key insight**: The annotation processor eliminates 90% of the boilerplate. You write the data model, the processor writes the optics.

**Generated code location**: Same package, with `Lenses`/`Prisms`/`Traversals` suffix.

**Links to documentation**: [Lenses](../optics/lenses.md) | [Prisms](../optics/prisms.md) | [Traversals](../optics/traversals.md)

---

### Tutorial 07: Real World Optics (~12 minutes)
**File**: `Tutorial07_RealWorldOptics.java` | **Exercises**: 6

Apply optics to realistic scenarios that mirror production code.

**What you'll learn**:
- User profile management with deep nesting
- API response processing with sum types
- E-commerce order processing with collections
- Data validation pipelines
- Form state management
- Configuration updates

**Key insight**: Real applications combine Lens, Prism, and Traversal in sophisticated ways. The patterns you've learned compose beautifully.

**Scenarios include**:
1. **User Management**: Update user profiles with nested address and contact info
2. **API Integration**: Process API responses with multiple variant types
3. **E-commerce**: Apply discounts, update inventory, modify order status
4. **Validation**: Build reusable validators that navigate data structures

**Links to documentation**: [Optics Examples](../optics/optics_examples.md) | [Auditing Complex Data](../optics/auditing_complex_data_example.md)

---

### Tutorial 08: Fluent Optics API (~12 minutes)
**File**: `Tutorial08_FluentOpticsAPI.java` | **Exercises**: 7

Learn the ergonomic fluent API for Java-friendly optic operations.

**What you'll learn**:
- Source-first static methods: `Lenses.get(lens, source)`
- Collection operations: `Traversals.getAll`, `Traversals.modifyAll`, `Traversals.setAll`
- Query operations: `Traversals.exists`, `Traversals.count`, `Traversals.find`
- Integration with `Either`, `Maybe`, `Validated` for effectful operations
- Real-world form validation with optics + Either

**Key insight**: The fluent API provides discoverable, readable syntax without sacrificing the power of optics.

**Before and After**:
```java
// Traditional style
List<Integer> scores = Traversals.getAll(playerScoresTraversal, league);

// Fluent style (more discoverable)
List<Integer> scores = OpticOps.getAll(league, playerScoresTraversal);
```

**Real-world application**: Form validation, data querying, conditional updates, batch processing.

**Links to documentation**: [Fluent API Guide](../optics/fluent_api.md)

---

### Tutorial 09: Advanced Optics DSL (~15 minutes)
**File**: `Tutorial09_AdvancedOpticsDSL.java` | **Exercises**: 7

Master the Free Monad DSL for building composable optic programs as data structures.

**What you'll learn**:
- Building programs as values with `OpticPrograms`
- Composing programs with `flatMap`
- Conditional workflows with `flatMap` branching
- Multi-step transformations
- Logging interpreter for audit trails
- Validation interpreter for dry-runs
- Real-world order processing pipeline

**Key insight**: The Free Monad DSL separates "what to do" (the program) from "how to do it" (the interpreter). Build the program once, run it many ways.

**Interpreters available**:
- **Direct**: Execute the program immediately
- **Logging**: Record every operation for audit trails
- **Validation**: Dry-run to check for potential issues

**Real-world application**: Auditable workflows, testable business logic, multi-stage data transformations, replayable operations.

**Example**:
```java
// Build a program
Free<OpticOpKind.Witness, Config> program = OpticPrograms
    .get(config, envLens)
    .flatMap(env ->
        env.equals("prod")
            ? OpticPrograms.set(config, debugLens, false)
            : OpticPrograms.pure(config)
    );

// Run with different interpreters
Config result = OpticInterpreters.direct().run(program);
LoggingOpticInterpreter.Log log = OpticInterpreters.logging().run(program);
ValidationResult validation = OpticInterpreters.validating().validate(program);
```

**Links to documentation**: [Free Monad DSL](../optics/free_monad_dsl.md) | [Optic Interpreters](../optics/interpreters.md)

---

## Learning Map

```
Tutorial 01: Lens Basics
    ↓
    └─ get, set, modify single fields
       ↓
Tutorial 02: Lens Composition
    ↓
    └─ andThen for deep paths
       ↓
Tutorial 03: Prism Basics
    ↓
    └─ getOptional, build for sum types
       ↓
Tutorial 04: Traversal Basics
    ↓
    └─ modify collections in bulk
       ↓
Tutorial 05: Optics Composition
    ↓
    └─ Combine different optic types
       ↓
Tutorial 06: Generated Optics
    ↓
    └─ @GenerateLenses, @GeneratePrisms
       ↓
Tutorial 07: Real World Optics
    ↓
    └─ Apply to production scenarios
       ↓
Tutorial 08: Fluent API
    ↓
    └─ Ergonomic Java-friendly syntax
       ↓
Tutorial 09: Free Monad DSL
    ↓
    └─ Build composable programs
```

## Optics Cheat Sheet

### Lens 🔎
- **Focus**: Single required field
- **Operations**: `get`, `set`, `modify`
- **Example**: User's email address
- **Generation**: `@GenerateLenses`

### Prism 🔬
- **Focus**: One variant of a sum type
- **Operations**: `getOptional`, `build`, `modify`
- **Example**: "Shipped" variant of OrderStatus
- **Generation**: `@GeneratePrisms`

### Traversal 🗺️
- **Focus**: Zero or more targets
- **Operations**: `getAll`, `modify`, `set`
- **Example**: All players' scores
- **Generation**: `@GenerateTraversals`

### Iso 🔄
- **Focus**: Reversible conversion
- **Operations**: `get`, `reverseGet`, `modify`
- **Example**: String ↔ Integer conversion

## Running the Tutorials

### Option 1: Run All Tests
```bash
./gradlew :hkj-examples:test --tests "*optics*"
```

### Option 2: Run Individual Tutorials
```bash
./gradlew :hkj-examples:test --tests "*Tutorial01_LensBasics*"
./gradlew :hkj-examples:test --tests "*Tutorial02_LensComposition*"
# ... etc
```

### Option 3: Use Your IDE
Right-click on any tutorial file → "Run 'Tutorial01_LensBasics'"

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
**Problem**: Calling `.get()` on a Prism when the variant doesn't match.

**Solution**: Prisms return `Optional`. Always use `getOptional()`:
```java
Optional<Shipped> shipped = shippedPrism.getOptional(orderStatus);
```

### 3. Generated Code Not Available
**Problem**: Can't find `UserLenses` even though you annotated the class.

**Solution**:
- Rebuild the project to trigger annotation processing
- Check that the annotation is on a top-level or static class, not a local class
- Verify annotation processor is configured in build.gradle

### 4. Wrong Traversal Type After Composition
**Problem**: Expecting a Lens after composing Lens + Prism.

**Solution**: Consult the composition table. Lens + Prism = Optic (the general base type).

### 5. Null Checks in Validation Interpreter
**Problem**: NPE when using Free Monad DSL with validation interpreter.

**Solution**: Validation returns null for `get` operations. Always null-check:
```java
.flatMap(value -> {
    if (value != null && value.equals("expected")) {
        // ...
    }
})
```

## What's Next?

After completing this track:

1. **Apply to Your Domain Models**: Annotate your records with `@GenerateLenses`
2. **Explore Advanced Features**: [Filtered Optics](../optics/filtered_optics.md), [Indexed Optics](../optics/indexed_optics.md)
3. **Study Production Examples**: The [Config Audit Example](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/configaudit/ConfigAuditExample.java)
4. **Learn Core Types**: Understand how `modifyF` uses Higher-Kinded Types

## Getting Help

- **Stuck on an exercise?** Check the [Solutions Guide](solutions_guide.md)
- **Generated code not working?** See the [Troubleshooting Guide](troubleshooting.md)
- **Conceptual confusion?** Re-read the linked documentation
- **Still stuck?** Ask in [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions)

---

Ready to start? Open `Tutorial01_LensBasics.java` and say goodbye to verbose immutable updates! 🚀
