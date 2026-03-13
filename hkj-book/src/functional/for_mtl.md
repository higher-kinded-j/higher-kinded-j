# MTL & ForState Bridge

## MTL Integration

The `For` builder composes naturally with [MTL capability interfaces](../transformers/mtl_capabilities.md). Because `MonadReader`, `MonadState`, and `MonadWriter` all extend `Monad<F>`, you can pass any MTL instance directly to `For.from()` and use capability operations as generators.

### Reading Configuration with MonadReader

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    buildConnectionString(MonadReader<F, AppConfig> env) {
  return For.from(env, env.ask())
      .yield(config -> config.dbUrl() + "?retries=" + config.maxRetries());
}
```

The function declares a capability (`MonadReader`) rather than a concrete type. It works with `ReaderT<CompletableFuture, ...>` in production and `ReaderT<Id, ...>` in tests without any change.

### Threading State with MonadState

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer>
    addTwoValues(MonadState<F, Counter> state) {
  return For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
      .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 20)))
      .from(_ -> state.gets(Counter::total))
      .yield((_, _, total) -> total);
}
```

Each `from()` step sees the state left by the previous step. The `For` comprehension handles the threading automatically.

### Accumulating Output with MonadWriter

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    auditedProcess(MonadWriter<F, List<String>> audit, String item) {
  return For.from(audit, audit.tell(List.of("Processing " + item)))
      .from(_ -> audit.tell(List.of("Completed " + item)))
      .yield((_, _) -> "processed-" + item);
}
```

Each `tell()` appends to the accumulated output. The entries combine via the `Monoid` for the output type.

~~~admonish tip title="See Also"
- [MTL Capabilities](../transformers/mtl_capabilities.md) for the full overview
- [MonadReader](../transformers/mtl_reader.md), [MonadState](../transformers/mtl_state.md), [MonadWriter](../transformers/mtl_writer.md) for detailed API guides
- [Combining Capabilities](../transformers/mtl_combining.md) for multi-capability patterns
~~~

---

## Bridging to ForState with `toState()`

When a workflow starts with a few monadic steps (fetching data, computing values) and then needs to thread named state through a series of updates, `toState()` lets you transition seamlessly from `For` into `ForState` mid-comprehension. The accumulated values become the constructor arguments for your state record, and from that point on you work with named fields and lenses instead of tuple positions.

```java
record Dashboard(String user, int count, boolean ready) {}

Lens<Dashboard, Boolean> readyLens = Lens.of(
    Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));
Lens<Dashboard, Integer> countLens = Lens.of(
    Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));

// Start with For (value accumulation), then switch to ForState (named state)
Kind<IdKind.Witness, Dashboard> result =
    For.from(idMonad, Id.of("Alice"))               // a = "Alice"
        .from(name -> Id.of(name.length()))          // b = 5
        .toState((name, count) ->                    // bridge: construct record
            new Dashboard(name, count, false))
        .modify(countLens, c -> c * 10)              // named lens operation
        .update(readyLens, true)
        .yield();

// Dashboard("Alice", 50, true)
```

The `toState()` method is available at every arity (1 through 12) in both **spread-style** and **tuple-style**:

```java
// Spread-style: arguments unpacked
.toState((name, count) -> new Dashboard(name, count, false))

// Tuple-style: single tuple argument
.toState(t -> new Dashboard(t._1(), t._2(), false))
```

When the comprehension uses a `MonadZero` (like `Maybe` or `List`), the returned builder is a `ForState.FilterableSteps`, preserving access to `when()` and `matchThen()` guards:

```java
Kind<MaybeKind.Witness, Dashboard> result =
    For.from(maybeMonad, MAYBE.just("Alice"))
        .toState(name -> new Dashboard(name, 0, false))
        .when(d -> d.user().length() > 3)   // guard still available
        .update(readyLens, true)
        .yield();

// Just(Dashboard("Alice", 0, true))
```

~~~admonish tip title="When to use toState()"
Use `toState()` when your workflow has a natural two-phase shape: **gather** values with `For` (fetching, computing, filtering), then **build and refine** a structured record with `ForState` (lens updates, zooming, traversals). This gives you the best of both worlds: concise tuple-based accumulation for the first few steps, and named field access for the rest.
~~~

## Stateful Updates with ForState

For workflows with more than a few steps, tuple-based access becomes fragile. `ForState` solves this by threading a **named record** through each step, with [lenses](../optics/lenses.md) providing type-safe field access. Every intermediate value has a name, not a position.

```java
// ForState: named fields instead of tuple positions
ForState.withState(monad, monad.of(initialContext))
    .fromThen(ctx -> validateOrder(ctx.orderId()),   validatedLens)
    .fromThen(ctx -> processPayment(ctx),            confirmationLens)
    .when(ctx -> ctx.totalCents() > 0)               // guard (MonadZero)
    .zoom(addressLens)                                // narrow scope
        .update(cityLens, "SPRINGFIELD")
    .endZoom()
    .yield(ctx -> buildReceipt(ctx.user(), ctx.confirmationId()));
```

ForState supports the full range of comprehension operations: pure updates (`update`, `modify`), effectful operations (`from`, `fromThen`), guards (`when`), pattern matching (`matchThen`), bulk traversal (`traverse`), and scope narrowing (`zoom`/`endZoom`).

~~~admonish info title="Full API Reference"
For a complete API reference, side-by-side comparison with `For`, and guidance on when to use each comprehension style, see **[ForState: Named State Comprehensions](forstate_comprehension.md)**.
~~~

---

**Previous:** [Optics Integration](for_optics.md) | **Next:** [ForState: Named State Comprehensions](forstate_comprehension.md)
