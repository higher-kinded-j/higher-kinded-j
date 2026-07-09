# Resilience Patterns

> *"Success consists of going from failure to failure without loss of enthusiasm."*
>
> -- Winston Churchill

---

A retry loop, distilled to its essence. Churchill was describing political life, but the sentiment maps precisely onto what a well-designed distributed system does dozens of times per second. The database times out; the system retries with a longer delay. The upstream service returns a 503; the circuit breaker trips, waits, then cautiously probes again. Each failure is met not with panic but with a policy: how long to wait, how many times to try, when to stop trying, and what to do instead.

This chapter introduces four resilience patterns that encode this discipline into your code. A **Retry** policy re-attempts transient failures with configurable backoff strategies. A **Circuit Breaker** remembers recent failures and prevents your system from wasting effort on a dependency that is clearly down. A **Bulkhead** limits how many concurrent callers can access a shared resource, so that one slow service cannot consume all available capacity. A **Saga** coordinates multi-step operations with compensation logic, so that when step four of five fails, the earlier steps are automatically undone.

```
    Retry timeline (exponential backoff with jitter):

    attempt 1       attempt 2             attempt 3
        │               │                     │
        ▼               ▼                     ▼
    ────X───────────────X─────────────────────X──────────────────✓
        │   ~200ms wait │     ~800ms wait     │
        │               │                     │
        fail            fail                  succeed

    Circuit Breaker states:

    ┌────────┐  failures ≥ threshold  ┌────────┐  timeout expires  ┌───────────┐
    │ CLOSED │───────────────────────▶│  OPEN  │──────────────────▶│ HALF_OPEN │
    │        │                        │        │                   │           │
    │ allow  │◀───────────────────────│ reject │                   │   probe   │
    │  all   │  successes ≥ threshold │  all   │◀──────────────────│  (allow   │
    └────────┘                        └────────┘   probe fails     │   one)    │
                                                                   └───────────┘
```

Higher-Kinded-J's resilience patterns integrate directly with `VTask`, `VStream`, and the Effect Path API. You compose them as combinators in the same fluent chains you already use for mapping, error handling, and parallel execution. A retry is just another method on a `VTaskPath`, no different in character from `map` or `timeout`. They are lazy, composable, and type-safe: they describe resilience as structure, not as an afterthought.

The full `with*` vocabulary (`withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`) is available across the Path family. The lazy carriers (`IOPath`, `VTaskPath`, `VResultPath`) chain the combinators as instance methods; on the eager `EitherPath` the same combinators are static, taking the step as a `Supplier`, because resilience wraps a *computation* and an eager path has already run. On the typed-error carriers the combinators are **railway-aware**: a business `Left` is a value, never retried and never counted as a circuit-breaker failure, while typed overloads land timeouts and rejections as `Left`s rather than thrown exceptions.

---

~~~admonish info title="In This Chapter"
- **[Retry](retry.md)**: `RetryPolicy` configuration with fixed, exponential, and jittered backoff strategies. Selective retry based on exception type. Path-native `withRetry` on every carrier, including railway-aware typed retry on `EitherPath` and `VResultPath`.

- **[Circuit Breaker](circuit_breaker.md)** -- A state machine that tracks dependency health across three states (closed, open, half-open). Protects recovering services from being overwhelmed by callers that have not yet noticed the failure.

- **[Bulkhead](bulkhead.md)** -- Semaphore-based concurrency limiting that prevents a single slow dependency from exhausting shared capacity. Configurable permits, fairness, and timeout behaviour.

- **[Saga](saga.md)** -- Compensating transactions for multi-step distributed operations. Each forward step registers a corresponding undo action; on failure, compensations execute in reverse order to restore consistency.

- **[Combined Patterns](combined.md)**: Composing multiple resilience patterns into layered defences. The `ResilienceBuilder` applies patterns in the correct order: timeout outermost, then bulkhead, then retry, then circuit breaker innermost. Plus the per-carrier availability table for the path-native `with*` combinators and a worked per-step example.
~~~

~~~admonish example title="See Example Code"
- [ResilienceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java) -- Retry policies, backoff strategies, and combined patterns
- [ConfigurableOrderWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/order/workflow/ConfigurableOrderWorkflow.java): Production-style per-step resilience; retry confined to an idempotent pre-flight, the commit run exactly once under a typed timeout
~~~

---

## Chapter Contents

1. [Retry](retry.md) -- Backoff strategies, selective retry, and exhaustion handling
2. [Circuit Breaker](circuit_breaker.md) -- State machine, configuration, and service protection
3. [Bulkhead](bulkhead.md) -- Concurrency limiting and resource isolation
4. [Saga](saga.md) -- Compensating transactions and distributed consistency
5. [Combined Patterns](combined.md) -- Layered resilience and the ResilienceBuilder

---

**Previous:** [Patterns and Recipes](../effect/patterns.md)
**Next:** [Retry](retry.md)
