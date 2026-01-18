# Structured Concurrency with Scope
## _Fluent API for Task Coordination_

~~~admonish info title="What You'll Learn"
- Using `Scope` for flexible structured concurrent computations
- Choosing between `allSucceed`, `anySucceed`, `firstComplete`, and `accumulating` joiners
- Error accumulation using `Validated` for validation scenarios
- Safe result handling with `Try`, `Either`, and `Maybe` wrappers
- Understanding the `ScopeJoiner` interface for custom joining behavior
~~~

~~~admonish warning title="Preview API Notice"
`Scope` and `ScopeJoiner` wrap Java's **structured concurrency APIs** (JEP 505/525), currently in **preview**. These APIs are stable but may see minor changes before finalisation in a near-future Java release. HKJ's abstractions provide a stable interface whilst adapting to any underlying changes.
~~~

~~~admonish example title="See Example Code"
[ScopeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ScopeExample.java)
~~~

> *"Structured concurrency is to concurrent programming what structured programming was to sequential programming."*
> — **Ron Pressler**, Project Loom Lead

While `Par` combinators provide simple parallel execution, `Scope` offers a more flexible, fluent API for structured concurrent computations. It wraps Java 25's `StructuredTaskScope` with functional result handling.

```
┌──────────────────────────────────────────────────────────────────┐
│                          Scope API                               │
│                                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐   │
│  │  create  │ → │   fork   │ → │ configure│ → │     join     │   │
│  │  scope   │   │  tasks   │   │ timeout  │   │   results    │   │
│  └──────────┘   └──────────┘   └──────────┘   └──────────────┘   │
│                                                                  │
│  Factory methods: allSucceed, anySucceed, firstComplete,         │
│                   accumulating, withJoiner                       │
└──────────────────────────────────────────────────────────────────┘
```

---

## Scope Usage Patterns

~~~admonish example title="Basic Scope Patterns"

```java
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

// All-succeed: wait for all tasks to complete successfully
VTask<List<String>> allResults = Scope.<String>allSucceed()
    .fork(VTask.of(() -> fetchUser(id)))
    .fork(VTask.of(() -> fetchProfile(id)))
    .fork(VTask.of(() -> fetchPreferences(id)))
    .join();

// Any-succeed: first successful result wins
VTask<String> fastest = Scope.<String>anySucceed()
    .fork(VTask.of(() -> fetchFromServerA()))
    .fork(VTask.of(() -> fetchFromServerB()))
    .fork(VTask.of(() -> fetchFromServerC()))
    .join();

// First-complete: return first result (success or failure)
VTask<Data> racing = Scope.<Data>firstComplete()
    .fork(riskyFastPath())
    .fork(reliableSlowPath())
    .join();

// With timeout
VTask<List<String>> withTimeout = Scope.<String>allSucceed()
    .timeout(Duration.ofSeconds(5))
    .fork(slowTask1())
    .fork(slowTask2())
    .join();

// Fork multiple tasks at once
List<VTask<Integer>> tasks = ids.stream()
    .map(id -> VTask.of(() -> processId(id)))
    .toList();

VTask<List<Integer>> all = Scope.<Integer>allSucceed()
    .forkAll(tasks)
    .join();
```
~~~

---

## Scope Factory Methods

| Method | Behavior | Use Case |
|--------|----------|----------|
| `allSucceed()` | Wait for all to succeed; fail on first failure | Parallel fetches that all must complete |
| `anySucceed()` | Return first success; cancel others | Racing redundant requests |
| `firstComplete()` | Return first result (success or failure) | Fast-path with fallback |
| `accumulating(mapper)` | Collect all errors; never fail-fast | Validation scenarios |
| `withJoiner(joiner)` | Custom joiner behavior | Advanced use cases |

### When to Use Each Joiner

**allSucceed** is the default choice when you need results from multiple independent operations:

```java
// Fetch user data from three services - need all of them
VTask<List<UserData>> userData = Scope.<UserData>allSucceed()
    .fork(VTask.of(() -> authService.getPermissions(userId)))
    .fork(VTask.of(() -> profileService.getProfile(userId)))
    .fork(VTask.of(() -> prefsService.getPreferences(userId)))
    .join();
```

**anySucceed** is ideal for redundant requests where any response will do:

```java
// Try multiple mirrors - first success wins
VTask<Package> download = Scope.<Package>anySucceed()
    .fork(VTask.of(() -> fetchFrom(mirror1)))
    .fork(VTask.of(() -> fetchFrom(mirror2)))
    .fork(VTask.of(() -> fetchFrom(mirror3)))
    .join();
```

**firstComplete** captures the first result regardless of success or failure:

```java
// Race a fast but unreliable path against a slow but reliable one
VTask<Result> result = Scope.<Result>firstComplete()
    .fork(VTask.of(() -> fastButRisky()))
    .fork(VTask.of(() -> slowButSafe()))
    .join();
```

---

## Safe Result Handling

Scope provides functional result wrappers that capture failures instead of throwing:

```java
// Get result as Try
VTask<Try<List<String>>> trySafe = Scope.<String>allSucceed()
    .fork(task1())
    .fork(task2())
    .joinSafe();

// Get result as Either
VTask<Either<Throwable, List<String>>> eitherResult = Scope.<String>allSucceed()
    .fork(task1())
    .fork(task2())
    .joinEither();

// Get result as Maybe (Nothing on failure)
VTask<Maybe<List<String>>> maybeResult = Scope.<String>allSucceed()
    .fork(task1())
    .fork(task2())
    .joinMaybe();
```

These wrappers are useful when you want to handle failures within your VTask pipeline rather than at the execution boundary.

---

## Error Accumulation with ScopeJoiner

The `accumulating` joiner is particularly powerful for validation scenarios where you want to report all errors at once rather than failing on the first error.

~~~admonish example title="Accumulating Errors"

```java
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.validated.Validated;

// Define how to convert exceptions to your error type
record ValidationError(String field, String message) {
    static ValidationError from(Throwable t) {
        return new ValidationError("unknown", t.getMessage());
    }
}

// Validate multiple fields in parallel, collecting all errors
VTask<Validated<List<ValidationError>, List<String>>> validation =
    Scope.<String>accumulating(ValidationError::from)
        .fork(VTask.of(() -> validateUsername(input.username())))
        .fork(VTask.of(() -> validateEmail(input.email())))
        .fork(VTask.of(() -> validatePassword(input.password())))
        .join();

// Handle the result
Validated<List<ValidationError>, List<String>> result = validation.run();

result.fold(
    errors -> {
        // All errors collected, not just the first
        errors.forEach(err ->
            System.out.println(err.field() + ": " + err.message()));
        return null;
    },
    successes -> {
        System.out.println("All validations passed: " + successes);
        return null;
    }
);
```
~~~

### Why Error Accumulation Matters

Traditional fail-fast behavior returns only the first error encountered. This frustrates users who must fix one error, resubmit, discover another error, fix it, resubmit again, and so on. Error accumulation runs all validations in parallel and collects every failure, enabling you to report all problems at once:

```java
// User submits a form with multiple invalid fields
// Instead of: "Username is too short" (fix, resubmit)
//             "Email is invalid" (fix, resubmit)
//             "Password needs a number" (fix, resubmit)
//
// You can show all three errors immediately
```

---

## ScopeJoiner Interface

`ScopeJoiner` wraps Java 25's `StructuredTaskScope.Joiner` with HKJ-friendly accessors:

```java
import org.higherkindedj.hkt.vtask.ScopeJoiner;

// Access joiner factories
ScopeJoiner<String, List<String>> allSucceed = ScopeJoiner.allSucceed();
ScopeJoiner<String, String> anySucceed = ScopeJoiner.anySucceed();
ScopeJoiner<String, String> firstComplete = ScopeJoiner.firstComplete();
ScopeJoiner<String, Validated<List<Error>, List<String>>> accum =
    ScopeJoiner.accumulating(Error::from);

// Use with custom Scope
VTask<List<String>> result = Scope.withJoiner(allSucceed)
    .fork(task1())
    .fork(task2())
    .join();

// Access underlying Java 25 Joiner when needed
StructuredTaskScope.Joiner<String, List<String>> java25Joiner =
    allSucceed.joiner();

// Get result wrapped in Either (functional error handling)
Either<Throwable, List<String>> eitherResult = allSucceed.resultEither();
```

### ScopeJoiner Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `joiner()` | `StructuredTaskScope.Joiner<T, R>` | Access underlying Java 25 Joiner |
| `resultEither()` | `Either<Throwable, R>` | Get result wrapped in Either |

---

~~~admonish info title="Key Takeaways"
* **Scope** provides a fluent builder for structured concurrency with configurable joining strategies
* **allSucceed** waits for all tasks and fails fast on any failure
* **anySucceed** returns the first success and cancels remaining tasks
* **firstComplete** returns the first result regardless of success or failure
* **accumulating** collects all errors using `Validated`, ideal for validation
* **Safe wrappers** (`joinSafe`, `joinEither`, `joinMaybe`) capture failures functionally
* **ScopeJoiner** bridges HKJ types with Java 25's native Joiner interface
~~~

~~~admonish info title="Hands-On Learning"
Practice Scope patterns in [Tutorial: Scope & Resource](../tutorials/concurrency/scope_resource_journey.md) (6 exercises, ~15 minutes).
~~~

~~~admonish tip title="See Also"
- [VTask Monad](vtask_monad.md) - Core VTask type and basic operations
- [Resource Management](vtask_resource.md) - Bracket pattern for safe resource handling
- [Validated](validated_monad.md) - Error accumulation type used with `accumulating` joiner
~~~

~~~admonish abstract title="Further Reading"
- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505) - The official OpenJDK proposal for Java's structured concurrency API
- [Notes on structured concurrency, or: Go statement considered harmful](https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/) - Nathaniel J. Smith's influential 2018 article that popularized structured concurrency concepts
- [Project Loom: Structured Concurrency in Java](https://rockthejvm.com/articles/structured-concurrency-in-java) - Practical tutorial with Java examples
~~~

---

**Previous:** [VTask Monad](vtask_monad.md)
**Next:** [Resource Management](vtask_resource.md)
