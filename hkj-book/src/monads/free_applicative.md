# Free Applicative
## _Independent Computations and Parallel Composition_

~~~admonish info title="What You'll Learn"
- The difference between Free Monad (sequential) and Free Applicative (independent)
- When to choose Free Applicative over Free Monad
- Building programs with independent computations
- Interpreting Free Applicative programs with natural transformations
- Enabling parallel execution, batching, and static analysis
- Validation patterns that accumulate all errors
~~~

~~~admonish title="Hands On Practice"
[Tutorial10_FreeApplicative.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial10_FreeApplicative.java)
~~~

~~~admonish example title="See Example Code"
- [FreeApTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free_ap/FreeApTest.java) - Comprehensive test suite
- [FreeApApplicativeTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free_ap/FreeApApplicativeTest.java) - Applicative law verification
- [FreeApplicativeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/FreeApplicativeExample.java) - Practical examples
- [StaticAnalysisExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/StaticAnalysisExample.java) - Analysing programs before execution
- [PermissionCheckingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/free_ap/PermissionCheckingExample.java) - Pre-execution permission analysis
~~~

## Purpose

The **Free Applicative** (`FreeAp`) is the applicative counterpart to the [Free Monad](free_monad.md). Whilst the Free Monad captures **sequential, dependent** computations, Free Applicative captures **independent** computations that can potentially run in parallel.

### The Key Distinction

Consider fetching a user and their posts:

**With Free Monad (sequential, dependent):**
<!-- verify -->
```java
// Each step depends on the previous result
Free<DbOpKind.Witness, UserProfile> program =
    getUser(userId)
        .flatMap(user ->              // Must wait for user
            getPosts(user.id())       // Uses user.id() from previous step
                .flatMap(posts ->
                    Free.pure(new UserProfile(user, posts))));
```

**With Free Applicative (independent):**
<!-- verify -->
```java
// Both fetches are independent - neither needs the other's result
FreeAp<DbOpKind.Witness, UserProfile> program =
    FreeAp.lift(DB_OP.widen(new DbOp.GetUser(userId)))
        .map2(
            FreeAp.lift(DB_OP.widen(new DbOp.GetPosts(userId))),  // Independent of GetUser
            UserProfile::new
        );
```

The Free Applicative version makes it explicit that the two fetches are **independent**. A smart interpreter can execute them in parallel or batch them into a single database query.

## Free Monad vs Free Applicative

| Aspect | Free Monad | Free Applicative |
|--------|------------|------------------|
| **Composition** | Sequential, dependent | Independent, parallel |
| **Core operation** | `flatMap: A -> Free[F, B]` | `ap: FreeAp[F, A->B] x FreeAp[F, A]` |
| **Structure** | Tree (one branch at a time) | DAG (multiple independent branches) |
| **Static analysis** | Cannot see full structure ahead | Full structure visible before interpretation |
| **Parallelism** | Not possible (each step depends on previous) | Natural fit (independent computations) |
| **Use case** | Workflows with conditional logic | Validation, parallel fetches, batching |

~~~admonish note title="Choosing Between Them"
Ask yourself: "Does step B need the **result** of step A?"
- **Yes** -> Use Free Monad (`flatMap`)
- **No** -> Use Free Applicative (`map2`, `map3`, etc.)
~~~

## Core Structure

`FreeAp<F, A>` is a sealed interface with three constructors:

```java
public sealed interface FreeAp<F, A> permits FreeAp.Pure, FreeAp.Lift, FreeAp.Ap {

    // A completed computation with a value
    record Pure<F, A>(A value) implements FreeAp<F, A> {}

    // A suspended single operation in F
    record Lift<F, A>(Kind<F, A> fa) implements FreeAp<F, A> {}

    // Application: independent function and argument computations
    record Ap<F, X, A>(
        FreeAp<F, Function<X, A>> ff,  // Computation producing a function
        FreeAp<F, X> fa                 // Computation producing a value
    ) implements FreeAp<F, A> {}
}
```

The crucial insight is in `Ap`: both `ff` and `fa` are **independent**. Neither depends on the other's result, which is what enables parallelism and static analysis.

## Basic Usage

### Creating FreeAp Values

<!-- verify -->
```java
// Pure value (no effects)
FreeAp<DbOpKind.Witness, Integer> pure = FreeAp.pure(42);

// Lift a single instruction: widen it into the Kind first
FreeAp<DbOpKind.Witness, User> userFetch =
    FreeAp.lift(DB_OP.widen(new DbOp.GetUser(userId)));

// Map over a FreeAp
FreeAp<DbOpKind.Witness, String> userName = userFetch.map(User::name);
```

### Combining Independent Computations

The `map2` method combines two independent computations:

<!-- verify -->
```java
FreeAp<DbOpKind.Witness, User> userFetch = FreeAp.lift(DB_OP.widen(new DbOp.GetUser(1)));
FreeAp<DbOpKind.Witness, List<Post>> postsFetch =
    FreeAp.lift(DB_OP.widen(new DbOp.GetPosts(1)));

// Combine them - these are INDEPENDENT
FreeAp<DbOpKind.Witness, UserProfile> profile = userFetch.map2(
    postsFetch,
    (user, posts) -> new UserProfile(user, posts)
);
```

For more values, chain `map2` or use the applicative instance:

<!-- verify -->
```java
FreeApApplicative<DbOpKind.Witness> applicative = FreeApApplicative.instance();

// The applicative works on the Kind, so widen each FreeAp on the way in
Kind<FreeApKind.Witness<DbOpKind.Witness>, Dashboard> combined = applicative.map3(
    FREE_AP.widen(FreeAp.lift(DB_OP.widen(new DbOp.GetUser(1)))),
    FREE_AP.widen(FreeAp.lift(DB_OP.widen(new DbOp.GetPosts(1)))),
    FREE_AP.widen(FreeAp.lift(DB_OP.widen(new DbOp.GetNotifications(1)))),
    Dashboard::new
);

FreeAp<DbOpKind.Witness, Dashboard> dashboard = FREE_AP.narrow(combined);
```

### Interpreting with foldMap

To execute a `FreeAp` program, provide a natural transformation and an `Applicative` instance:

<!-- verify -->
```java
// Natural transformation: DbOp ~> IO. `Natural`'s method is generic, so it takes an
// anonymous class rather than a lambda.
Natural<DbOpKind.Witness, IOKind.Witness> interpreter =
    new Natural<>() {
        @Override
        @SuppressWarnings("unchecked") // the operation fixes A; the switch cannot say so
        public <A> Kind<IOKind.Witness, A> apply(Kind<DbOpKind.Witness, A> fa) {
            DbOp<A> op = DB_OP.narrow(fa);
            IO<Object> io = switch (op) {
                case DbOp.GetUser g -> IO.delay(() -> database.findUser(g.id()));
                case DbOp.GetPosts g -> IO.delay(() -> database.findPosts(g.userId()));
                case DbOp.GetNotifications g ->
                    IO.delay(() -> database.findNotifications(g.userId()));
            };
            return (Kind<IOKind.Witness, A>) IO_OP.widen(io);
        }
    };

// Interpret the program
FreeAp<DbOpKind.Witness, Dashboard> program = dashboardProgram(1);
Kind<IOKind.Witness, Dashboard> result = program.foldMap(interpreter, ioApplicative);
```

## Parallel Execution

The power of Free Applicative emerges when the target `Applicative` supports parallelism. Consider using `CompletableFuture`:

<!-- verify -->
```java
// Interpreter to CompletableFuture (can run in parallel)
Natural<DbOpKind.Witness, CompletableFutureKind.Witness> parallelInterpreter =
    new Natural<>() {
        @Override
        @SuppressWarnings("unchecked") // the operation fixes A; the switch cannot say so
        public <A> Kind<CompletableFutureKind.Witness, A> apply(Kind<DbOpKind.Witness, A> fa) {
            DbOp<A> op = DB_OP.narrow(fa);
            CompletableFuture<Object> future = switch (op) {
                case DbOp.GetUser g ->
                    CompletableFuture.supplyAsync(() -> database.findUser(g.id()));
                case DbOp.GetPosts g ->
                    CompletableFuture.supplyAsync(() -> database.findPosts(g.userId()));
                case DbOp.GetNotifications g ->
                    CompletableFuture.supplyAsync(() -> database.findNotifications(g.userId()));
            };
            return (Kind<CompletableFutureKind.Witness, A>) FUTURE.widen(future);
        }
    };

// When interpreted, GetUser and GetPosts can run in parallel!
FreeAp<DbOpKind.Witness, UserProfile> program = userFetch.map2(postsFetch, UserProfile::new);
Kind<CompletableFutureKind.Witness, UserProfile> future =
    program.foldMap(parallelInterpreter, cfApplicative);
```

Because the Free Applicative structure makes independence explicit, the `CompletableFuture` applicative can start both operations immediately rather than waiting for one to complete.

## Static Analysis

Unlike Free Monad, Free Applicative programs can be analysed before execution. The `analyse` method (an alias for `foldMap`) emphasises this capability:

<!-- verify -->
```java
// Count operations before executing
Natural<DbOpKind.Witness, ConstKind.Witness<Integer>> counter =
    new Natural<>() {
        @Override
        public <A> Kind<ConstKind.Witness<Integer>, A> apply(Kind<DbOpKind.Witness, A> fa) {
            return CONST.widen(new Const<>(1));  // Each operation counts as 1
        }
    };

FreeAp<DbOpKind.Witness, Dashboard> program = dashboardProgram(1);
Kind<ConstKind.Witness<Integer>, Dashboard> analysis =
    program.analyse(counter, constApplicative);

int operationCount = CONST.narrow(analysis).value();
System.out.println("Program will execute " + operationCount + " operations");
```

This is useful for:
- **Query optimisation**: Batch similar database operations
- **Cost estimation**: Calculate resource usage before execution
- **Validation**: Check program structure meets constraints
- **Logging**: Record what operations will be performed

## Validation with Error Accumulation

Free Applicative is excellent for validation that should report **all** errors, not just the first:

<!-- verify -->
```java
// Define validation operations
sealed interface ValidationOp<A> {
    record ValidateEmail(String email) implements ValidationOp<String> {}
    record ValidateAge(int age) implements ValidationOp<Integer> {}
    record ValidateName(String name) implements ValidationOp<String> {}
}

record Pair<A, B>(A first, B second) {}

record ValidatedUser(String name, String email, int age) {}
```

Each operation is lifted independently, so nothing short-circuits:

<!-- verify -->
```java
// Build validation program
FreeAp<ValidationOpKind.Witness, ValidatedUser> validateUser(
        String name, String email, int age) {
    return FreeAp.lift(VALIDATION_OP.widen(new ValidationOp.ValidateName(name))).map2(
        FreeAp.lift(VALIDATION_OP.widen(new ValidationOp.ValidateEmail(email))).map2(
            FreeAp.lift(VALIDATION_OP.widen(new ValidationOp.ValidateAge(age))),
            (e, a) -> new Pair<>(e, a)
        ),
        (n, pair) -> new ValidatedUser(n, pair.first(), pair.second())
    );
}
```

The interpreter turns each operation into a `Validated`, and the `Validated` applicative
accumulates every failure it meets:

<!-- verify -->
```java
// Interpreter to Validated (accumulates all errors)
Natural<ValidationOpKind.Witness, ValidatedKind.Witness<List<String>>> interpreter =
    new Natural<>() {
        @Override
        @SuppressWarnings("unchecked") // the operation fixes A; the switch cannot say so
        public <A> Kind<ValidatedKind.Witness<List<String>>, A> apply(
                Kind<ValidationOpKind.Witness, A> fa) {
            ValidationOp<A> op = VALIDATION_OP.narrow(fa);
            Validated<List<String>, Object> validated = switch (op) {
                case ValidationOp.ValidateName v ->
                    v.name().length() >= 2
                        ? Validated.valid(v.name())
                        : Validated.invalid(List.of("Name must be at least 2 characters"));
                case ValidationOp.ValidateEmail v ->
                    v.email().contains("@")
                        ? Validated.valid(v.email())
                        : Validated.invalid(List.of("Invalid email format"));
                case ValidationOp.ValidateAge v ->
                    v.age() >= 0 && v.age() <= 150
                        ? Validated.valid(v.age())
                        : Validated.invalid(List.of("Age must be between 0 and 150"));
            };
            return (Kind<ValidatedKind.Witness<List<String>>, A>) VALIDATED.widen(validated);
        }
    };

// Execute validation
FreeAp<ValidationOpKind.Witness, ValidatedUser> program = validateUser("X", "invalid", -5);
Kind<ValidatedKind.Witness<List<String>>, ValidatedUser> result =
    program.foldMap(interpreter, validatedApplicative);

// Result: Invalid(["Name must be at least 2 characters", "Invalid email format",
//                  "Age must be between 0 and 150"]) - all three errors are reported.
```

## Retracting to the Original Applicative

If your instruction type `F` is already an `Applicative`, you can "retract" back to it:

<!-- verify -->
```java
FreeAp<IOKind.Witness, String> program = FreeAp.lift(IO_OP.widen(IO.delay(() -> "done")));

// Retract: FreeAp[IO, A] -> IO[A]
Kind<IOKind.Witness, String> io = program.retract(ioApplicative);

// Equivalent to:
Kind<IOKind.Witness, String> sameThing = program.foldMap(Natural.identity(), ioApplicative);
```

## Applicative Laws

`FreeAp` satisfies the applicative laws by construction:

1. **Identity**: `pure(id).ap(fa) == fa`
2. **Homomorphism**: `pure(f).ap(pure(x)) == pure(f(x))`
3. **Interchange**: `ff.ap(pure(x)) == pure(f -> f(x)).ap(ff)`
4. **Composition**: `pure(.).ap(ff).ap(fg).ap(fa) == ff.ap(fg.ap(fa))`

These laws ensure that combining computations behaves predictably.

## When to Use Free Applicative

### Good Use Cases

1. **Parallel data fetching**: Multiple independent API calls or database queries
2. **Validation**: Accumulate all validation errors rather than failing on first
3. **Batching**: Combine similar operations into bulk requests
4. **Static analysis**: Inspect program structure before execution
5. **Cost estimation**: Calculate resource requirements upfront

### When Free Monad is Better

1. **Conditional logic**: "If user is admin, do X, otherwise do Y"
2. **Sequential dependencies**: "Use the user ID from step 1 in step 2"
3. **Early termination**: "Stop processing if validation fails"

### Combining Both

In practice, you might use both. Free Applicative for independent parts, Free Monad for the sequential orchestration:

<!-- verify -->
```java
// Free Applicative: independent fetches
FreeAp<DbOpKind.Witness, UserProfile> fetchProfile =
    userFetch.map2(postsFetch, UserProfile::new);

// Free Monad: `Free.Ap` holds a FreeAp as one step of a sequential workflow
Free<DbOpKind.Witness, UserProfile> workflow =
    new Free.Ap<>(fetchProfile)                 // Run the independent fetches
        .flatMap(profile ->                     // Then use the result
            getPosts(profile.user().id())       // This depends on fetchProfile
                .map(posts -> new UserProfile(profile.user(), posts)));
```

## Summary

Free Applicative provides:

- **Independence**: Computations don't depend on each other's results
- **Parallelism**: Smart interpreters can execute operations concurrently
- **Static analysis**: Full program structure visible before execution
- **Error accumulation**: Collect all errors rather than failing fast
- **Batching potential**: Similar operations can be combined

Use Free Applicative when your computations are independent; use Free Monad when they have sequential dependencies. Often the best solution combines both.

---

~~~admonish tip title="See Also"
- [Choosing Abstraction Levels](../functional/abstraction_levels.md) - When to use Applicative vs Selective vs Monad
- [Selective](../functional/selective.md) - Middle ground between Applicative and Monad
- [Applicative](../functional/applicative.md) - The type class Free Applicative is based on
~~~

~~~admonish tip title="Further Reading"
- **Functional Programming in Scala** (Red Book): Chapter 12 covers applicative functors and their relationship to monads
- **Cats Documentation**: [Free Applicative](https://typelevel.org/cats/datatypes/freeapplicative.html) - Scala implementation with examples
~~~

~~~admonish info title="Hands-On Learning"
- [Tutorial 10: Free Applicative](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial10_FreeApplicative.java) (6 exercises, ~10 minutes) - Building Free Applicative programs
- [Tutorial 11: Static Analysis](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial11_StaticAnalysis.java) (10 exercises, ~12 minutes) - Analysing programs before execution
~~~

---

**Previous:** [Free](free_monad.md)
**Next:** [EitherF](eitherf.md)
