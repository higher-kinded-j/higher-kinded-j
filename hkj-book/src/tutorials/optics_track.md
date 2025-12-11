# Optics Tutorial Track

**Sessions**: 7 | **Total Duration**: ~137 minutes | **Tutorials**: 13 | **Exercises**: 93

Each session is designed to fit comfortably into a lunch break (20-25 minutes). Work through them at your own pace, one session per sitting, or combine them for longer learning sessions.

## What You'll Master

By the end of this track, you'll confidently use Optics to:
- Perform deep updates on nested immutable data structures without boilerplate
- Work with collections elegantly using Traversals
- Handle sum types (sealed interfaces) safely with Prisms
- Navigate optional fields precisely with Affines
- Build sophisticated data transformation pipelines with optic composition
- Create reusable, testable data access patterns

This track teaches you practical techniques for working with complex domain models, JSON structures, and immutable data in Java. You'll write less code and make fewer mistakes.

## The Optics Hierarchy

```
         Optic (base)
           ├── Iso (reversible conversion)
           ├── Lens (product type field access)
           ├── Prism (sum type case matching)
           ├── Affine (optional field access, zero-or-one)
           ├── Traversal (multiple targets)
           ├── Fold (read-only aggregation)
           ├── Getter (read-only access)
           └── Setter (write-only modification)
```

---

## Session 1: Lens Fundamentals (~18 minutes)

*Master the foundation: accessing and updating fields in immutable records*

### Tutorial 01: Lens Basics (~8 minutes)
**File**: `Tutorial01_LensBasics.java` | **Exercises**: 7

Learn immutable field access and modification with Lenses, the foundation of the optics library.

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

## Session 2: Sum Types and Optional Fields (~22 minutes)

*Handle variants and missing data with type safety*

### Tutorial 03: Prism Basics (~10 minutes)
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

**Real-world application**: State machine handling, discriminated unions, API response variants, event processing.

**Example scenario**: An `OrderStatus` can be `Pending`, `Processing`, or `Shipped`. A Prism lets you safely operate on just the `Shipped` variant.

**Links to documentation**: [Prisms Guide](../optics/prisms.md) | [Advanced Prism Patterns](../optics/advanced_prism_patterns.md)

---

### Tutorial 04: Affine Basics (~12 minutes)
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

**Real-world application**: User profiles with optional contact info, configuration with optional sections, nullable legacy fields.

**Example scenario**: A `UserProfile` has an optional `ContactInfo`, which has an optional phone number. An Affine lets you safely access and update the phone number through both layers of optionality.

**Links to documentation**: [Affines Guide](../optics/affine.md)

---

## Session 3: Collections and Composition (~20 minutes)

*Work with multiple elements and combine optic types*

### Tutorial 05: Traversal Basics (~10 minutes)
**File**: `Tutorial05_TraversalBasics.java` | **Exercises**: 7

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

### Tutorial 06: Optics Composition (~10 minutes)
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

**Links to documentation**: [Composition Rules](../optics/composition_rules.md)

---

## Session 4: Practical Optics (~20 minutes)

*From annotation-driven generation to real-world scenarios*

### Tutorial 07: Generated Optics (~8 minutes)
**File**: `Tutorial07_GeneratedOptics.java` | **Exercises**: 7

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

### Tutorial 08: Real World Optics (~12 minutes)
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

**Links to documentation**: [Auditing Complex Data](../optics/auditing_complex_data_example.md)

---

## Session 5: Fluent APIs and Advanced Patterns (~22 minutes)

*Ergonomic syntax and sophisticated prism techniques*

### Tutorial 09: Fluent Optics API (~12 minutes)
**File**: `Tutorial09_FluentOpticsAPI.java` | **Exercises**: 7

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

### Tutorial 10: Advanced Prism Patterns (~10 minutes)
**File**: `Tutorial10_AdvancedPrismPatterns.java` | **Exercises**: 8

Master advanced prism techniques including predicate-based matching and cross-optic composition.

**What you'll learn**:
- The `nearly` prism for predicate-based matching (complement to `only`)
- Using `doesNotMatch` for exclusion filtering
- Lens + Prism = Affine composition pattern
- Prism + Lens = Affine composition pattern
- Chaining compositions with `lens.asTraversal()`

**Key insight**: Cross-optic composition lets you navigate complex data structures that mix product types, sum types, and optional values.

**Composition patterns**:
```java
// Navigate through optional field
Lens<Config, Optional<Database>> dbLens = ...;
Prism<Optional<Database>, Database> somePrism = Prisms.some();
Affine<Config, Database> dbAffine = dbLens.andThen(somePrism);

// Access field within sum type variant
Prism<Response, Success> successPrism = ...;
Lens<Success, Data> dataLens = ...;
Affine<Response, Data> dataAffine = successPrism.andThen(dataLens);
```

**Real-world application**: API response processing, configuration with optional sections, validation pipelines.

**Links to documentation**: [Advanced Prism Patterns](../optics/advanced_prism_patterns.md) | [Composition Rules](../optics/composition_rules.md)

---

## Session 6: The Free Monad DSL (~15 minutes)

*Build composable optic programs as data structures*

### Tutorial 11: Advanced Optics DSL (~15 minutes)
**File**: `Tutorial11_AdvancedOpticsDSL.java` | **Exercises**: 7

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

## Session 7: Focus DSL (~22 minutes)

*Type-safe path navigation with automatic type transitions*

### Tutorial 12: Focus DSL Basics (~12 minutes)
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
FocusPath → via(Prism) → AffinePath → via(Traversal) → TraversalPath
         → via(Affine) →            → via(Lens)      →
         → each()      →            → each()         →
```

**Links to documentation**: [Focus DSL](../optics/focus_dsl.md)

---

### Tutorial 13: Advanced Focus DSL (~10 minutes)
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

**Real-world application**: Processing Kind-wrapped collections, aggregate computations, conditional business logic, debugging complex path compositions.

**Example**:
```java
// Manual traverseOver for Kind<ListKind.Witness, Role> field
FocusPath<User, Kind<ListKind.Witness, Role>> rolesKindPath = FocusPath.of(userRolesLens);
TraversalPath<User, Role> allRolesPath = rolesKindPath
    .<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

// With @GenerateFocus, this is generated automatically:
// TraversalPath<User, Role> roles = UserFocus.roles();
```

**Links to documentation**: [Kind Field Support](../optics/kind_field_support.md) | [Foldable and Traverse](../functional/foldable_and_traverse.md)

---

## Learning Map

```
Session 1: Lens Fundamentals
    ↓
    └─ Tutorial 01: get, set, modify single fields
       ↓
       └─ Tutorial 02: andThen for deep paths
          ↓
Session 2: Sum Types and Optional Fields
    ↓
    └─ Tutorial 03: getOptional, build for sum types
       ↓
       └─ Tutorial 04: Affines for optional fields
          ↓
Session 3: Collections and Composition
    ↓
    └─ Tutorial 05: modify collections in bulk
       ↓
       └─ Tutorial 06: Combine different optic types
          ↓
Session 4: Practical Optics
    ↓
    └─ Tutorial 07: @GenerateLenses, @GeneratePrisms
       ↓
       └─ Tutorial 08: Apply to production scenarios
          ↓
Session 5: Fluent APIs and Advanced Patterns
    ↓
    └─ Tutorial 09: Ergonomic Java-friendly syntax
       ↓
       └─ Tutorial 10: nearly, doesNotMatch, cross-optic composition
          ↓
Session 6: The Free Monad DSL
    ↓
    └─ Tutorial 11: Build composable programs
       ↓
Session 7: Focus DSL
    ↓
    └─ Tutorial 12: Type-safe path navigation
       ↓
       └─ Tutorial 13: Kind fields and type class integration
```

## Optics Cheat Sheet

### Lens
- **Focus**: Single required field
- **Operations**: `get`, `set`, `modify`
- **Example**: User's email address
- **Generation**: `@GenerateLenses`

### Prism
- **Focus**: One variant of a sum type
- **Operations**: `getOptional`, `build`, `modify`
- **Example**: "Shipped" variant of OrderStatus
- **Generation**: `@GeneratePrisms`

### Affine
- **Focus**: Zero or one element (optional field)
- **Operations**: `getOptional`, `set`, `modify`
- **Example**: User's optional phone number
- **Creation**: `Lens.andThen(Prism)` or `Affine.of()`

### Traversal
- **Focus**: Zero or more targets
- **Operations**: `getAll`, `modify`, `set`
- **Example**: All players' scores
- **Generation**: `@GenerateTraversals`

### Iso
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

### 3. Expecting Traversal When You Get Affine
**Problem**: Thinking Lens + Prism = Traversal.

**Solution**: Lens + Prism = Affine (zero-or-one, not zero-or-more). This is more precise. Use `asTraversal()` if you need a Traversal.

### 4. Generated Code Not Available
**Problem**: Can't find `UserLenses` even though you annotated the class.

**Solution**:
- Rebuild the project to trigger annotation processing
- Check that the annotation is on a top-level or static class, not a local class
- Verify annotation processor is configured in build.gradle

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

1. **Use Focus DSL with @GenerateFocus**: Annotate your records with `@GenerateFocus` for automatic path generation
2. **Explore Kind Field Support**: See [Kind Field Support](../optics/kind_field_support.md) for automatic `Kind<F, A>` field handling
3. **Explore Advanced Features**: [Filtered Optics](../optics/filtered_optics.md), [Indexed Optics](../optics/indexed_optics.md)
4. **Study Production Examples**: The [Kind Field Focus Example](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/KindFieldFocusExample.java)
5. **Learn Core Types**: Understand how `modifyF` uses Higher-Kinded Types

## Getting Help

- **Stuck on an exercise?** Check the [Solutions Guide](solutions_guide.md)
- **Generated code not working?** See the [Troubleshooting Guide](troubleshooting.md)
- **Conceptual confusion?** Re-read the linked documentation
- **Still stuck?** Ask in [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions)

---

Ready to start? Open `Tutorial01_LensBasics.java` and say goodbye to verbose immutable updates!

---

**Previous:** [Core Types Track](coretypes_track.md)
**Next:** [Solutions Guide](solutions_guide.md)
