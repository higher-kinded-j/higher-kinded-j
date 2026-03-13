# ForPath Parallel Composition

Sequential comprehensions with `.from()` force each step to wait for the previous one, even when two computations don't depend on each other. If you're fetching a user profile and loading application config, there's no reason the config call should wait for the profile to finish. The `par()` combinator lets you declare this independence explicitly, and for `VTaskPath`, that declaration translates into genuine concurrent execution on virtual threads.

## Combining Independent Values

`ForPath.par()` accepts two or three independent Path values and combines them using applicative semantics. The key difference from `.from()` is that `par()` does not thread values sequentially -- it evaluates all branches and merges the results:

```java
// Two independent MaybePaths
MaybePath<String> result =
    ForPath.par(Path.just("Alice"), Path.just(42))
        .yield((name, age) -> name + " is " + age);
// Just("Alice is 42")

// Three independent EitherPaths
EitherPath<String, String> profile =
    ForPath.par(
            Path.<String, String>right("Alice"),
            Path.<String, Integer>right(42),
            Path.<String, String>right("admin"))
        .yield((name, age, role) -> name + " (" + age + ") [" + role + "]");
// Right("Alice (42) [admin]")
```

Short-circuiting works as expected for types with failure semantics -- if any branch fails, the whole computation fails:

```java
MaybePath<String> result =
    ForPath.par(Path.just("Bob"), Path.<Integer>nothing())
        .yield((name, age) -> name + " is " + age);
// Nothing — the second computation failed
```

## VTaskPath: True Parallel Execution

This is where `par()` really shines. For `VTaskPath`, it uses `Par.map2`/`Par.map3` under the hood, which spawns virtual threads via `StructuredTaskScope`. Independent computations genuinely execute concurrently, so the total time is the *maximum* of the individual times rather than the *sum*:

```java
VTaskPath<String> result =
    ForPath.par(
            Path.vtaskPath(() -> fetchUserData(userId)),    // virtual thread 1
            Path.vtaskPath(() -> fetchConfigData()))        // virtual thread 2
        .yield((user, config) -> buildResponse(user, config));

// Both fetches run concurrently; total time ≈ max(fetch1, fetch2)
String response = result.run().run();
```

For two 50ms API calls, this means ~50ms total instead of ~100ms sequential.

## IOPath and IdPath

IO computations can also be combined with `par()`. Execution is currently sequential, but the code documents the independence for future parallel IO support:

```java
IOPath<String> result =
    ForPath.par(Path.io(() -> "hello"), Path.io(() -> "world"))
        .yield((a, b) -> a + " " + b);

String value = result.unsafeRun();  // "hello world"
```

IdPath uses `Id.of()` to wrap pure values -- there is no effect to parallelise, but `par()` still expresses structural independence:

```java
IdPath<Integer> sum =
    ForPath.par(Path.idPath(Id.of(10)), Path.idPath(Id.of(20)), Path.idPath(Id.of(30)))
        .yield((a, b, c) -> a + b + c);
// Id(60)
```

## Chaining After `par()`

The result of `par()` is a regular step, so you can continue the comprehension with `.from()`, `.let()`, `.when()`, or another `.par()`:

```java
MaybePath<String> result =
    ForPath.par(Path.just("Alice"), Path.just(5))
        .let(t -> t._1() + " has " + t._2() + " letters")
        .yield((name, len, sentence) -> sentence.toUpperCase());
// Just("ALICE HAS 5 LETTERS")
```

## When Is `par()` Actually Parallel?

| Path Type | Behaviour |
|-----------|-----------|
| `VTaskPath` | True concurrency via `Par.map2`/`Par.map3` on virtual threads |
| `IOPath` | Sequential (applicative, not parallel) |
| `MaybePath`, `OptionalPath` | Sequential; short-circuits on Nothing/empty |
| `EitherPath`, `TryPath` | Sequential; short-circuits on Left/Failure |
| `NonDetPath` | Cartesian product (applicative semantics) |
| `IdPath` | Immediate; no effect to parallelise |

Even when execution is sequential, `par()` documents the *intent* that the computations are independent, making the dependency structure of your workflow explicit.

~~~admonish tip title="See Also"
For `par()` with raw `Kind` values (no Path wrappers), see [For Parallel Composition](../functional/for_par.md).
~~~

---

**Previous:** [ForPath Examples](forpath_examples.md) | **Next:** [ForPath Traverse](forpath_traverse.md)
