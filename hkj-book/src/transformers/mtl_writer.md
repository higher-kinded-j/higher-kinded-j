# MonadWriter: Output Accumulation

> *"What is written without effort is in general read without pleasure."*
>
> -- Samuel Johnson

Johnson was talking about prose, but the principle maps neatly onto computation logs. If accumulating output requires effort at every call site (threading a mutable list, remembering to append, praying nobody drops an entry) the resulting log will be incomplete and unreliable. `MonadWriter` removes that effort. You `tell`, and the output accumulates.

~~~admonish info title="What You'll Learn"
- How `MonadWriter` abstracts output accumulation independently of any transformer stack
- Using `tell`, `listen`, `pass`, `listens`, and `censor` in polymorphic code
- What the MonadWriter laws mean in plain Java terms
- The role of `Monoid` in controlling how output combines
~~~

---

## What MonadWriter Does

`MonadWriter<F, W>` gives your computation the ability to accumulate output of type `W` alongside its result. Each step can append to the output using `tell`, and the accumulated output travels invisibly through `flatMap` chains. You never see it, never thread it, never worry about dropping an entry.

The critical difference from [MonadState](mtl_state.md): writer output is **append-only**. You cannot read the accumulated output mid-computation (except via `listen`, which observes it without consuming it). You cannot overwrite or clear it. This restriction is what makes writer output reliable: once something is `tell`-ed, it stays.

```
    ┌──────────────────────────────────────────────────────────────┐
    │  MonadWriter<F, W>  (extends Monad<F>)                       │
    │                                                              │
    │  tell(w)           →  Kind<F, Unit>                          │
    │                       "Append w to the accumulated output"   │
    │                                                              │
    │  listen(ma)        →  Kind<F, Pair<A, W>>                    │
    │                       "Run ma, return its result paired      │
    │                        with the output it produced"          │
    │                                                              │
    │  pass(ma)          →  Kind<F, A>                             │
    │                       "Run ma (which returns (a, f)),        │
    │                        apply f to transform the output"      │
    │                                                              │
    │  listens(f, ma)    →  Kind<F, Pair<A, B>>                    │
    │                       "Like listen, but map f over output"   │
    │                                                              │
    │  censor(f, ma)     →  Kind<F, A>                             │
    │                       "Run ma, transform its output with f"  │
    └──────────────────────────────────────────────────────────────┘
```

### `tell`: Append to the Output

`tell(w)` is the fundamental operation. It appends `w` to the accumulated output and returns `Unit` (the functional `void`). Successive `tell` calls combine their output via a `Monoid`:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, String>
    processOrder(MonadWriter<F, List<String>> audit, String orderId) {
  return For.from(audit, audit.tell(List.of("Validated order " + orderId)))
      .from(_ -> audit.tell(List.of("Charged payment")))
      .yield((_, _) -> "receipt-" + orderId);
}
// Output: ["Validated order order-123", "Charged payment"]
// Result: "receipt-order-123"
```

Each `from` step appends to the accumulated output. The `yield` produces the final result without adding any output of its own.

```
    Output accumulation through a For comprehension:

    []                                   ← initial (Monoid.empty)
     │
     ├──▶ tell(["Validated order"])
     │    ["Validated order"]            ← after step 1
     │
     ├──▶ tell(["Charged payment"])
     │    ["Validated order",            ← combine via List monoid
     │     "Charged payment"]
     │
     └──▶ of("receipt-123")
          ["Validated order",            ← output unchanged (of adds empty)
           "Charged payment"]
```

### The Monoid Requirement

The output type `W` must have a `Monoid<W>` instance. The monoid controls two things:

1. **`empty()`**: the starting output when a computation produces nothing (used by `of`)
2. **`combine(w1, w2)`**: how to merge output from two consecutive steps (used by `flatMap`)

| Monoid | `empty()` | `combine(a, b)` | Use Case |
|--------|-----------|------------------|----------|
| `List<T>` | `[]` | concatenation | Structured audit entries |
| `String` | `""` | `a + b` | Simple text logs |
| `Integer` (sum) | `0` | `a + b` | Counting operations |
| `Set<T>` | `{}` | union | Collecting unique tags |

Without a `Monoid`, there would be no way to combine outputs from successive steps. The `Monoid` is what makes accumulation lawful and automatic.

### `listen`: Observe the Output

`listen(ma)` runs a computation and returns a `Pair` containing the result and the output that computation produced. The output is still accumulated normally; `listen` simply lets you peek at it:

```java
var computation = For.from(audit, audit.tell(List.of("computed value")))
    .yield(_ -> 42);

var listened = audit.listen(computation);
// Result: Pair(Pair(42, ["computed value"]), ["computed value"])
//               ^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^
//               result paired with output    output also preserved normally
```

This is useful when you need to make decisions based on what was logged. For example, checking whether any warnings were recorded before proceeding.

### `censor`: Redact or Transform Output

`censor(f, ma)` runs `ma` and applies function `f` to its accumulated output. The result is unchanged; only the output is transformed:

```java
var withSensitiveData = For.from(audit, audit.tell(List.of("API key: sk_live_abc123")))
    .yield(_ -> "done");

var redacted = audit.censor(
    entries -> entries.stream()
        .map(e -> e.contains("API key") ? "API key: [REDACTED]" : e)
        .toList(),
    withSensitiveData);
// Output: ["API key: [REDACTED]"]
// Result: "done"
```

This is particularly useful for sanitising logs before they leave a security boundary.

### `pass`: Output Transformation From Inside

`pass(ma)` is the most powerful (and least commonly needed) operation. The computation `ma` returns a `Pair<A, Function<W, W>>`: a result and a function to apply to its own output. `pass` extracts the function and applies it:

```java
var computation = For.from(audit, audit.tell(List.of("hello")))
    .yield(_ -> Pair.of(42, (Function<List<String>, List<String>>)
        entries -> entries.stream().map(String::toUpperCase).toList()));

var result = audit.pass(computation);
// Output: ["HELLO"]  (the function uppercased the output)
// Result: 42
```

`censor` is usually clearer when you know the transformation in advance. `pass` is for cases where the computation itself decides how to transform its output.

### `listens`: Listen With a Projection

`listens(f, ma)` is like `listen`, but applies a function to the output in the returned pair. The accumulated output is still preserved unchanged:

```java
var computation = For.from(audit, audit.tell(List.of("step1", "step2")))
    .yield(_ -> 42);

var result = audit.listens(List::size, computation);
// Result: Pair(Pair(42, 2), ["step1", "step2"])
//                       ^   output count, not the entries themselves
```

---

## The MonadWriter Laws

Every `MonadWriter` implementation must satisfy three laws. These guarantee that output accumulation is predictable and that `tell` behaves like appending to a log.

### Law 1: Tell-Empty

```
    tell(empty)  ≡  of(Unit)
```

**In Java:** Telling the monoid's empty value is the same as doing nothing. An empty log entry contributes nothing.

```java
// These produce the same result and output:
audit.tell(List.of())       // tell with empty list
audit.of(Unit.INSTANCE)     // pure Unit, no output
```

This guarantees that the monoid's `empty()` is truly neutral. If telling an empty list somehow added something to the output, compositions would break.

### Law 2: Tell-Combine

```
    tell(a) >> tell(b)  ≡  tell(combine(a, b))
```

**In Java:** Telling two values in sequence is the same as telling their combined value once.

```java
// These produce the same output:
For.from(audit, audit.tell(List.of("a")))
    .from(_ -> audit.tell(List.of("b")))
    .yield((_, _) -> Unit.INSTANCE)

audit.tell(List.of("a", "b"))
```

This guarantees that output accumulation respects the `Monoid`. Two sequential tells produce the same result as a single tell with the combined output. This is what makes it safe to refactor multiple tells into one (or split one into many).

### Law 3: Listen-Tell

```
    listen(tell(w))  ≡  tell(w) >> of(Pair(Unit, w))
```

**In Java:** Listening to a `tell` gives you a pair of the `tell`'s result (`Unit`) and the output that was told (`w`).

```java
// These produce the same result:
audit.listen(audit.tell(List.of("entry")))
// → result: Pair(Unit, ["entry"]), output: ["entry"]
```

This guarantees that `listen` faithfully reports what was accumulated. It does not add, remove, or transform anything.

---

## The Concrete Instance: WriterTMonad

`WriterTMonad<F, W>` is the standard implementation of `MonadWriter` for the `WriterT` transformer. Unlike `ReaderTMonadReader` and `StateTMonadState` (which extend separate classes), `WriterTMonad` implements `MonadWriter` directly:

```java
WriterTMonad<IdKind.Witness, List<String>> writerInstance =
    new WriterTMonad<>(idMonad, listMonoid);

// Now use it as a MonadWriter:
Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, Unit> logged =
    writerInstance.tell(List.of("application started"));
```

---

~~~admonish warning title="Common Mistakes"
- **Large accumulated output:** `MonadWriter` accumulates the entire output in memory. For high-volume logging (thousands of entries per second), consider a streaming approach with `VStream` instead.
- **Trying to read output mid-computation:** Unlike `MonadState`, you cannot inspect the accumulated output during a computation. Use `listen` to observe the output of a sub-computation, but be aware that `listen` returns the output as a value; it does not give you a running total.
- **Forgetting Monoid associativity:** If your `Monoid`'s `combine` is not associative, the order of accumulation in nested `flatMap` chains may produce unexpected results. Always verify that `combine(combine(a, b), c) == combine(a, combine(b, c))`.
~~~

---

~~~admonish tip title="See Also"
- [WriterT](writert_transformer.md) -- The concrete transformer behind `MonadWriter`
- [MonadReader](mtl_reader.md) -- Read-only environment access
- [MonadState](mtl_state.md) -- Read-write state threading
- [Writer Monad](../monads/writer_monad.md) -- The non-transformer version
- [Semigroup and Monoid](../functional/semigroup_and_monoid.md) -- How output combination works
~~~

---

**Previous:** [MonadState](mtl_state.md)
**Next:** [Combining Capabilities](mtl_combining.md)
