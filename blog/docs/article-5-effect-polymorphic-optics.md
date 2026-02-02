# The Effect Path API: Railway-Style Error Handling

*Part 5 of the Functional Optics for Modern Java series*

In Article 4, we built traversals that visit every node in our expression tree. We implemented constant folding, identity simplification, and dead branch elimination. But all our transformations were pure: they took an expression and returned a new expression, with no side effects.

Real compilers and interpreters need more. Type checking should report *all* errors, not just the first one. Interpretation must track variable bindings as it descends through the tree. These are *effects*, and they change everything about how we structure our code.

Higher-Kinded-J provides the **[Effect Path API](https://higher-kinded-j.github.io/latest/effect/ch_intro.html)**: a fluent interface for computations that might fail, accumulate errors, or require deferred execution. This is the practical face of effect polymorphism, making powerful abstractions accessible through an ergonomic API.

---

## Effects as Assembly Line Quality Control

Before diving into code, consider an analogy. Imagine you're running an assembly line in a factory. Each station performs an operation on a product, but things can go wrong:

- **MaybePath**: A station that might produce nothing (parts ran out). The line continues, but there's no product to pass on.

- **EitherPath**: A station with an inspector. If the product fails inspection, it's immediately diverted to the rejection bin with a tag explaining why. The line stops for that product.

- **ValidationPath**: A multi-point inspection station. Every defect is recorded on a checklist, even if there are multiple problems. The product is only rejected after *all* checks are complete, and the checklist shows *everything* that needs fixing.

- **TryPath**: A station that might malfunction. If it throws a wrench (literally), we catch the exception and treat it as data rather than letting it crash the whole factory.

- **IOPath**: A station that doesn't run until you press the "GO" button. The work is planned and sequenced, but nothing actually happens until you explicitly start it.

- **VTaskPath**: A station that spawns lightweight workers for each task, coordinating them efficiently. Work happens concurrently on virtual threads, and the station can wait for all workers, race them, or collect their results.

The Effect Path API gives you these different assembly line configurations, letting you choose the right error handling strategy for each situation.

---

## The Railway Model

Effect Paths follow the "railway" metaphor popularised by Scott Wlaschin. Values travel along tracks, and computations can switch between success and failure:

```
                    Success Track
Input ---> [validate] ---> [transform] ---> [combine] ---> Output
               |               |               |
               v               v               v
            [error]         [error]         [error]
               |               |               |
               +--------> Failure Track ------+---> Accumulated Errors
```

The key insight: instead of throwing exceptions or returning null, Effect Paths make failure explicit in the type system. A `MaybePath<String>` might contain a string or might be empty. An `EitherPath<Error, User>` contains either an error or a user. A `ValidationPath<List<Error>, Form>` contains either accumulated errors or a valid form.

This explicitness has practical benefits:

1. **Compiler enforcement**: You cannot ignore a potential failure; the types require handling it
2. **Composition**: Effect Paths chain naturally with `map`, `via`, and `zipWith`
3. **Flexibility**: Choose fail-fast (`EitherPath`) or accumulating (`ValidationPath`) behaviour

---

## The Effect Path Types

Higher-Kinded-J provides six core Effect Path types, each suited to different use cases:

| Effect Path | Contains | Use Case |
|-------------|----------|----------|
| `MaybePath<A>` | Value or nothing | Optional data, silent failures |
| `EitherPath<E, A>` | Error or value | Fail-fast error handling |
| `TryPath<A>` | Exception or value | Wrapping throwing code |
| `ValidationPath<E, A>` | Errors or value | Accumulating all problems |
| `IOPath<A>` | Deferred computation | Side effects, resource management |
| `VTaskPath<A>` | Virtual thread computation | Concurrent operations, parallelism |

### Creating Effect Paths

The `Path` factory class provides convenient constructors:

```java
import org.higherkindedj.hkt.effect.Path;

// MaybePath
MaybePath<String> present = Path.just("hello");
MaybePath<String> absent = Path.nothing();
MaybePath<String> nullable = Path.maybe(possiblyNullValue);

// EitherPath
EitherPath<String, Integer> success = Path.right(42);
EitherPath<String, Integer> failure = Path.left("Something went wrong");

// TryPath
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));
TryPath<Integer> safe = Path.success(42);
TryPath<Integer> failed = Path.failure(new IllegalArgumentException("bad input"));

// ValidationPath (requires a Semigroup for error accumulation)
ValidationPath<List<Error>, User> valid = Path.valid(user, Semigroups.list());
ValidationPath<List<Error>, User> invalid = Path.invalid(errors, Semigroups.list());

// IOPath (deferred execution)
IOPath<String> readFile = Path.io(() -> Files.readString(path));
IOPath<Unit> sideEffect = Path.ioRunnable(() -> System.out.println("Hello"));
```

---

## MaybePath: Optional Values

`MaybePath<A>` represents a computation that might not produce a value. It wraps Higher-Kinded-J's `Maybe` type.

```java
MaybePath<String> greeting = Path.just("Hello")
    .map(String::toUpperCase)
    .filter(s -> s.length() > 3)
    .map(s -> s + "!");

// Extract the result
String result = greeting.getOrElse("default");  // "HELLO!"

// Or pattern match
greeting.run().fold(
    () -> System.out.println("No value"),
    value -> System.out.println("Got: " + value)
);
```

### Chaining with via

The `via` method chains dependent computations:

```java
MaybePath<User> userPath = Path.just(userId)
    .via(id -> lookupUser(id))        // Returns MaybePath<User>
    .via(user -> validateUser(user)); // Returns MaybePath<User>

// If any step returns nothing, the chain short-circuits
```

### Converting to Other Effect Paths

```java
MaybePath<String> maybe = Path.just("hello");

// To EitherPath (provide error for empty case)
EitherPath<String, String> either = maybe.toEitherPath("Value was missing");

// To TryPath (provide exception for empty case)
TryPath<String> tryPath = maybe.toTryPath(() -> new NoSuchElementException());

// To ValidationPath (provide error and semigroup)
ValidationPath<List<Error>, String> validated =
    maybe.toValidationPath(List.of(new Error("missing")), Semigroups.list());
```

---

## EitherPath: Fail-Fast Error Handling

`EitherPath<E, A>` represents a computation that either succeeds with a value or fails with a typed error. Unlike exceptions, the error type is explicit in the signature.

```java
EitherPath<String, Integer> divide(int a, int b) {
    if (b == 0) {
        return Path.left("Division by zero");
    }
    return Path.right(a / b);
}

EitherPath<String, Integer> result = divide(10, 2)
    .map(n -> n * 2)
    .via(n -> divide(n, 3));

// Pattern match on the result
result.run().fold(
    error -> System.out.println("Error: " + error),
    value -> System.out.println("Result: " + value)
);
```

### Error Transformation

```java
// Map over the error type
EitherPath<Integer, String> withErrorCode =
    Path.<String, String>left("Not found")
        .mapError(msg -> 404);

// Recover from errors
EitherPath<String, Integer> recovered =
    Path.<String, Integer>left("Error")
        .recover(error -> -1);  // Replace error with default value

// Recover with another EitherPath
EitherPath<String, Integer> fallback =
    Path.<String, Integer>left("Primary failed")
        .recoverWith(error -> fetchFromBackup());
```

---

## ValidationPath: Error Accumulation

`ValidationPath<E, A>` is the key type for comprehensive error reporting. Unlike `EitherPath`, which stops at the first error, `ValidationPath` collects all errors.

```
┌──────────────────────────────────────────────────────────────────────┐
│              EitherPath vs ValidationPath                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  EitherPath (fail-fast):                                             │
│                                                                      │
│    [validate name] ──✗──▶ STOP (only see "Name is required")        │
│           │                                                          │
│           ✓                                                          │
│           ▼                                                          │
│    [validate age] ──✗──▶ ...never reached...                        │
│           │                                                          │
│           ✓                                                          │
│           ▼                                                          │
│    [validate email] ──✗──▶ ...never reached...                      │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ValidationPath (accumulating):                                      │
│                                                                      │
│    [validate name] ──✗──┐                                           │
│                         │                                            │
│    [validate age]  ──✗──┼──▶ COLLECT ALL: ["Name is required",      │
│                         │                   "Age must be positive",  │
│    [validate email]──✗──┘                   "Invalid email format"] │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

```java
// Create validators that return ValidationPath
ValidationPath<List<String>, String> validateName(String name) {
    if (name == null || name.isBlank()) {
        return Path.invalid(List.of("Name is required"), Semigroups.list());
    }
    return Path.valid(name.trim(), Semigroups.list());
}

ValidationPath<List<String>, Integer> validateAge(int age) {
    if (age < 0) {
        return Path.invalid(List.of("Age cannot be negative"), Semigroups.list());
    }
    if (age > 150) {
        return Path.invalid(List.of("Age seems unrealistic"), Semigroups.list());
    }
    return Path.valid(age, Semigroups.list());
}

ValidationPath<List<String>, String> validateEmail(String email) {
    if (!email.contains("@")) {
        return Path.invalid(List.of("Invalid email format"), Semigroups.list());
    }
    return Path.valid(email, Semigroups.list());
}
```

### Combining Validations: Short-Circuit vs Accumulating

ValidationPath offers two composition modes:

**Short-circuit** (via `via`): Stops at first error, like EitherPath

```java
// Sequential: second validation only runs if first succeeds
ValidationPath<List<String>, User> sequential = validateName(name)
    .via(n -> validateAge(age).map(a -> new User(n, a, null)));
```

**Accumulating** (via `zipWithAccum`): Collects all errors

```java
// Parallel: all validations run, errors accumulate
ValidationPath<List<String>, User> accumulated = validateName(name)
    .zipWithAccum(validateAge(age), (n, a) -> new Pair<>(n, a))
    .zipWithAccum(validateEmail(email), (pair, e) -> new User(pair.first(), pair.second(), e));
```

For multiple validations, use `zipWith3Accum`:

```java
ValidationPath<List<String>, User> user = validateName(name)
    .zipWith3Accum(
        validateAge(age),
        validateEmail(email),
        (n, a, e) -> new User(n, a, e)
    );
```

### The Semigroup Requirement

ValidationPath requires a `Semigroup<E>` to combine errors. Higher-Kinded-J provides common semigroups:

```java
import org.higherkindedj.hkt.Semigroups;

// List semigroup: concatenates lists
Semigroup<List<String>> listSemigroup = Semigroups.list();

// String semigroup: concatenates strings
Semigroup<String> stringSemigroup = Semigroups.string();

// Custom semigroup
Semigroup<ErrorReport> reportSemigroup = (a, b) -> a.merge(b);
```

---

## TryPath: Exception Handling

`TryPath<A>` wraps computations that might throw exceptions, converting them to values:

```java
// Wrap throwing code
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(userInput));

// Chain operations safely
TryPath<Double> calculation = Path.tryOf(() -> Integer.parseInt(a))
    .map(x -> x * 2.0)
    .via(x -> Path.tryOf(() -> x / Double.parseDouble(b)));

// Handle the result
calculation.run().fold(
    value -> System.out.println("Result: " + value),
    ex -> System.out.println("Error: " + ex.getMessage())
);
```

### Recovery from Exceptions

```java
TryPath<Integer> withDefault = Path.tryOf(() -> Integer.parseInt(input))
    .recover(ex -> -1);  // Use -1 on parse failure

TryPath<Integer> withFallback = Path.tryOf(() -> fetchFromPrimary())
    .recoverWith(ex -> Path.tryOf(() -> fetchFromBackup()));
```

---

## IOPath: Deferred Side Effects

`IOPath<A>` represents a computation that will be executed later. Nothing happens until you call `unsafeRun()` or `runSafe()`.

```java
// Define computations without executing them
IOPath<String> readConfig = Path.io(() -> Files.readString(Path.of("config.json")));
IOPath<Unit> writeLog = Path.ioRunnable(() -> logger.info("Operation complete"));

// Compose deferred computations
IOPath<Config> loadConfig = readConfig
    .map(json -> parseJson(json))
    .via(parsed -> validateConfig(parsed));

// Execute when ready
Config config = loadConfig.unsafeRun();  // Throws on error
Try<Config> safe = loadConfig.runSafe(); // Captures exceptions
```

### Resource Management

IOPath provides safe resource handling with `bracket` and `withResource`:

```java
// Bracket pattern: acquire, use, release
IOPath<String> content = IOPath.bracket(
    () -> Files.newBufferedReader(path),  // Acquire
    reader -> reader.lines().collect(Collectors.joining("\n")),  // Use
    reader -> reader.close()  // Release (always runs)
);

// For AutoCloseable resources
IOPath<String> simpler = IOPath.withResource(
    () -> Files.newBufferedReader(path),
    reader -> reader.lines().collect(Collectors.joining("\n"))
);
```

### Parallel Execution and Retry

```java
// Run two computations in parallel
IOPath<UserProfile> profile = fetchUser.parZipWith(
    fetchOrders,
    (user, orders) -> new UserProfile(user, orders)
);

// Retry with exponential backoff
IOPath<String> resilient = Path.io(() -> httpClient.get(url))
    .retry(3, Duration.ofMillis(100));  // 3 attempts, 100ms initial delay
```

---

## VTaskPath: Virtual Thread Concurrency

`VTaskPath<A>` represents a computation that runs on Java's virtual threads. It brings the lightweight concurrency of Project Loom to the Effect Path API, letting you write simple blocking code that scales to millions of concurrent operations.

```java
// Create VTaskPaths
VTaskPath<String> fetchUser = Path.vtask(() -> userService.get(userId));
VTaskPath<String> fetchOrders = Path.vtask(() -> orderService.list(userId));

// Pure value (no computation)
VTaskPath<Integer> pure = Path.vtaskPure(42);

// Immediate failure
VTaskPath<String> failed = Path.vtaskFail(new IOException("Network error"));

// From a Runnable
VTaskPath<Unit> logAction = Path.vtaskExec(() -> logger.info("Starting..."));
```

### Execution Model

Unlike `IOPath`, which runs on the caller's thread, `VTaskPath` executes on virtual threads managed by the JVM. Virtual threads consume mere kilobytes of memory (versus megabytes for platform threads), enabling millions of concurrent tasks.

```java
VTaskPath<Integer> task = Path.vtask(() -> expensiveComputation());

// Three ways to execute
Integer result = task.run();           // Blocks, may throw
Try<Integer> safe = task.runSafe();    // Captures exceptions in Try
CompletableFuture<Integer> future = task.runAsync();  // Non-blocking
```

### Composition

VTaskPath chains with the same `map` and `via` patterns as other Effect Paths:

```java
VTaskPath<Dashboard> dashboard = Path.vtask(() -> fetchUser(id))
    .map(user -> user.preferences())
    .via(prefs -> Path.vtask(() -> buildDashboard(prefs)));
```

### Parallel Execution with Par

The `Par` utility provides combinators for running VTasks concurrently:

```java
import org.higherkindedj.hkt.vtask.Par;

// Combine two tasks in parallel
VTask<UserProfile> profile = Par.map2(
    VTask.of(() -> fetchUser(id)),
    VTask.of(() -> fetchOrders(id)),
    (user, orders) -> new UserProfile(user, orders)
);

// Execute a list of tasks in parallel
List<VTask<Integer>> tasks = ids.stream()
    .map(id -> VTask.of(() -> process(id)))
    .toList();
VTask<List<Integer>> allResults = Par.all(tasks);

// Race: first successful result wins
VTask<String> fastest = Par.race(List.of(
    VTask.of(() -> fetchFromMirror1()),
    VTask.of(() -> fetchFromMirror2())
));
```

### Structured Concurrency with Scope

For more control over concurrent operations, use `Scope`:

```java
import org.higherkindedj.hkt.vtask.Scope;

// Wait for all tasks to succeed
VTask<List<String>> all = Scope.<String>allSucceed()
    .fork(VTask.of(() -> fetchA()))
    .fork(VTask.of(() -> fetchB()))
    .fork(VTask.of(() -> fetchC()))
    .timeout(Duration.ofSeconds(5))
    .join();

// First success wins, cancel others
VTask<String> any = Scope.<String>anySucceed()
    .fork(VTask.of(() -> fetchFromPrimary()))
    .fork(VTask.of(() -> fetchFromBackup()))
    .join();

// Accumulate errors (like ValidationPath, but concurrent)
VTask<Validated<List<Error>, List<String>>> validated =
    Scope.<String>accumulating(Error::from)
        .fork(validateField1())
        .fork(validateField2())
        .fork(validateField3())
        .join();
```

### Error Handling

```java
VTaskPath<Config> config = Path.vtask(() -> loadConfig())
    .handleError(ex -> Config.defaults())           // Replace error with value
    .handleErrorWith(ex -> Path.vtask(() -> loadFallback()));  // Try another task
```

### Timeouts

```java
VTaskPath<Data> withTimeout = Path.vtask(() -> slowOperation())
    .timeout(Duration.ofSeconds(5));
```

### When to Use VTaskPath vs IOPath

| Aspect | VTaskPath | IOPath |
|--------|-----------|--------|
| **Thread model** | Virtual threads | Caller's thread |
| **Parallelism** | Built-in via `Par`, `Scope` | Manual composition |
| **Structured concurrency** | Yes, with `Scope` | No |
| **Best for** | I/O-bound concurrent work | Single-threaded effects |

Choose `VTaskPath` when you need lightweight concurrency at scale. Choose `IOPath` when single-threaded execution is sufficient or when you need explicit control over which thread runs the computation.

---

## Bridging Focus Paths and Effect Paths

The [Focus DSL](https://higher-kinded-j.github.io/latest/optics/ch4_intro.html) (FocusPath, AffinePath, TraversalPath) integrates seamlessly with Effect Paths. This bridge is where navigation meets computation. For a complete reference of all bridge methods, see the [Focus-Effect Integration Guide](https://higher-kinded-j.github.io/latest/effect/focus_integration.html).

### From Focus Paths to Effect Paths

```java
// FocusPath to MaybePath (always succeeds since FocusPath has exactly one focus)
FocusPath<User, String> namePath = UserFocus.name();
MaybePath<String> name = namePath.toMaybePath(user);  // Always Just(value)

// AffinePath to MaybePath (may be empty)
AffinePath<User, String> nicknamePath = UserFocus.nickname();
MaybePath<String> nickname = nicknamePath.toMaybePath(user);  // Just or Nothing

// AffinePath to EitherPath (provide error for missing case)
EitherPath<String, String> nicknameOrError =
    nicknamePath.toEitherPath(user, "No nickname set");
```

### Applying Focus Paths Within Effect Contexts

Effect Paths have a `focus` method that applies a FocusPath:

```java
// Start with an Effect Path containing a User
EitherPath<Error, User> userPath = fetchUser(userId);

// Focus on a field within the effect context
EitherPath<Error, String> emailPath = userPath.focus(UserFocus.email());
// Equivalent to: userPath.map(user -> UserFocus.email().get(user))

// Chain multiple focuses
EitherPath<Error, String> city = userPath
    .focus(UserFocus.address())
    .focus(AddressFocus.city());
```

For AffinePath (which might not find a value), provide an error:

```java
MaybePath<User> maybeUser = Path.just(user);
MaybePath<String> nickname = maybeUser.focus(
    UserFocus.nickname()  // AffinePath for optional field
);
// Returns Nothing if nickname is absent

EitherPath<String, User> eitherUser = Path.right(user);
EitherPath<String, String> nickname = eitherUser.focus(
    UserFocus.nickname(),
    "User has no nickname"  // Error if absent
);
```

---

## Type Checking with ValidationPath

Let's apply the Effect Path API to our expression language. Type checking is a perfect use case for `ValidationPath`: we want to report all type errors, not just the first one.

### Defining Types and Errors

```java
public enum Type { INT, BOOL, STRING }

public record TypeError(String message) {}
```

### The Type Checker

```java
public final class ExprTypeChecker {

    private static final Semigroup<List<TypeError>> ERRORS = Semigroups.list();

    public static ValidationPath<List<TypeError>, Type> typeCheck(Expr expr, TypeEnv env) {
        return switch (expr) {
            case Literal(var value) -> typeCheckLiteral(value);
            case Variable(var name) -> typeCheckVariable(name, env);
            case Binary(var left, var op, var right) -> typeCheckBinary(left, op, right, env);
            case Conditional(var cond, var then_, var else_) ->
                typeCheckConditional(cond, then_, else_, env);
        };
    }

    private static ValidationPath<List<TypeError>, Type> typeCheckLiteral(Object value) {
        return switch (value) {
            case Integer _ -> Path.valid(Type.INT, ERRORS);
            case Boolean _ -> Path.valid(Type.BOOL, ERRORS);
            case String _ -> Path.valid(Type.STRING, ERRORS);
            default -> Path.invalid(
                List.of(new TypeError("Unknown literal type: " + value.getClass().getSimpleName())),
                ERRORS
            );
        };
    }

    private static ValidationPath<List<TypeError>, Type> typeCheckVariable(String name, TypeEnv env) {
        return env.lookup(name)
            .map(type -> Path.valid(type, ERRORS))
            .orElseGet(() -> Path.invalid(
                List.of(new TypeError("Undefined variable: " + name)),
                ERRORS
            ));
    }

    private static ValidationPath<List<TypeError>, Type> typeCheckBinary(
            Expr left, BinaryOp op, Expr right, TypeEnv env) {
        // Use zipWithAccum to accumulate errors from both operands
        return typeCheck(left, env)
            .zipWithAccum(typeCheck(right, env), (lt, rt) -> checkBinaryTypes(op, lt, rt))
            .via(result -> result);  // Flatten nested validation
    }

    private static ValidationPath<List<TypeError>, Type> typeCheckConditional(
            Expr cond, Expr then_, Expr else_, TypeEnv env) {
        // Accumulate errors from all three sub-expressions
        return typeCheck(cond, env)
            .zipWith3Accum(
                typeCheck(then_, env),
                typeCheck(else_, env),
                ExprTypeChecker::checkConditionalTypes
            )
            .via(result -> result);
    }

    private static ValidationPath<List<TypeError>, Type> checkBinaryTypes(
            BinaryOp op, Type left, Type right) {
        return switch (op) {
            case ADD, SUB, MUL, DIV -> {
                if (left == Type.INT && right == Type.INT) {
                    yield Path.valid(Type.INT, ERRORS);
                }
                yield Path.invalid(List.of(new TypeError(
                    "Arithmetic operator '%s' requires INT operands, got %s and %s"
                        .formatted(op, left, right)
                )), ERRORS);
            }
            case AND, OR -> {
                if (left == Type.BOOL && right == Type.BOOL) {
                    yield Path.valid(Type.BOOL, ERRORS);
                }
                yield Path.invalid(List.of(new TypeError(
                    "Logical operator '%s' requires BOOL operands, got %s and %s"
                        .formatted(op, left, right)
                )), ERRORS);
            }
            case EQ, NE -> Path.valid(Type.BOOL, ERRORS);
            case LT, LE, GT, GE -> {
                if (left == Type.INT && right == Type.INT) {
                    yield Path.valid(Type.BOOL, ERRORS);
                }
                yield Path.invalid(List.of(new TypeError(
                    "Comparison operator '%s' requires INT operands, got %s and %s"
                        .formatted(op, left, right)
                )), ERRORS);
            }
        };
    }

    private static ValidationPath<List<TypeError>, Type> checkConditionalTypes(
            Type cond, Type then_, Type else_) {
        List<TypeError> errors = new ArrayList<>();

        if (cond != Type.BOOL) {
            errors.add(new TypeError("Condition must be BOOL, got " + cond));
        }
        if (then_ != else_) {
            errors.add(new TypeError(
                "Branches must have same type, got %s and %s".formatted(then_, else_)
            ));
        }

        if (errors.isEmpty()) {
            return Path.valid(then_, ERRORS);
        }
        return Path.invalid(errors, ERRORS);
    }
}
```

### Running the Type Checker

```java
// Expression with multiple errors: (1 + true) * (false && 42)
Expr expr = new Binary(
    new Binary(new Literal(1), BinaryOp.ADD, new Literal(true)),
    BinaryOp.MUL,
    new Binary(new Literal(false), BinaryOp.AND, new Literal(42))
);

ValidationPath<List<TypeError>, Type> result = ExprTypeChecker.typeCheck(expr, TypeEnv.empty());

result.run().fold(
    errors -> {
        System.out.println("Type errors:");
        for (TypeError error : errors) {
            System.out.println("  - " + error.message());
        }
    },
    type -> System.out.println("Type: " + type)
);
```

Output:
```
Type errors:
  - Arithmetic operator 'ADD' requires INT operands, got INT and BOOL
  - Logical operator 'AND' requires BOOL operands, got BOOL and INT
```

Both errors are reported in a single pass. The user can fix them both at once.

---

## Understanding the Underlying Abstractions

The Effect Path API is built on Higher-Kinded-J's type class hierarchy. Understanding these abstractions helps when you need maximum flexibility.

### The modifyF Operation

Every optic supports `modifyF`, which generalises modification to work with any effect:

```java
public interface Traversal<S, A> {
    <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
        Function<A, Kind<F, A>> f,
        S source,
        Applicative<F> applicative
    );
}
```

The `Applicative<F>` parameter provides:

1. **`of(a)`**: Wrap a pure value in the effect
2. **`map2(fa, fb, combine)`**: Combine two effectful values

With just these operations, we can sequence independent computations while accumulating their effects.

### Effect Path Types as Kind Wrappers

Each Effect Path type wraps a corresponding `Kind<F, A>`:

```java
// MaybePath wraps Kind<Maybe.Witness, A>
MaybePath<String> maybePath = Path.just("hello");
Maybe<String> underlying = maybePath.run();

// EitherPath wraps Kind<Either.Witness<E, ?>, A>
EitherPath<String, Integer> eitherPath = Path.right(42);
Either<String, Integer> underlying = eitherPath.run();
```

The Effect Path API provides ergonomic methods that delegate to these underlying types.

### When to Use modifyF Directly

For most use cases, the Effect Path API suffices. Use `modifyF` directly when:

- You're building reusable library code
- You need to work with custom effect types
- You want maximum composability with optics

```java
// Using modifyF directly with a traversal
TraversalPath<Company, Employee> allEmployees = CompanyFocus
    .departments().each()
    .employees().each();

Kind<ValidatedKind.Witness<List<Error>>, Company> result = allEmployees.modifyF(
    emp -> validateEmployee(emp),
    company,
    ValidatedApplicative.instance(Semigroups.list())
);
```

---

## Effect Path API vs modifyF: Choosing Your Level

Higher-Kinded-J gives you two levels of abstraction:

| Level | API | Best For |
|-------|-----|----------|
| **High** | Effect Path API | Most application code, clear intent |
| **Low** | `modifyF` with `Kind<F, A>` | Libraries, custom effects, maximum flexibility |

### High-Level: Effect Path API

```java
// Clear, fluent, discoverable
ValidationPath<List<Error>, User> validated = Path.valid(user, Semigroups.list())
    .via(u -> validateName(u.name()))
    .zipWithAccum(validateAge(user.age()), (u, age) -> u);
```

### Low-Level: modifyF with Applicative

```java
// Maximum control, composable with any optic
Traversal<User, String> nameLens = UserLenses.name().asTraversal();
Kind<ValidatedKind.Witness<List<Error>>, User> result = nameLens.modifyF(
    name -> validateName(name),
    user,
    ValidatedApplicative.instance(Semigroups.list())
);
```

Start with the Effect Path API. Drop to `modifyF` when you need its power.

---

## Summary

This article introduced the Effect Path API for effectful programming:

1. **Effect Path types**: `MaybePath`, `EitherPath`, `TryPath`, `ValidationPath`, `IOPath`, `VTaskPath`
2. **Railway model**: Values travel success/failure tracks with explicit error handling
3. **ValidationPath**: Accumulate all errors with `zipWithAccum` and `zipWith3Accum`
4. **VTaskPath**: Virtual thread concurrency with `Par` and `Scope` for parallel operations
5. **Bridge methods**: Connect Focus paths to Effect paths via `toMaybePath`, `toEitherPath`
6. **Type checking example**: Comprehensive error reporting with ValidationPath

The Effect Path API makes effect polymorphism practical. The same patterns that work for optional values work for error handling, validation, and deferred execution. Choose the right Effect Path type for your use case, and let composition do the rest.

---

## What's Next

We've built a substantial expression language: AST definition, optics generation, tree traversals, optimisation passes, type checking, and the Effect Path API for error accumulation.

In Article 6, we'll step back and reflect on what we've built:

- **The complete pipeline**: From source text through parsing, type checking, optimisation, and evaluation
- **Design patterns**: Common patterns for effect-polymorphic code
- **Performance considerations**: When to use optics and when simpler approaches suffice
- **Real-world applications**: Applying these techniques beyond expression languages

---

## Further Reading

### Effect Systems and Functional Programming

- **Scott Wlaschin, ["Railway Oriented Programming"](https://fsharpforfunandprofit.com/rop/)**: The visual explanation of error handling that inspired the railway metaphor.

- **Philip Wadler, ["Monads for functional programming"](https://homepages.inf.ed.ac.uk/wadler/papers/marktoberdorf/baastad.pdf)** (1992): The foundational paper explaining how monads structure effectful computation.

- **Conor McBride & Ross Paterson, ["Applicative programming with effects"](https://www.staff.city.ac.uk/~ross/papers/Applicative.html)** (JFP, 2008): The paper that introduced `Applicative` as distinct from `Monad`, directly relevant to understanding why `Validated` accumulates errors.

### Error Handling Patterns

- **Cats Documentation, [Validated](https://typelevel.org/cats/datatypes/validated.html)**: The Scala Cats library's `Validated` type, which inspired Higher-Kinded-J's implementation.

### Higher-Kinded Types and Functional Abstractions

- **["Higher-Kinded Types with Java and Scala"](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html)** (Scott Logic, 2025): Explores how higher-kinded types work and how Java can simulate them, providing context for understanding the `Kind<F, A>` pattern used throughout Higher-Kinded-J.

- **["Functors and Monads with Java and Scala"](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html)** (Scott Logic, 2025): A practical comparison of how Functor and Monad abstractions are implemented in Java vs Scala, directly relevant to understanding the Effect Path API's foundation.

- **["Algebraic Data Types with Java"](https://blog.scottlogic.com/2025/01/20/algebraic-data-types-with-java.html)** (Scott Logic, 2025): A thorough introduction to algebraic data types using Java's sealed interfaces and records. Covers how sum types and product types compose to model complex domains.

### Higher-Kinded-J

- **[Effect Path API](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-core/src/main/java/org/higherkindedj/hkt/effect)**: API reference for the Effect Path types.

- **[Path Factory](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/effect/Path.java)**: Factory methods for creating Effect Paths.

- **[Semigroups](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/Semigroups.java)**: Common semigroup implementations for error accumulation.

- **[Focus DSL Guide](https://higher-kinded-j.github.io/latest/optics/ch4_intro.html)**: Fluent navigation with FocusPath, AffinePath, and TraversalPath.

- **[Effect Path API Guide](https://higher-kinded-j.github.io/latest/effect/ch_intro.html)**: Railway-style error handling with MaybePath, EitherPath, ValidationPath, and VTaskPath.

- **[VTask and Structured Concurrency](https://higher-kinded-j.github.io/latest/monads/vtask_monad.html)**: Virtual thread concurrency with Scope and Resource.

- **[Focus-Effect Integration](https://higher-kinded-j.github.io/latest/effect/focus_integration.html)**: Bridging the optics and effects domains with `toXxxPath()` and `focus()` methods.

---

*Next: [Article 6: Retrospective and Real-World Applications](article-6-retrospective.md)*
