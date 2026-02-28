# The Lazy Monad:
## _Lazy Evaluation with `Lazy`_

~~~admonish info title="What You'll Learn"
- How to defer expensive computations until their results are actually needed
- Understanding memoization: compute once, read many times
- Composing lazy operations with `map` and `flatMap` while preserving laziness
- Handling exceptions in lazy computations with `ThrowableSupplier`
- Choosing between `Lazy` and `IO` for deferred work
~~~

~~~ admonish example title="See Example Code:"
[LazyExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/lazy/LazyExample.java)
~~~

## The Problem: Doing Work You'll Throw Away

Imagine a dashboard that assembles a user summary from three data sources:

```java
String buildDashboard(String userId) {
    var profile         = fetchUserProfile(userId);      // 200ms
    var recommendations = fetchRecommendations(userId);  // 800ms
    var analytics       = fetchAnalytics(userId);        // 500ms
    return summarize(profile, recommendations, analytics);
}
```

Every call pays the full 1500ms cost. But 90% of callers only need the profile. The other two results are assembled, inspected, and thrown away. That is 1300ms of wasted work on nearly every request.

```
EAGER (always runs everything):         LAZY (runs on demand):
  fetchUserProfile()    -> 200ms done     defer(fetchUserProfile)     -> 0ms
  fetchRecommendations()-> 800ms waste    defer(fetchRecommendations) -> 0ms
  fetchAnalytics()      -> 500ms waste    defer(fetchAnalytics)       -> 0ms
  Total: 1500ms                          force(userProfile)          -> 200ms
                                         Total: 200ms (saved 87%)
```

With `Lazy`, each computation is wrapped in a deferred shell. Nothing runs until you explicitly call `force()`. If you never force a value, you never pay its cost.

This is not a niche optimization. Any time your code builds a data structure with fields that are expensive to populate but cheap to skip, laziness eliminates wasted work at zero architectural cost. The calling code decides what to evaluate -- not the producer.

## The Fix: Defer and Force

`Lazy<A>` stores a computation without executing it. Call `defer()` to wrap the work; call `force()` when you actually need the result. After the first `force()`, the result is cached -- subsequent calls return instantly with zero recomputation.

```java
Kind<LazyKind.Witness, String> profile = LAZY.defer(() -> fetchUserProfile(userId));
// Nothing has executed yet.

String result = LAZY.force(profile);  // Now it runs -- once.
String cached = LAZY.force(profile);  // Returns the cached value instantly.
```

This is the "compute once, read many" guarantee. Whether you force the value twice or two thousand times, the underlying supplier runs exactly once. The first caller pays the cost; every subsequent caller gets the answer for free.

Contrast this with a raw `Supplier<T>`, which re-executes on every `.get()` call. `Lazy` gives you the same deferred interface with built-in caching and exception handling baked in.

## Core Components

**The Lazy Type**

![lazy_class.svg](../images/puml/lazy_class.svg)

**The HKT Bridge for Lazy**

![lazy_kind.svg](../images/puml/lazy_kind.svg)

**Typeclasses for Lazy**

![lazy_monad.svg](../images/puml/lazy_monad.svg)

| Component | Role |
|-----------|------|
| `ThrowableSupplier<T>` | Like `Supplier`, but its `get()` may throw any `Throwable` -- the computation source for `Lazy` |
| `Lazy<A>` | Core class: wraps a supplier, evaluates on `force()`, and memoizes the result (or exception) |
| `LazyKind<A>` | HKT marker (`Kind<LazyKind.Witness, A>`) so `Lazy` can participate in generic typeclass code |
| `LazyKindHelper` | Bridge utilities: `widen`, `narrow`, `defer`, `now`, `force` for converting between `Lazy` and `LazyKind` |
| `LazyMonad` | Typeclass instance implementing `Monad`, `Applicative`, and `Functor` for `LazyKind.Witness` |

`LazyKindHelper` deserves special attention. It is your primary API surface when working with `Lazy` in generic HKT code. Its static methods handle the wrapping and unwrapping so you can stay in the `Kind<LazyKind.Witness, A>` world without manual casting:

- **`defer(supplier)`** -- create a deferred `LazyKind` from a `ThrowableSupplier`
- **`now(value)`** -- create an already-evaluated `LazyKind`
- **`force(kind)`** -- unwrap and evaluate, returning the cached result or throwing the cached exception
- **`widen(lazy)`** / **`narrow(kind)`** -- convert between `Lazy<A>` and `Kind<LazyKind.Witness, A>`

~~~admonish note title="How Lazy Evaluation Works"
```
defer(() -> compute())          force()            force()
       |                           |                  |
       v                           v                  v
  [unevaluated]  -------->  [compute & cache]  --->  [return cached]
   Supplier stored       Supplier called once     No recomputation
```

The `Supplier` is stored on creation, invoked exactly once on the first `force()`, and the result (or exception) is cached for all subsequent calls.
~~~

~~~admonish tip title="Memoization in Action"
The first `force()` pays the full computation cost. Every call after that is effectively free:

```
force() #1  -->  supplier runs  -->  result stored  -->  200ms
force() #2  -->  cache hit      -->  result returned -->    0ms
force() #3  -->  cache hit      -->  result returned -->    0ms
  ...
force() #N  -->  cache hit      -->  result returned -->    0ms
```

This makes `Lazy` ideal for values that are expensive to produce but read frequently -- configuration lookups, parsed templates, compiled patterns, and similar compute-once artifacts.
~~~

~~~admonish example title="Example 1: Deferred Computation"
Creating lazy values does no work. Forcing them does -- exactly once.

```java
AtomicInteger counter = new AtomicInteger(0);

// Deferred: the supplier is stored, not called
Kind<LazyKind.Witness, String> deferred = LAZY.defer(() -> {
    counter.incrementAndGet();
    Thread.sleep(50); // simulate work
    return "Computed Value";
});

// Already-evaluated: no supplier to call later
Kind<LazyKind.Witness, String> ready = LAZY.now("Precomputed Value");

System.out.println("Counter after creation: " + counter.get()); // 0

String result1 = LAZY.force(deferred);  // runs the supplier
System.out.println(result1);            // "Computed Value"
System.out.println("Counter: " + counter.get()); // 1

String result2 = LAZY.force(deferred);  // returns cached value
System.out.println("Counter: " + counter.get()); // still 1 -- no recomputation

String resultNow = LAZY.force(ready);   // "Precomputed Value" -- counter unchanged
```

Exceptions follow the same rule: if the computation throws on the first `force()`, that exception is cached. Every subsequent `force()` rethrows the same exception without re-executing the supplier. This means error behavior is deterministic -- you will never see a computation fail once, then succeed on retry through the same `Lazy` instance.
~~~

~~~admonish example title="Example 2: Composing with map and flatMap"
`LazyMonad` lets you chain transformations without triggering evaluation. Only the final `force()` runs the entire pipeline.

```java
LazyMonad lazyMonad = LazyMonad.INSTANCE;
AtomicInteger counter = new AtomicInteger(0);

Kind<LazyKind.Witness, Integer> base = LAZY.defer(() -> {
    counter.incrementAndGet();
    return 10;
});

// map: transform the eventual value without forcing it
Kind<LazyKind.Witness, String> mapped = lazyMonad.map(i -> "Value: " + i, base);
System.out.println("Counter after map: " + counter.get()); // 0

System.out.println(LAZY.force(mapped)); // "Value: 10"
System.out.println("Counter: " + counter.get()); // 1

// flatMap: sequence two lazy computations
Kind<LazyKind.Witness, String> chained = lazyMonad.flatMap(
    v1 -> lazyMonad.map(
        v2 -> "Combined: " + v1 + " & " + v2,
        LAZY.defer(() -> v1 * 2)  // second step depends on first
    ),
    LAZY.defer(() -> 5)           // first step
);

System.out.println(LAZY.force(chained)); // "Combined: 5 & 10"
```

Neither `map` nor `flatMap` triggers evaluation -- they build a new `Lazy` that will run the full chain when forced. This means you can construct an entire pipeline of transformations upfront, and the cost of the whole pipeline is paid only at the single point where `force()` is called.
~~~

~~~admonish warning title="Lazy vs IO: Know the Difference"
**`Lazy`** defers **pure computation** -- work that depends only on its inputs and always produces the same result. The memoized value is safe to reuse because it never changes.

**`IO`** defers **side effects** -- work that reads files, calls APIs, writes to databases, or depends on external state. Each execution may produce a different result, so memoization would give stale answers.

| Question | Answer |
|----------|--------|
| Does it read a file, call an API, or write to a database? | Use `IO` |
| Does it compute a value from pure inputs? | Use `Lazy` |
| Should repeated calls return the same cached result? | Use `Lazy` |
| Should repeated calls re-execute the effect? | Use `IO` |

**Rule of thumb:** if your computation talks to the outside world, use [`IO`](./io_monad.md). If it only crunches data, use `Lazy`.
~~~

## When to Use Lazy

| Scenario | Recommendation |
|----------|----------------|
| Deferring expensive computation until needed | `Lazy` / `LazyMonad` |
| Composing deferred computations while preserving laziness | `LazyMonad` -- `map`/`flatMap` don't trigger evaluation |
| Caching computation results (memoization) | `Lazy` -- result is cached after first `force()` |
| Computations that may throw checked exceptions | `Lazy` -- wraps `ThrowableSupplier` |
| Building data structures with optional expensive fields | `Lazy` -- callers force only what they need |
| Configuration values loaded once and reused | `Lazy` -- natural fit for compute-once semantics |
| Deferred side effects with execution control | Prefer [IO](./io_monad.md) instead |

~~~admonish important title="Key Points"
- `Lazy<A>` wraps a `ThrowableSupplier<A>` -- nothing executes until `force()` is called.
- Results are **memoized**: the first `force()` computes and caches; subsequent calls return the cached value instantly.
- Exceptions are also memoized -- if the computation throws on first `force()`, the same exception is rethrown on subsequent calls.
- `map` and `flatMap` via `LazyMonad` produce new `Lazy` values without triggering evaluation of the input.
- `Lazy.now(value)` creates an already-evaluated instance -- useful for lifting pure values into the Lazy context.
- `ThrowableSupplier` allows checked exceptions -- no need for awkward `try`/`catch` inside lambdas.
- Thread safety: `Lazy` ensures the supplier runs at most once, even under concurrent `force()` calls.
~~~

---

~~~admonish example title="Benchmarks"
Lazy has dedicated JMH benchmarks measuring deferred construction, memoization overhead, and chain depth. Key expectations:

- **Construction** (`defer`, `now`) is very fast -- Lazy is a thin wrapper with no immediate execution
- **First `force()`** incurs the full computation cost; subsequent calls return the cached result
- **Deep chains (50+)** of `map`/`flatMap` complete without error -- composition overhead dominates at depth

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*LazyBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details and how to interpret results.
~~~

---

**Previous:** [IO](io_monad.md)
**Next:** [List](list_monad.md)
