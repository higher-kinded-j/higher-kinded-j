# Production Readiness

Senior engineers evaluating a library for production use inevitably ask three questions: "What do the stack traces look like?", "How much does the abstraction cost?", and "Will it blow the stack?" This page answers all three honestly, with data.

~~~admonish info title="What You'll Learn"
- How to read stack traces from Path chains and add debug logging
- The allocation cost of wrapper objects and why it is negligible in practice
- When recursive chains can overflow the JVM stack and how to prevent it
~~~

---

## Reading Path Chain Stack Traces

Path chains produce deeper stack traces than their imperative equivalents. Each `map`, `via`, or `recover` call adds a frame. This is the trade-off for composability; the good news is that the traces are predictable once you know what to look for.

### An Annotated Example

Consider a simple service pipeline:

```java
EitherPath<AppError, Invoice> result =
    Path.<AppError, String>right(orderId)
        .via(id -> lookupOrder(id))          // step 1
        .via(order -> validateOrder(order))   // step 2
        .map(order -> generateInvoice(order)) // step 3
        .recover(error -> Invoice.empty());   // step 4
```

If `validateOrder` throws an unexpected `NullPointerException`, the stack trace might look like this:

```
java.lang.NullPointerException: Cannot invoke "Address.postcode()" on null reference
    at com.example.OrderService.validateOrder(OrderService.java:47)    // <-- YOUR CODE: the actual failure
    at com.example.OrderService.lambda$process$1(OrderService.java:23) // <-- YOUR CODE: the .via() lambda
    at org.higherkindedj.hkt.either.Either.flatMap(Either.java:142)    //     library internals (skip)
    at org.higherkindedj.hkt.effect.EitherPath.via(EitherPath.java:98) //     library internals (skip)
    at com.example.OrderService.process(OrderService.java:23)          // <-- YOUR CODE: the chain call site
    at com.example.OrderController.handleOrder(OrderController.java:31)
    ...
```

### How to Read Path Stack Traces

The pattern is consistent:

1. **Top of the trace**: your code that threw the exception (the business logic inside the lambda)
2. **Middle frames**: library internals (`Either.flatMap`, `EitherPath.via`); skip these
3. **Lower frames**: the call site where you built the chain, then your normal application frames

**Rule of thumb:** look for your package name in the trace. The topmost frame with your package is the failing business logic; the next one down is the chain step that invoked it.

### Using peek for Debug Logging

When you need to inspect intermediate values without breaking the chain, `peek` provides observation points:

```java
EitherPath<AppError, Invoice> result =
    Path.<AppError, String>right(orderId)
        .via(id -> lookupOrder(id))
        .peek(order -> log.debug("Looked up order: {}", order.id()))
        .via(order -> validateOrder(order))
        .peek(order -> log.debug("Validated order: {}", order.id()))
        .map(order -> generateInvoice(order));
```

`peek` runs a side effect on the success track without changing the value. If the chain has already diverted to the error track, `peek` is skipped. This makes it safe to leave debug logging in place; it only executes on the happy path and has negligible cost.

~~~admonish tip title="Naming Your Lambdas"
For clearer stack traces, extract lambdas into named methods:

```java
// Anonymous lambda: shows as lambda$process$1 in traces
.via(id -> lookupOrder(id))

// Method reference: shows as OrderService.lookupOrder in traces
.via(this::lookupOrder)
```

Method references produce more readable stack frames than anonymous lambdas.
~~~

---

## Allocation Overhead

Every step in a Path chain creates a small wrapper object. A `map` call on `EitherPath` creates one new `Either` and one new `EitherPath`. This is real allocation, and it is worth understanding.

### The Cost in Context

| Operation | Typical Cost | Order of Magnitude |
|-----------|-------------|-------------------|
| Wrapper allocation (`EitherPath`, `MaybePath`) | 5-20 ns | Nanoseconds |
| `map` or `via` step (wrapper + lambda object) | 10-50 ns | Nanoseconds |
| HashMap lookup | 10-100 ns | Nanoseconds |
| JSON serialisation (small object) | 1-10 us | Microseconds |
| Database query (local) | 0.5-5 ms | Milliseconds |
| HTTP request (same data centre) | 1-50 ms | Milliseconds |
| HTTP request (cross-region) | 50-200 ms | Milliseconds |

A typical Path chain of five steps adds roughly 50-250 nanoseconds of overhead. A single database call takes 500,000-5,000,000 nanoseconds. The wrapper overhead is three to four orders of magnitude smaller than any I/O operation your application performs.

### When Overhead Matters

Allocation overhead is not zero, and in two scenarios it deserves attention:

1. **Tight computational loops** processing millions of items per second with no I/O. If you are writing a number-crunching inner loop, use primitive types directly. Path types are designed for orchestrating effectful operations, not replacing arithmetic.

2. **Very long chains** (hundreds of steps). Each step allocates, and the objects are short-lived, which means GC pressure. In practice, chains rarely exceed 10-20 steps. If yours does, consider breaking it into named submethods that each return a Path.

For the vast majority of applications, particularly those performing any I/O, **the overhead is negligible**.

### Measuring It Yourself

The project includes JMH benchmarks that measure construction, execution, and composition overhead for all Path types. To run the most relevant ones:

```bash
./gradlew jmh --includes=".*IOPathBenchmark.*"
./gradlew jmh --includes=".*AbstractionOverheadBenchmark.*"
./gradlew jmh --includes=".*MemoryFootprintBenchmark.*"
```

~~~admonish example title="See Benchmark Code"
- [IOPathBenchmark.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/IOPathBenchmark.java) - Construction, execution, and composition overhead
- [AbstractionOverheadBenchmark.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/AbstractionOverheadBenchmark.java) - Raw Java vs IO vs VTask comparison
- [MemoryFootprintBenchmark.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/MemoryFootprintBenchmark.java) - Allocation rates under GC profiling
- [TrampolineBenchmark.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/TrampolineBenchmark.java) - Stack-safe recursion performance
~~~

~~~admonish tip title="Full Benchmark Suite"
The `hkj-benchmarks` module contains 18 benchmark classes covering every major type in the library — not just Effect Path types. For the full picture including VTask, VStream, Either, Maybe, concurrency scaling, and Java baseline comparisons, see [Benchmarks & Performance](../benchmarks.md).
~~~

---

## Stack Safety and Trampolining

The JVM does not perform Tail Call Optimisation (TCO). Every method call, including every recursive call, adds a frame to the call stack. The default stack size is typically 512KB to 1MB, which allows roughly 5,000-15,000 frames depending on frame size.

### When Path Chains Are Stack-Safe

Most Path usage is inherently stack-safe. A chain like this:

```java
Path.right(value)
    .via(this::step1)
    .via(this::step2)
    .map(this::step3)
    .recover(this::handleError)
```

Each step executes and returns immediately. The chain does not recurse. Even a chain with 50 steps uses only a handful of stack frames at any point; the depth is constant, not proportional to chain length.

### When Recursion Causes Problems

The risk arises when a Path chain calls itself recursively:

```java
// DANGER: recursive Path chain, will overflow for large n
EitherPath<Error, Integer> countdown(int n) {
    if (n <= 0) return Path.right(0);
    return Path.<Error, Integer>right(n)
        .via(x -> countdown(x - 1));  // recursive call adds a stack frame
}

countdown(100_000);  // StackOverflowError
```

Each call to `countdown` adds a frame. For large inputs, this exhausts the stack.

### The Solution: TrampolinePath

`TrampolinePath` converts recursive calls into a loop that uses constant stack space:

```java
// SAFE: trampolined recursion, works for any depth
TrampolinePath<Integer> countdown(int n) {
    if (n <= 0) return TrampolinePath.done(0);
    return TrampolinePath.defer(() -> countdown(n - 1));
}

Integer result = countdown(1_000_000).run();  // completes without overflow
```

`TrampolinePath.done(value)` signals completion. `TrampolinePath.defer(supplier)` describes the next step without executing it. The trampoline runner bounces through deferred steps in a loop, never growing the call stack.

### Quick Decision Guide

| Scenario | Stack-safe? | Action needed |
|----------|------------|---------------|
| Linear chain (`via`, `map`, `recover`) | Yes | None |
| Bounded recursion (depth < 1,000) | Usually | None, but consider `TrampolinePath` as insurance |
| Unbounded recursion (paginated APIs, tree traversal) | No | Use `TrampolinePath` |
| Mutual recursion (A calls B calls A) | No | Use `TrampolinePath` |

~~~admonish tip title="See Also"
- [TrampolinePath](path_trampoline.md) - Creation, usage, and conversion
- [Advanced Topics: Stack-Safe Recursion](advanced_topics.md#stack-safe-recursion-with-trampolinepath) - Deep dive with fibonacci, mutual recursion, and integration patterns
- [Safe Recursion Stack archetype](../transformers/archetypes.md#the-safe-recursion-stack) - When to reach for `TrampolinePath` in enterprise applications
~~~

---

~~~admonish info title="Key Takeaways"
* **Stack traces** from Path chains are deeper but predictable. Look for your package name; the top frame is your failing logic, the next is the chain step that invoked it.
* **Allocation overhead** is real but negligible for any application that performs I/O. Wrapper allocation costs nanoseconds; database calls cost milliseconds.
* **Linear Path chains are stack-safe.** Only unbounded recursion needs `TrampolinePath`, which converts recursion into constant-space iteration.
* **Honesty builds trust.** These are the real trade-offs. For the vast majority of production workloads, they are non-issues.
~~~

---

**Previous:** [Advanced Topics](advanced_topics.md)
