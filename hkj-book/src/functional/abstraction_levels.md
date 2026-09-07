# Choosing Your Abstraction Level
## _Applicative, Selective, or Monad?_

> _"It is a capital mistake to theorise before one has data."_
>
> — Arthur Conan Doyle, *A Scandal in Bohemia*

With monads, we theorise about what our program might do, but we cannot know until we run it. Applicatives and Selectives give us the data first, letting us analyse before we execute.

~~~admonish info title="What You'll Learn"
- The expressiveness spectrum from Applicative to Monad
- How each abstraction level trades power for analysability
- When to choose Applicative, Selective, or Monad
- How to leverage static analysis with Free Applicative
- Practical decision criteria for real-world code
~~~

~~~admonish example title="See Example Code"
- [StaticAnalysisExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/StaticAnalysisExample.java) - Analysing programs before execution
- [PermissionCheckingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/PermissionCheckingExample.java) - Pre-execution permission analysis
~~~

---

## The Expressiveness Spectrum

When working with effects in functional programming, you face a fundamental choice: how much power do you need? The answer determines which abstraction to use.

```
Applicative  ←──  Selective  ←──  Monad
    │                 │              │
    ▼                 ▼              ▼
Most analysable   Middle ground   Most expressive
```

Each step to the right gains expressive power but loses the ability to statically analyse what your program will do. This trade-off is not merely academic; it has real consequences for optimisation, testing, and safety.

### The Core Trade-off

| Abstraction | Key Operation | Static Analysis | What You Can Express |
|-------------|---------------|-----------------|----------------------|
| **Applicative** | `ap`, `map2` | Complete | Independent computations |
| **Selective** | `select`, `ifS` | Partial (bounds) | Conditional effects with visible branches |
| **Monad** | `flatMap` | None | Dynamic effect selection |

The crucial insight is that **monadic bind (`flatMap`) allows dynamic selection of effects based on runtime values**. This is powerful, but it means you cannot know which effects will execute without actually running the program.

---

## Understanding Each Level

### Applicative: Independent Computations

With `Applicative`, all computations are independent. Neither can affect which effects the other produces:

<!-- verify -->
```java
// Two independent fetches - neither depends on the other's result
FreeAp<DbOpKind.Witness, UserProfile> program = userFetch.map2(
    postsFetch,
    (user, posts) -> new UserProfile(user, posts)
);

// Before execution, we can:
// - Count all operations
// - Check for dangerous operations
// - Parallelise independent fetches
// - Batch similar queries
```

**Static analysis capability:** Complete. You can inspect the entire program structure before running anything.

**Limitation:** You cannot use the result of one computation to decide what to do next.

### Selective: Conditional Effects

`Selective` sits between Applicative and Monad. It allows conditional execution, but **all branches must be provided upfront**:

<!-- verify -->
```java
// Both branches visible at construction time
Kind<IOKind.Witness, Config> config = selective.ifS(
    isProd,
    prodConfig,   // Known upfront
    devConfig     // Known upfront
);
```

**Static analysis capability:** Partial. You can determine bounds:
- **Under (minimum):** Effects that will definitely execute
- **Over (maximum):** Effects that might possibly execute

**Limitation:** You cannot dynamically construct new effect paths based on runtime values.

### Monad: Dynamic Effect Selection

With `Monad`, each step can decide what to do based on the previous result:

<!-- verify -->
```java
// The function inside flatMap is opaque until runtime
Kind<MaybeKind.Witness, Result> program = monad.flatMap(user -> {
    if (user.isAdmin()) {
        return fetchAdminDashboard(user.id());  // Not known until runtime
    } else {
        return fetchUserDashboard(user.id());   // Not known until runtime
    }
}, getUser(userId));
```

**Static analysis capability:** None. The program structure is constructed dynamically.

**Advantage:** Maximum flexibility. You can express any computational pattern.

---

## Decision Flowchart

When choosing an abstraction, ask yourself these questions:

```
Does step B need the RESULT of step A?
    │
    ├─ No → Use Applicative
    │       (map2, ap, FreeAp)
    │
    └─ Yes → Does step B need to CHOOSE different
             effects based on A's result?
                 │
                 ├─ No, just needs the value → Use Applicative
                 │   (the value flows through, but effect choice is fixed)
                 │
                 └─ Yes, different effects for different values
                         │
                         ├─ All branches known upfront? → Use Selective
                         │   (ifS, whenS, branch)
                         │
                         └─ Branches constructed dynamically? → Use Monad
                             (flatMap)
```

### Practical Examples

**Use Applicative when:**
<!-- verify -->
```java
// Fetching user AND posts - independent operations
FreeAp<DbOpKind.Witness, Dashboard> dashboard =
    fetchUser(id).map2(fetchPosts(id), Dashboard::new);

// Validating multiple fields - accumulate ALL errors
Kind<ValidatedKind.Witness<List<String>>, User> user = applicative.map3(
    VALIDATED.widen(validateName(name)),
    VALIDATED.widen(validateEmail(email)),
    VALIDATED.widen(validateAge(age)),
    (n, e, a) -> new User(n, e, true));
```

**Use Selective when:**
<!-- verify -->
```java
// Feature flag - both branches known, but only one executes
Kind<IOKind.Witness, Unit> maybeTrack = selective.whenS(
    featureFlagEnabled("analytics"),
    trackEvent("page_view")
);

// Environment-based config - both configs defined upfront
Kind<IOKind.Witness, Config> config = selective.ifS(isProd, prodConfig, devConfig);
```

**Use Monad when:**
<!-- verify -->
```java
// User type determines next action - genuinely dynamic
Kind<MaybeKind.Witness, Result> dashboard = monad.flatMap(user -> {
    return switch (user.role()) {
        case "ADMIN" -> fetchAdminDashboard(user.id());
        case "MANAGER" -> fetchManagerDashboard(user.id());
        default -> fetchUserDashboard(user.id());
        // New roles can be added; branches not fixed at construction
    };
}, getUser(userId));
```

---

## Static Analysis in Practice

### Analysing Free Applicative Programs

`FreeAp` captures the structure of applicative computations, enabling analysis before execution:

<!-- verify -->
```java
// Build a program
FreeAp<DbOpKind.Witness, Dashboard> program = buildDashboard(userId);

// Count operations before running
int opCount = FreeApAnalyzer.countOperations(program);
System.out.println("Program will execute " + opCount + " database operations");

// Check for dangerous operations
boolean hasDeletions = FreeApAnalyzer.containsOperation(
    program,
    op -> DbOpHelper.DB_OP.narrow(op) instanceof DbOp.DeleteOp
);

boolean approved =
    !hasDeletions || promptUser("Program contains delete operations. Continue?");
```

### Analysing Selective Programs

For Selective, you can determine bounds on possible effects:

<!-- verify -->
```java
// Collect all effects that might possibly run (Over semantics)
Set<DbOp<?>> possibleEffects = SelectiveAnalyzer.collectPossibleEffects(
    program,
    DbOpHelper.DB_OP::narrow
);

// Get effect bounds (minimum and maximum operations)
SelectiveAnalyzer.EffectBounds bounds = SelectiveAnalyzer.computeEffectBounds(program);
System.out.println("Effects: min=" + bounds.minimum() + ", max=" + bounds.maximum());

// Check for dangerous operations before execution
boolean hasDangerous = SelectiveAnalyzer.containsDangerousEffect(
    program,
    DbOpHelper.DB_OP::narrow,
    op -> op instanceof DbOp.DeleteOp
);
```

---

## Why This Matters

### Optimisation Opportunities

With analysable programs, interpreters can:
- **Parallelise** independent operations
- **Batch** similar database queries
- **Cache** and deduplicate requests
- **Reorder** operations for efficiency

### Safety and Auditing

Before executing a program, you can:
- **Check permissions** for sensitive operations
- **Estimate resource usage** (API calls, database queries)
- **Generate audit logs** of what will happen
- **Request user approval** for dangerous actions

### Testing and Debugging

Analysable programs enable:
- **Structural testing** without execution
- **Dependency visualisation**
- **Dead code detection**
- **Coverage analysis** of effect paths

---

## Higher-Kinded-J Support

Higher-Kinded-J provides comprehensive support for all three levels:

### Applicative

Every effect type has an `Applicative` instance:
- `MaybeApplicative`, `EitherApplicative`, `IOApplicative`
- `ValidatedApplicative` for error accumulation
- `FreeApApplicative` for analysable programs

### Selective

Selective instances for conditional execution:
- `MaybeSelective`, `EitherSelective`, `IOSelective`
- `ValidatedSelective`, `ListSelective`, `ReaderSelective`

### Monad

Full monadic power when needed:
- All effect types have `Monad` instances
- `MonadError` for error handling
- `Free` monad for DSL construction

### Analysis Tools

- `FreeAp.analyse()` for applicative program analysis
- `FreeApAnalyzer` utility class for common analysis patterns
- `SelectiveAnalyzer` for Under/Over analysis of selective programs
- `Const` functor for effect-free information gathering

---

~~~admonish info title="Key Takeaways"
* **The expressiveness spectrum** runs from Applicative (most analysable) through Selective to Monad (most expressive)
* **Choose the least powerful abstraction** that solves your problem; you gain analysability
* **Applicative** is for independent computations; use `FreeAp` for full static analysis
* **Selective** is for conditional effects where all branches are known upfront
* **Monad** is for genuinely dynamic effect selection based on runtime values
* **Static analysis enables** optimisation, safety checks, and better tooling
~~~

---

~~~admonish tip title="See Also"
- [Selective](selective.md) - Conditional effects with static structure
- [Free Applicative](../monads/free_applicative.md) - Building analysable programs
- [Applicative](applicative.md) - Combining independent effects
- [Monad](monad.md) - Chaining dependent computations
~~~

~~~admonish tip title="Further Reading"
- **Chris Penner**: [Monads Are Too Powerful](https://chrispenner.ca/posts/expressiveness-spectrum) - The original article inspiring this guide
- **Andrey Mokhov et al.**: [Selective Applicative Functors](https://dl.acm.org/doi/10.1145/3341694) - Academic paper introducing Selective
~~~

---

**Previous:** [ForState Comprehension](forstate_comprehension.md)
