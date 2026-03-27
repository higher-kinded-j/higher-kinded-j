# The WriterT Transformer:
## _Accumulating Output Across Effect Boundaries_

> *"The writer's duty is to keep on writing."*
>
> -- William Styron

A writer accumulates, sentence by sentence, until a complete work emerges. `WriterT` does the same for your computations: each step appends to an output log, and the accumulation travels invisibly through whatever outer effect you are working in.

~~~admonish info title="What You'll Learn"
- How to add **output accumulation** (logging, audit trails, diagnostics) to any monad
- Building workflows that record every step without threading a mutable log
- Understanding the `Monoid` requirement and how it controls output combination
- Using `tell`, `listen`, `pass`, and `censor` within transformer contexts
- The relationship between `Writer` and `WriterT<F, W, A>`
~~~

---

## The Problem: Logs That Vanish Into Outer Effects

Consider an async pipeline that needs an audit trail:

```java
// Without WriterT: manually pairing results with logs
CompletableFuture<Pair<BigDecimal, List<String>>> applyDiscount(
        BigDecimal price, List<String> logSoFar) {
    return CompletableFuture.supplyAsync(() -> {
        BigDecimal discounted = price.multiply(new BigDecimal("0.9"));
        List<String> newLog = new ArrayList<>(logSoFar);
        newLog.add("Applied 10% discount");
        return Pair.of(discounted, newLog);
    });
}

CompletableFuture<Pair<BigDecimal, List<String>>> addShipping(
        BigDecimal price, List<String> logSoFar) {
    return CompletableFuture.supplyAsync(() -> {
        BigDecimal withShipping = price.add(new BigDecimal("5.00"));
        List<String> newLog = new ArrayList<>(logSoFar);
        newLog.add("Added shipping");
        return Pair.of(withShipping, newLog);
    });
}
```

Every function must accept a log, copy it, append to it, and return it alongside the result. The log leaks into every signature. Composition requires manual log threading at every call site. Miss one handoff and entries disappear.

## The Solution: WriterT

```java
// With WriterT: the log accumulates automatically
WriterTMonad<CompletableFutureKind.Witness, List<String>> writerMonad =
    new WriterTMonad<>(futureMonad, listMonoid);

Kind<WriterTKind.Witness<CompletableFutureKind.Witness, List<String>>, BigDecimal>
    workflow = writerMonad.flatMap(
        price -> writerMonad.flatMap(
            discounted -> writerMonad.flatMap(
                _ -> writerMonad.of(discounted),
                writerMonad.tell(List.of("Added shipping"))),
            writerMonad.flatMap(
                _ -> writerMonad.of(price.multiply(new BigDecimal("0.9"))),
                writerMonad.tell(List.of("Applied 10% discount")))),
        writerMonad.of(new BigDecimal("100.00")));

// One flatMap chain. The log combines via List's Monoid. No manual threading.
```

```
    ┌──────────────────────────────────────────────────────────┐
    │  WriterT<CompletableFutureKind.Witness, List<String>, A> │
    │                                                          │
    │   Outer monad: CompletableFuture (async execution)       │
    │   Output type: List<String>    (audit entries)           │
    │   Value type:  A               (computation result)      │
    │                                                          │
    │   Internally wraps:                                      │
    │     CompletableFuture<Pair<A, List<String>>>             │
    │                                                          │
    │   flatMap ──▶ sequences steps, combines logs via Monoid  │
    │   tell ────▶ appends entries, returns Unit               │
    │   listen ──▶ exposes accumulated log alongside result    │
    │   censor ──▶ transforms the log (e.g. filter sensitive)  │
    └──────────────────────────────────────────────────────────┘
```

---

## How WriterT Works

`WriterT<F, W, A>` wraps `Kind<F, Pair<A, W>>`. The outer monad `F` provides the computational context (async, optional, error-handling); the `Pair<A, W>` carries both the computed value and the accumulated output.

```
    ┌───────────────────────────────────────────────────────────────┐
    │  WriterT<F, W, A>                                             │
    │                                                               │
    │  ┌─── Kind<F, Pair<A, W>> ─────────────────────────────────┐  │
    │  │                                                         │  │
    │  │  F provides:  async / optional / error / identity       │  │
    │  │  Pair.first:  the computed value (A)                    │  │
    │  │  Pair.second: the accumulated output (W)                │  │
    │  │                                                         │  │
    │  └─────────────────────────────────────────────────────────┘  │
    │                                                               │
    │  Monoid<W> controls combination:                              │
    │    empty()         →  starting output for of()                │
    │    combine(w1, w2) →  merges outputs during flatMap           │
    └───────────────────────────────────────────────────────────────┘
```

* **`F`**: The witness type of the **outer monad** (e.g., `IdKind.Witness`, `CompletableFutureKind.Witness`).
* **`W`**: The **output type** that accumulates. Must have a `Monoid<W>` instance.
* **`A`**: The type of the computed value.
* **`run()`**: Returns the wrapped `Kind<F, Pair<A, W>>`.

```java
public record WriterT<F, W, A>(Kind<F, Pair<A, W>> run)
    implements WriterTKind<F, W, A> {
  // ... static factory methods ...
}
```

---

## Setting Up WriterTMonad

The `WriterTMonad<F, W>` class implements both `Monad` and `MonadWriter`, providing monadic operations and output accumulation. It requires a `Monad<F>` for the outer monad and a `Monoid<W>` for combining outputs:

```java
// String output with concatenation
Monoid<String> stringMonoid = new Monoid<>() {
    public String empty() { return ""; }
    public String combine(String a, String b) { return a + b; }
};

Monad<IdKind.Witness> idMonad = IdMonad.instance();

WriterTMonad<IdKind.Witness, String> writerMonad =
    new WriterTMonad<>(idMonad, stringMonoid);
```

~~~admonish note title="Type Witness and Helpers"
**Witness Type:** `WriterTKind<F, W, A>` extends `Kind<WriterTKind.Witness<F, W>, A>`. The outer monad `F` and output type `W` are fixed; `A` is the variable value type.

**KindHelper:** `WriterTKindHelper` provides `WRITER_T.widen` and `WRITER_T.narrow` for safe conversion.

```java
Kind<WriterTKind.Witness<F, W>, A> kind = WRITER_T.widen(writerT);
WriterT<F, W, A> concrete = WRITER_T.narrow(kind);
```
~~~

---

## The Monoid Requirement

The `Monoid<W>` determines how output from successive steps is combined. This is the engine that makes automatic log accumulation work:

| Monoid | `empty()` | `combine(a, b)` | Use Case |
|--------|-----------|------------------|----------|
| `String` | `""` | `a + b` | Simple text logs |
| `List<T>` | `[]` | concatenation | Structured audit entries |
| `Integer` (sum) | `0` | `a + b` | Counting operations |
| `Set<T>` | `{}` | union | Collecting unique tags |

Without a `Monoid`, WriterT cannot combine the output from `flatMap` chains. The `Monoid` is what makes the accumulation lawful: `combine(empty(), w) == w`, `combine(w, empty()) == w`, and `combine` is associative.

---

## Key Operations

~~~admonish info title="Core Operations"
* **`of(value)`**: Lifts a pure value with empty output. Result: `F<Pair(value, empty)>`.
* **`map(f, ma)`**: Transforms the value, preserves output unchanged.
* **`flatMap(f, ma)`**: Sequences computations. Runs `ma` to get `(a, w1)`, applies `f(a)` to get `(b, w2)`, returns `(b, combine(w1, w2))`.
* **`tell(w)`**: Appends `w` to the output. Returns `Unit`.
* **`listen(ma)`**: Runs `ma` and returns `Pair(Pair(a, w), w)` -- the result paired with its accumulated output.
* **`pass(ma)`**: Runs `ma` which returns `Pair(a, f)`, then applies `f` to transform the output.
* **`listens(f, ma)`**: Like `listen`, but maps a function over the accumulated output in the pair.
* **`censor(f, ma)`**: Modifies the accumulated output without seeing the result.
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of a `WriterT` without touching the accumulated output. Perhaps you want to wrap an `Id`-based writer into an `Optional` context for a downstream API, or switch effect types via a natural transformation.

`mapT` applies a function to the wrapped `Kind<F, Pair<A, W>>` and produces a new `WriterT<G, W, A>`:

```
  WriterT< F , W, A >  ── mapT(f) ──>  WriterT< G , W, A >
       │                                      │
  ┌────┴────┐                            ┌────┴────┐
  │    F    │     f: F[...] -> G[...]    │    G    │
  │ ┌─────┐ │          ====>             │ ┌─────┐ │
  │ │Pair │ │   inner Pair untouched     │ │Pair │ │
  │ │ A,W │ │                            │ │ A,W │ │
  │ └─────┘ │                            │ └─────┘ │
  └─────────┘                            └─────────┘
```

```java
// Switch from Id to Optional — wrapping a pure writer into an optional context
WriterT<IdKind.Witness, List<String>, String> idWriter =
    WriterT.writer(idMonad, "result", List.of("step 1", "step 2"));

WriterT<OptionalKind.Witness, List<String>, String> optWriter =
    idWriter.mapT(idKind -> {
      Pair<String, List<String>> pair = ID.unwrap(idKind);
      return OPTIONAL.widen(Optional.of(pair));
    });
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* inside the `Pair` (the `A` in `Pair<A, W>`).
`mapT` transforms the *outer monad* wrapping the `Pair` — the `F` in `F<Pair<A, W>>`.
The accumulated output `W` is completely unaffected.
~~~

---

## Creating WriterT Instances

```java
Monad<IdKind.Witness> idMonad = IdMonad.instance();
Monoid<String> stringMonoid = /* as above */;

// 1. Pure value with empty output
WriterT<IdKind.Witness, String, Integer> pure =
    WriterT.of(idMonad, stringMonoid, 42);
// → Pair(42, "")

// 2. Record output with no meaningful value
WriterT<IdKind.Witness, String, Unit> logged =
    WriterT.tell(idMonad, "initialised; ");
// → Pair(Unit.INSTANCE, "initialised; ")

// 3. Lift an outer monad value with empty output
Kind<IdKind.Witness, Integer> idValue = IdKindHelper.ID.widen(new Id<>(42));
WriterT<IdKind.Witness, String, Integer> lifted =
    WriterT.liftF(idMonad, stringMonoid, idValue);
// → Pair(42, "")

// 4. Explicit value and output
WriterT<IdKind.Witness, String, Integer> explicit =
    WriterT.writer(idMonad, 42, "created; ");
// → Pair(42, "created; ")

// 5. From an existing Kind<F, Pair<A, W>>
WriterT<IdKind.Witness, String, Integer> fromKind =
    WriterT.fromKind(idMonad.of(Pair.of(42, "restored; ")));
// → Pair(42, "restored; ")
```

---

## Real-World Example: Audit Trail

~~~admonish Example title="Building an Audit Trail"

**The problem:** You have a multi-step order processing pipeline and need to record every decision for compliance. The log must travel with the computation, not sit in a mutable side channel.

**The solution:**

```java
WriterTMonad<IdKind.Witness, List<String>> audit =
    new WriterTMonad<>(idMonad, listMonoid);

// Each step records what it did
var validateOrder = audit.flatMap(
    _ -> audit.of("order-123"),
    audit.tell(List.of("Validated order")));

var applyDiscount = audit.flatMap(
    orderId -> audit.flatMap(
        _ -> audit.of(new BigDecimal("90.00")),
        audit.tell(List.of("Applied 10% discount to " + orderId))),
    validateOrder);

var chargePayment = audit.flatMap(
    amount -> audit.flatMap(
        _ -> audit.of("receipt-456"),
        audit.tell(List.of("Charged " + amount))),
    applyDiscount);

// Run it
WriterT<IdKind.Witness, List<String>, String> result =
    WRITER_T.narrow(chargePayment);
Pair<String, List<String>> pair =
    IdKindHelper.ID.narrow(result.run()).value();

pair.first();   // → "receipt-456"
pair.second();  // → ["Validated order",
                //     "Applied 10% discount to order-123",
                //     "Charged 90.00"]
```

**Why this works:** Each `tell` appends entries. Each `flatMap` combines outputs via the `List` monoid (concatenation). The audit trail is complete, ordered, and immutable. No step can forget to pass the log forward -- `WriterT` handles it.
~~~

---

## Inspecting and Transforming Output

### `listen`: See What Was Written

`listen` runs a computation and returns the result paired with the output that computation produced:

```java
var computation = audit.flatMap(
    _ -> audit.of(42),
    audit.tell(List.of("computed value")));

var listened = audit.listen(computation);
// → Pair(Pair(42, ["computed value"]), ["computed value"])
//         ^^^^^^^^^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^^
//         result paired with output    output preserved
```

This is useful for conditional logic based on what was logged.

### `censor`: Redact Sensitive Output

`censor` applies a function to the output without seeing the result:

```java
var withSensitiveData = audit.flatMap(
    _ -> audit.of("done"),
    audit.tell(List.of("API key: sk_live_abc123")));

var redacted = audit.censor(
    entries -> entries.stream()
        .map(e -> e.contains("API key") ? "API key: [REDACTED]" : e)
        .toList(),
    withSensitiveData);
// Output: ["API key: [REDACTED]"]
```

---

## Relationship to Writer

`Writer<W, A>` is the non-transformer version: it pairs a value with accumulated output directly, with no outer monad. `WriterT<IdKind.Witness, W, A>` is equivalent to `Writer<W, A>` -- the identity monad adds no additional effect.

```
    Writer<W, A>              ≡  WriterT<Id, W, A>

    Writer<W, A>                 WriterT<F, W, A>
    ┌───────────────┐           ┌────────────────────────────┐
    │ Pair(A, W)    │           │ Kind<F, Pair(A, W)>        │
    │               │           │                            │
    │ No outer      │           │ Outer monad adds:          │
    │ effect        │           │   async / optional / error │
    └───────────────┘           └────────────────────────────┘
```

Use `Writer` when you need output accumulation in pure code. Use `WriterT` when you need output accumulation combined with another effect.

---

~~~admonish warning title="Common Mistakes"
- **Forgetting the Monoid:** `WriterT` requires a `Monoid<W>` at construction. If you pass `null`, you get a `NullPointerException`. If your monoid's `combine` is incorrect (not associative), law tests will fail.
- **Large accumulated output:** Unlike streaming, `WriterT` accumulates the entire output in memory. For high-volume logging, consider `VStream` with a logging side effect instead.
- **Using WriterT when you need state:** `WriterT` is *append-only*. You cannot read the accumulated output mid-computation (use `listen` to observe it). If you need to read and modify state, use `StateT`.
~~~

---

~~~admonish tip title="See Also"
- [Writer Monad](../monads/writer_monad.md) -- The non-transformer version for pure computations
- [Monad Transformers](transformers.md) -- General concept and choosing the right transformer
- [MTL Capabilities](mtl_capabilities.md) -- `MonadWriter` interface for stack-independent code
- [StateT](statet_transformer.md) -- When you need read-write state, not append-only output
- [ReaderT](readert_transformer.md) -- When you need read-only environment access
~~~

---

**Previous:** [StateT](statet_transformer.md)
**Next:** [MTL Capabilities](mtl_capabilities.md)
