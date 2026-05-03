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
- Using `For` comprehensions with `tell`, `listen`, `pass`, and `censor`
- The relationship between `Writer` and `WriterT<F, W, A>`
- When to use the [`WriterPath`](../effect/advanced_effects.md) Path type or the [`MonadWriter`](mtl_writer.md) capability instead of raw `WriterT`
~~~

~~~admonish example title="See Example Code"
[WriterTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer_t/WriterTExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`WriterPath<W, A>`](../effect/advanced_effects.md) is the better starting point when accumulating output is the only effect. When you need polymorphic, stack-independent code, the [`MonadWriter<F, W>`](mtl_writer.md) capability is usually a better fit than the concrete `WriterT`.

Reach for raw `WriterT` only when you need to combine accumulation with a specific outer monad that Path does not wrap, or when you are constructing your own MTL instance.
~~~

---

## The Problem: Logs That Vanish Into Outer Effects

Consider an async pipeline that needs an audit trail:

```java
CompletableFuture<Pair<BigDecimal, List<String>>> applyDiscount(
        BigDecimal price, List<String> logSoFar) {
    return CompletableFuture.supplyAsync(() -> {
        var discounted = price.multiply(new BigDecimal("0.9"));
        var newLog = new ArrayList<>(logSoFar);
        newLog.add("Applied 10% discount");
        return Pair.of(discounted, newLog);
    });
}

CompletableFuture<Pair<BigDecimal, List<String>>> addShipping(
        BigDecimal price, List<String> logSoFar) {
    return CompletableFuture.supplyAsync(() -> {
        var withShipping = price.add(new BigDecimal("5.00"));
        var newLog = new ArrayList<>(logSoFar);
        newLog.add("Added shipping");
        return Pair.of(withShipping, newLog);
    });
}
```

Every function must accept a log, copy it, append to it, and return it alongside the result. The log leaks into every signature. Composition requires manual log threading at every call site. Miss one handoff and entries disappear.

---

## The Solution

### With the Effect Path API (single effect)

If accumulation is the only effect, `WriterPath` is the simplest expression:

```java
WriterPath<List<String>, BigDecimal> workflow(BigDecimal price) {
    return WriterPath.<List<String>, BigDecimal>writer(
            price.multiply(new BigDecimal("0.9")),
            List.of("Applied 10% discount"),
            listMonoid)
        .via(discounted -> WriterPath.writer(
            discounted.add(new BigDecimal("5.00")),
            List.of("Added shipping"),
            listMonoid));
}
```

### With raw `WriterT` (combined effect)

When accumulation must combine with another monad (here `Id` for a pure example, but the same shape works over `CompletableFuture`):

```java
var idMonad     = IdMonad.instance();
var listMonoid  = Monoids.list();
var writerMonad = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, listMonoid);

var workflow = For.from(writerMonad, writerMonad.tell(List.of("Applied 10% discount")))
    .from(_ -> writerMonad.of(new BigDecimal("90.00")))
    .from(p -> writerMonad.tell(List.of("Added shipping")))
    .yield((_, price, _) -> price.add(new BigDecimal("5.00")));
```

One comprehension. The log combines via `List`'s `Monoid`. No manual threading.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Value</b>   ═══●═══════════════●═══════════════●═══▶  final price (in F)</span>
    <span style="color:#4CAF50">         applyDiscount   addShipping     yield</span>
    <span style="color:#4CAF50">         (flatMap)       (flatMap)</span>
              │                  │
              ▼ <i>tell</i>             ▼ <i>tell</i>
    <span style="color:#FFB300"><b>Log</b>     ──●──────────────●──────────────────────▶  ["Discount", "Shipping"]</span>
    <span style="color:#FFB300">       "Applied 10%"  "Added shipping"</span>
    <span style="color:#FFB300">                  combined via Monoid&lt;List&lt;String&gt;&gt;</span>
</pre>

The value track and the log track advance together. Each `tell` appends to the log without interrupting the computation; `flatMap` combines accumulated logs through the supplied `Monoid`. The final `Kind<F, Pair<A, W>>` carries both the result and the full audit trail.

---

## How WriterT Works

`WriterT<F, W, A>` wraps `Kind<F, Pair<A, W>>`. The outer monad `F` provides the computational context (async, optional, error-handling); the `Pair<A, W>` carries both the computed value and the accumulated output.

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

* **`F`**: The witness type of the **outer monad** (e.g. `IdKind.Witness`, `CompletableFutureKind.Witness`).
* **`W`**: The **output type** that accumulates. Must have a `Monoid<W>` instance.
* **`A`**: The type of the computed value.
* **`run()`**: returns the wrapped `Kind<F, Pair<A, W>>`.

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
Monoid<String> stringMonoid = new Monoid<>() {
    public String empty()                      { return ""; }
    public String combine(String a, String b)  { return a + b; }
};

var idMonad     = IdMonad.instance();
var writerMonad = new WriterTMonad<IdKind.Witness, String>(idMonad, stringMonoid);
```

~~~admonish note title="Working with Kind"
**Witness Type:** `WriterTKind<F, W, A>` extends `Kind<WriterTKind.Witness<F, W>, A>`. The outer monad `F` and output type `W` are fixed; `A` is the variable value type.

**KindHelper:** `WriterTKindHelper` provides `WRITER_T.widen` and `WRITER_T.narrow` for safe conversion. With `For` comprehensions you rarely need them; they appear at the boundaries when interoperating with raw `flatMap` chains.

```java
Kind<WriterTKind.Witness<F, W>, A> kind = WRITER_T.widen(writerT);
WriterT<F, W, A> concrete                = WRITER_T.narrow(kind);
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

Without a `Monoid`, `WriterT` cannot combine the output from `flatMap` chains. The `Monoid` is what makes the accumulation lawful: `combine(empty(), w) == w`, `combine(w, empty()) == w`, and `combine` is associative.

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `of(value)`         | Lifts a pure value with empty output as `F<Pair(value, empty)>` |
| `map(f, kind)`      | Transforms the value; output preserved unchanged |
| `flatMap(f, kind)`  | Sequences computations; combines outputs via the `Monoid` |
| `tell(w)`           | Appends `w` to the output, returns `Unit` |
| `listen(kind)`      | Runs the computation and returns the output alongside the result |
| `pass(kind)`        | Computation returns `Pair(a, f)`; applies `f` to transform the output |
| `listens(f, kind)`  | Like `listen` but maps `f` over the accumulated output in the pair |
| `censor(f, kind)`   | Modifies the accumulated output without seeing the result |

---

## Creating WriterT Instances

```java
var idMonad      = IdMonad.instance();
Monoid<String> stringMonoid = /* as above */;

// 1. Pure value with empty output
var pure = WriterT.of(idMonad, stringMonoid, 42);
// → Pair(42, "")

// 2. Record output with no meaningful value
var logged = WriterT.tell(idMonad, "initialised; ");
// → Pair(Unit.INSTANCE, "initialised; ")

// 3. Lift an outer-monad value with empty output
Kind<IdKind.Witness, Integer> idValue = IdKindHelper.ID.widen(new Id<>(42));
var lifted = WriterT.liftF(idMonad, stringMonoid, idValue);
// → Pair(42, "")

// 4. Explicit value and output
var explicit = WriterT.writer(idMonad, 42, "created; ");
// → Pair(42, "created; ")

// 5. From an existing Kind<F, Pair<A, W>>
var fromKind = WriterT.fromKind(idMonad.of(Pair.of(42, "restored; ")));
// → Pair(42, "restored; ")
```

---

## Real-World Example: Audit Trail

~~~admonish example title="Building an Audit Trail"

**The problem:** a multi-step order processing pipeline must record every decision for compliance. The log must travel with the computation, not sit in a mutable side channel.

**The solution:**

```java
var idMonad    = IdMonad.instance();
var listMonoid = Monoids.list();
var audit      = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, listMonoid);

var workflow = For.from(audit, audit.tell(List.of("Validated order")))
    .from(_ -> audit.of("order-123"))
    .from(orderId -> audit.tell(List.of("Applied 10% discount to " + orderId)))
    .from((_, _, _) -> audit.of(new BigDecimal("90.00")))
    .from(amount   -> audit.tell(List.of("Charged " + amount)))
    .yield((_, _, _, _, _) -> "receipt-456");

var concrete = WRITER_T.narrow(workflow);
var pair     = IdKindHelper.ID.narrow(concrete.run()).value();

pair.first();   // → "receipt-456"
pair.second();  // → ["Validated order",
                //     "Applied 10% discount to order-123",
                //     "Charged 90.00"]
```

**Why this works:** each `tell` appends entries. Each `from` step combines outputs via the `List` monoid. The audit trail is complete, ordered, and immutable. No step can forget to pass the log forward; `WriterT` handles it.
~~~

---

## Inspecting and Transforming Output

### `listen`: see what was written

`listen` runs a computation and returns the result paired with the output that computation produced:

```java
var computation = For.from(audit, audit.tell(List.of("computed value")))
    .yield(_ -> 42);

var listened = audit.listen(computation);
// → Pair(Pair(42, ["computed value"]), ["computed value"])
//         result paired with output    output preserved
```

This is useful for conditional logic based on what was logged.

### `censor`: redact sensitive output

`censor` applies a function to the output without seeing the result:

```java
var withSensitiveData = For.from(audit, audit.tell(List.of("API key: sk_live_abc123")))
    .yield(_ -> "done");

var redacted = audit.censor(
    entries -> entries.stream()
        .map(e -> e.contains("API key") ? "API key: [REDACTED]" : e)
        .toList(),
    withSensitiveData);
// Output: ["API key: [REDACTED]"]
```

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
var idWriter = WriterT.writer(idMonad, "result", List.of("step 1", "step 2"));

var optWriter = idWriter.mapT(idKind -> {
  Pair<String, List<String>> pair = ID.unwrap(idKind);
  return OPTIONAL.widen(Optional.of(pair));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* inside the `Pair` (the `A` in `Pair<A, W>`).
`mapT` transforms the *outer monad* wrapping the `Pair`, the `F` in `F<Pair<A, W>>`.
The accumulated output `W` is completely unaffected.
~~~

---

## Relationship to Writer

`Writer<W, A>` is the non-transformer version: it pairs a value with accumulated output directly, with no outer monad. `WriterT<IdKind.Witness, W, A>` is equivalent to `Writer<W, A>`, the identity monad adds no additional effect.

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
- **Large accumulated output:** unlike streaming, `WriterT` accumulates the entire output in memory. For high-volume logging, consider `VStream` with a logging side effect instead.
- **Using `WriterT` when you need state:** `WriterT` is *append-only*. You cannot read the accumulated output mid-computation (use `listen` to observe it). If you need to read and modify state, use [`StateT`](statet_transformer.md).
- **Reaching for the transformer when `WriterPath` would do:** if accumulation is your only effect, `WriterPath` is shorter and reads more naturally.
~~~

---

~~~admonish tip title="See Also"
- [WriterPath / Advanced Effects](../effect/advanced_effects.md) - The Path-API equivalent
- [MonadWriter](mtl_writer.md) - The MTL capability for stack-independent code
- [Stack Archetypes](archetypes.md) - The Audit Stack archetype maps to `WriterT`/`WriterPath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [Writer Monad](../monads/writer_monad.md) - The non-transformer version for pure computations
- [Monad Transformers](transformers.md) - General concept and choosing the right transformer
- [StateT](statet_transformer.md) - When you need read-write state, not append-only output
- [ReaderT](readert_transformer.md) - When you need read-only environment access
~~~

~~~admonish info title="Hands-On Learning"
The `MonadWriter` capability that wraps `WriterT` is exercised in [Tutorial 04: Polymorphic Capabilities (MTL)](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial04_PolymorphicCapabilities.java) (14 exercises, ~30-40 minutes).
~~~

---

**Previous:** [StateT](statet_transformer.md)
**Next:** [MTL Capabilities](mtl_capabilities.md)
