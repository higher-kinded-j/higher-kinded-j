# Optics IV: Java-Friendly APIs

> *"There was no particular reason to respect the language of the Establishment."*
>
> – Norman Mailer, *The Armies of the Night*

---

Optics originated in Haskell, a language with rather different conventions to Java. Method names like `view`, `over`, and `preview` don't match what Java developers expect, and the parameter ordering (value before source) feels backwards to anyone accustomed to the receiver-first style.

This chapter addresses that gap with three complementary APIs, each suited to different needs.

---

## Which API Should I Use?

~~~admonish tip title="Start Here"
**For most users, the Focus DSL is the recommended starting point.** It provides the most intuitive, IDE-friendly experience for navigating and modifying nested data structures.
~~~

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CHOOSING YOUR API                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐                                                        │
│  │  Focus DSL      │ ◄─── START HERE                                        │
│  │  (Recommended)  │      Path-based navigation with full type safety       │
│  └────────┬────────┘      CompanyFocus.departments().employees().name()     │
│           │                                                                 │
│           │  Need validation-aware modifications?                           │
│           │  Working with Either/Maybe/Validated?                           │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │  Fluent API     │      Static methods + builders for effectful ops       │
│  │  (OpticOps)     │      OpticOps.modifyEither(user, lens, validator)      │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           │  Need audit trails? Dry-runs? Multiple execution strategies?    │
│           ▼                                                                 │
│  ┌─────────────────┐                                                        │
│  │  Free Monad DSL │      Programs as data, interpreted later               │
│  │  (Advanced)     │      OpticPrograms.get(...).flatMap(...)               │
│  └─────────────────┘                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## The Three APIs at a Glance

### Focus DSL: The Primary API

The Focus DSL provides fluent, path-based navigation that mirrors how you think about your data:

```java
// Navigate deeply nested structures with type safety
String city = CompanyFocus.headquarters().city().get(company);

// Modify values at any depth
Company updated = CompanyFocus.departments()
    .employees()
    .salary()
    .modifyAll(s -> s.multiply(1.10), company);
```

Use `@GenerateFocus` on your records to generate path builders automatically. The DSL handles collections (`.each()`), optionals (`.some()`), and indexed access (`.at(index)`) naturally.

**Best for:** Day-to-day optic operations, deep navigation, IDE discoverability, learning optics.

### Fluent API: Validation and Effects

The `OpticOps` class provides static methods and builders for operations that involve validation or effects:

```java
// Validation-aware modification with error accumulation
Validated<List<String>, Order> result = OpticOps.modifyAllValidated(
    order,
    orderPricesTraversal,
    price -> validatePrice(price)
);

// Effectful operations with type classes
Kind<CompletableFutureKind.Witness, User> asyncResult = OpticOps.modifyF(
    user, emailLens, this::fetchVerifiedEmail, cfApplicative
);
```

**Best for:** Validation pipelines, error accumulation, async operations, integration with `Either`/`Maybe`/`Validated`.

### Free Monad DSL: Programs as Data

The Free Monad DSL separates *what* from *how*, letting you build optic programs as data structures that can be interpreted in multiple ways:

```java
// Build a program (no execution yet)
Free<OpticOpKind.Witness, Person> program = OpticPrograms
    .get(person, ageLens)
    .flatMap(age -> OpticPrograms.set(person, ageLens, age + 1));

// Choose how to execute
Person result = OpticInterpreters.direct().run(program);      // Production
LoggingInterpreter logger = OpticInterpreters.logging();
Person logged = logger.run(program);                          // With audit trail
```

**Best for:** Audit trails, dry-runs, testing without side effects, complex conditional workflows.

---

## Programs as Data

The Free Monad DSL separates *what* from *how*:

```
    ┌─────────────────────────────────────────────────────────┐
    │  PROGRAM (Description)                                  │
    │                                                         │
    │   get(age) ─────► flatMap ─────► set(age + 1)          │
    │                                                         │
    │  A data structure representing operations               │
    │  No side effects yet!                                   │
    └────────────────────────┬────────────────────────────────┘
                             │
                             ▼
    ┌────────────────────────┴────────────────────────────────┐
    │                   INTERPRETERS                          │
    │                                                         │
    │  ┌─────────┐   ┌─────────┐   ┌───────────┐             │
    │  │ Direct  │   │ Logging │   │ Validating│             │
    │  │   Run   │   │  Audit  │   │  Dry-Run  │             │
    │  └────┬────┘   └────┬────┘   └─────┬─────┘             │
    │       │             │              │                    │
    │       ▼             ▼              ▼                    │
    │   Person       Audit Log     Valid/Invalid             │
    └─────────────────────────────────────────────────────────┘
```

Same program, different execution strategies.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Focus DSL** – The recommended starting point for most users. Navigate nested structures with a fluent, path-based API that provides full IDE autocomplete support.
- **Fluent API** – When modifications need validation or can fail, the `OpticOps` class provides builders that integrate with Either, Maybe, and Validated for error handling.
- **Validation Integration** – Combine optics with functional error types. Validate all fields in a nested structure and accumulate every error, not just the first.
- **Free Monad DSL** – Describe optic operations as data structures, then interpret them later. Enables dry-runs, audit trails, and testable programs.
- **Interpreters** – Multiple ways to run the same optic program: direct execution for production, logging for debugging, validation for testing.
~~~

---

## Chapter Contents

1. [Focus DSL](focus_dsl.md) - Path-based navigation with type safety and IDE support
2. [Fluent API](fluent_api.md) - Static methods and builders for validation-aware modifications
3. [Free Monad DSL](free_monad_dsl.md) - Building optic programs as composable data
4. [Interpreters](interpreters.md) - Multiple execution strategies for the same program

---

**Next:** [Focus DSL](focus_dsl.md)
