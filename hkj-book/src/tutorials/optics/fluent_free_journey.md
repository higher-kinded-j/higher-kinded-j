# Optics: Fluent & Free DSL Journey

~~~admonish info title="What You'll Learn"
- The ergonomic fluent API for Java-friendly optic operations
- Advanced prism patterns including predicate-based matching
- Building composable optic programs with the Free Monad DSL
- Interpreting programs for logging, validation, and direct execution
~~~

**Duration**: ~37 minutes | **Tutorials**: 3 | **Exercises**: 22

**Prerequisites**: [Optics: Traversals & Practice Journey](traversals_journey.md)

## Journey Overview

This journey covers advanced optics patterns: the fluent API for ergonomic usage, advanced prism techniques, and the powerful Free Monad DSL for building interpretable optic programs.

```
Fluent API (ergonomics) → Advanced Prisms → Free Monad DSL (programs as data)
```

---

## Tutorial 09: Fluent Optics API (~12 minutes)
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
String name = lens.get(user);

// Fluent style (more discoverable in IDE)
String name = Lenses.get(lens, user);

// Query a collection
boolean hasAdmin = Traversals.exists(rolesTraversal, role -> role.isAdmin(), user);
```

**Real-world application**: Form validation, data querying, conditional updates, batch processing.

**Links to documentation**: [Fluent API Guide](../../optics/fluent_api.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial09_FluentOpticsAPI.java)

---

## Tutorial 10: Advanced Prism Patterns (~10 minutes)
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

**The `nearly` prism**:
```java
// Match values that satisfy a predicate
Prism<Integer, Integer> positive = Prisms.nearly(0, n -> n > 0);
positive.getOptional(5);   // Optional.of(5)
positive.getOptional(-3);  // Optional.empty()
```

**Real-world application**: API response processing, configuration with optional sections, validation pipelines.

**Links to documentation**: [Advanced Prism Patterns](../../optics/advanced_prism_patterns.md) | [Composition Rules](../../optics/composition_rules.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial10_AdvancedPrismPatterns.java)

---

## Tutorial 11: Free Monad DSL (~15 minutes)
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

**Interpreters available**:
- **Direct**: Execute the program immediately
- **Logging**: Record every operation for audit trails
- **Validation**: Dry-run to check for potential issues

**Real-world application**: Auditable workflows, testable business logic, multi-stage data transformations, replayable operations.

**Links to documentation**: [Free Monad DSL](../../optics/free_monad_dsl.md) | [Optic Interpreters](../../optics/interpreters.md)

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial11_AdvancedOpticsDSL.java)

---

## Running the Tutorials

```bash
./gradlew :hkj-examples:test --tests "*Tutorial09_FluentOpticsAPI*"
./gradlew :hkj-examples:test --tests "*Tutorial10_AdvancedPrismPatterns*"
./gradlew :hkj-examples:test --tests "*Tutorial11_AdvancedOpticsDSL*"
```

---

## Common Pitfalls

### 1. Null Checks in Validation Interpreter
**Problem**: NPE when using Free Monad DSL with validation interpreter.

**Solution**: Validation returns null for `get` operations. Always null-check:
```java
.flatMap(value -> {
    if (value != null && value.equals("expected")) {
        // ...
    }
})
```

### 2. Overusing the Free Monad DSL
**Problem**: Using Free for simple operations where direct optics suffice.

**Solution**: Use Free Monad DSL when you need:
- Audit trails
- Dry-run validation
- Multiple interpretations of the same program
- Testable, mockable optic workflows

### 3. Forgetting to Run the Interpreter
**Problem**: Building a Free program but never interpreting it.

**Solution**: Free programs are lazy data. Call `interpreter.run(program)` to execute.

---

## What's Next?

After completing this journey:

1. **Continue to Focus DSL**: Learn the type-safe path navigation API
2. **Combine with Effect API**: Use optics with Effect paths for powerful workflows
3. **Study Production Examples**: See [Draughts Game](../../hkts/draughts.md) for complex optics usage

---

**Previous:** [Optics: Traversals & Practice](traversals_journey.md)
**Next:** [Optics: Focus DSL](focus_dsl_journey.md)
