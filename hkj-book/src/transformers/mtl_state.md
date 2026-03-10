# MonadState: Mutable State

> *"All that is solid melts into air."*
>
> -- Karl Marx, *The Communist Manifesto*

Marx was describing political transformation, but the phrase captures exactly what `MonadState` does to mutable variables: it dissolves them into a purely functional flow. The state is still there (read, updated, replaced) but it threads through your computation invisibly, with no `var`, no `synchronized`, no shared mutable field.

~~~admonish info title="What You'll Learn"
- How `MonadState` abstracts stateful computation independently of any transformer stack
- Using `get`, `put`, `modify`, `gets`, and `inspect` in polymorphic code
- What the MonadState laws mean in plain Java terms
- Building stateful workflows that remain testable and composable
~~~

---

## What MonadState Does

`MonadState<F, S>` gives your computation a piece of mutable state of type `S`. You can read the current state, replace it entirely, or transform it with a function. The state threads automatically through `flatMap` chains, so each step sees the state left by the previous step.

This is different from `MonadReader` in a crucial way: the value *changes* as the computation progresses. Reader is a notice board that everyone reads; State is a whiteboard that each step can erase and rewrite.

```
    ┌──────────────────────────────────────────────────────────────┐
    │  MonadState<F, S>  (extends Monad<F>)                        │
    │                                                              │
    │  get()          →  Kind<F, S>                                │
    │                    "What is the current state?"              │
    │                                                              │
    │  put(s)         →  Kind<F, Unit>                             │
    │                    "Replace the state with s"                │
    │                                                              │
    │  modify(f)      →  Kind<F, Unit>     where f : S → S         │
    │                    "Transform the current state with f"      │
    │                                                              │
    │  gets(f)        →  Kind<F, A>        where f : S → A         │
    │                    "Read the state, then extract a value"    │
    │                                                              │
    │  inspect(f)     →  Kind<F, A>        (alias for gets)        │
    └──────────────────────────────────────────────────────────────┘
```

### `get` and `put`: The Primitive Pair

`get()` returns the current state as a value inside the monad. `put(s)` replaces the state entirely and returns `Unit` (the functional equivalent of `void`). Every other operation is built on top of these two.

```java
record Counter(int count, int total) {}

// Read the current counter
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Counter>
    currentCounter(MonadState<F, Counter> state) {
  return state.get();
}

// Reset the counter to zero
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit>
    resetCounter(MonadState<F, Counter> state) {
  return state.put(new Counter(0, 0));
}
```

### `modify`: Transform Without Reading

`modify(f)` applies a function to the current state and stores the result. It is equivalent to reading the state, applying `f`, and writing it back, but expressed as a single operation:

```java
// Increment the count by 1
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Unit>
    incrementCount(MonadState<F, Counter> state) {
  return state.modify(c -> new Counter(c.count() + 1, c.total()));
}
```

This is the workhorse operation. Most stateful steps are "read the state, compute a new state, store it". `modify` captures that pattern directly.

### `gets` / `inspect`: Read and Extract

`gets(f)` reads the current state and applies a function to extract a value from it. This is equivalent to `map(f, get())` but reads more clearly:

```java
// Extract just the total from the counter
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer>
    currentTotal(MonadState<F, Counter> state) {
  return state.gets(Counter::total);
}
```

`inspect(f)` is an alias for `gets(f)`.

---

## How State Threads Through a For Comprehension

The key insight of `MonadState` is that each step in a chain sees the state left by the previous step. You do not pass the state explicitly; the `For` comprehension handles the threading:

```
    State flow through a For comprehension:

    Counter(0, 0)                        ← initial state
         │
         ├──▶ modify(c -> (c.count+1, c.total+10))
         │    Counter(1, 10)             ← state after step 1
         │
         ├──▶ modify(c -> (c.count+1, c.total+20))
         │    Counter(2, 30)             ← state after step 2
         │
         ├──▶ gets(Counter::total)
         │    returns 30                 ← reads state from step 2
         │    Counter(2, 30)             ← state unchanged (gets is read-only)
         │
         └──▶ put(new Counter(0, 0))
              Counter(0, 0)              ← state replaced entirely
```

In Java, this chain looks like:

```java
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer>
    addTwoValues(MonadState<F, Counter> state) {
  return For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
      .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 20)))
      .from(_ -> state.gets(Counter::total))
      .yield((_, _, total) -> total);
}
// With initial Counter(0, 0), returns 30 with final state Counter(2, 30)
```

Each `from` step is a monadic operation whose effect (modifying state, reading state) is sequenced automatically. The `yield` at the end maps over the accumulated values to produce the final result.

---

## A Complete Example

Here is a polymorphic function that models a simple shopping cart:

```java
record Cart(List<String> items, BigDecimal total) {
  Cart addItem(String item, BigDecimal price) {
    var newItems = new ArrayList<>(items);
    newItems.add(item);
    return new Cart(List.copyOf(newItems), total.add(price));
  }
}

<F extends WitnessArity<TypeArity.Unary>> Kind<F, BigDecimal>
    checkout(MonadState<F, Cart> state) {
  return For.from(state, state.modify(c -> c.addItem("Gadget", new BigDecimal("49.99"))))
      .from(_ -> state.modify(c -> c.addItem("Widget", new BigDecimal("29.99"))))
      .from(_ -> state.modify(c -> c.addItem("Shipping", new BigDecimal("5.00"))))
      .from(_ -> state.gets(Cart::total))
      .yield((_, _, _, total) -> total);
}

// Use with StateT over Id (for testing)
StateTMonadState<Cart, IdKind.Witness> cartState =
    new StateTMonadState<>(idMonad);
Kind<StateTKind.Witness<Cart, IdKind.Witness>, BigDecimal> result =
    checkout(cartState);
```

---

## The MonadState Laws

Every `MonadState` implementation must satisfy four laws. These guarantee that state behaves the way a mutable variable would: reads reflect the most recent write, redundant writes collapse, and `modify` is just a read-then-write.

### Law 1: Get-Put

```
    get >>= put  ≡  of(Unit)
```

**In Java:** Reading the current state and immediately writing it back is the same as doing nothing.

```java
// These produce the same result and final state:
For.from(state, state.get())
    .from(s -> state.put(s))
    .yield((_, unit) -> unit)

state.of(Unit.INSTANCE)
```

This guarantees that `get` returns the true current state. If `get` returned a stale copy, writing it back could overwrite changes. The law says that cannot happen: reading and writing back is a no-op.

### Law 2: Put-Get

```
    put(s) >> get  ≡  put(s) >> of(s)
```

**In Java:** Writing a value and then reading the state back gives you the value you just wrote.

```java
// These produce the same result:
For.from(state, state.put(newCounter))
    .from(_ -> state.get())
    .yield((_, s) -> s)

For.from(state, state.put(newCounter))
    .yield(_ -> newCounter)
```

This is the complement of get-put. Together they say: `put` stores exactly what you give it, and `get` returns exactly what was stored. No surprises, no transformations, no lost updates.

### Law 3: Put-Put

```
    put(s1) >> put(s2)  ≡  put(s2)
```

**In Java:** Writing a value and immediately overwriting it is the same as just writing the second value.

```java
// These produce the same final state:
For.from(state, state.put(counter1))
    .from(_ -> state.put(counter2))
    .yield((_, unit) -> unit)

state.put(counter2)
```

This guarantees that `put` is a complete replacement. There is no "merge" or "accumulate" semantics; the old state is simply discarded. (If you want accumulation, use [MonadWriter](mtl_writer.md).)

### Law 4: Modify Coherence

```
    modify(f)  ≡  get >>= (s -> put(f.apply(s)))
```

**In Java:** `modify(f)` is exactly equivalent to reading the state, applying `f`, and writing the result back.

```java
// These produce the same result and final state:
state.modify(c -> new Counter(c.count() + 1, c.total()))

For.from(state, state.get())
    .from(c -> state.put(new Counter(c.count() + 1, c.total())))
    .yield((_, unit) -> unit)
```

This means `modify` is not a separate primitive; it is a convenience that composes `get` and `put`. You can always replace a `modify` call with the explicit get-then-put pattern and get identical behaviour.

---

## The Concrete Instance: StateTMonadState

`StateTMonadState<S, F>` is the standard implementation of `MonadState` for the `StateT` transformer. It extends `StateTMonad<S, F>` and adds the state-access operations:

```java
StateTMonadState<Counter, IdKind.Witness> stateInstance =
    new StateTMonadState<>(idMonad);

// Now use it as a MonadState:
Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> total =
    stateInstance.gets(Counter::total);
```

---

~~~admonish warning title="Common Mistakes"
- **Forgetting that `put` replaces the entire state:** `put` does not merge fields. If your state is a record with five fields and you `put` a new record with only one field changed, you must copy the other four. Use `modify` with a function that transforms specific fields instead.
- **Using MonadState when you need append-only output:** If you only add to the state and never read intermediate values, [MonadWriter](mtl_writer.md) is a better fit. Writer provides `tell` (append) and guarantees the output accumulates via a `Monoid`.
- **Confusing state threading with concurrency:** `MonadState` threads state through a sequential `flatMap` chain. It does not provide thread-safe concurrent access. For concurrent state, use `AtomicReference` or other concurrency primitives.
~~~

---

~~~admonish tip title="See Also"
- [StateT](statet_transformer.md) -- The concrete transformer behind `MonadState`
- [MonadReader](mtl_reader.md) -- When you need read-only environment access
- [MonadWriter](mtl_writer.md) -- When you need append-only output
- [State Monad](../monads/state_monad.md) -- The non-transformer version
~~~

---

**Previous:** [MonadReader](mtl_reader.md)
**Next:** [MonadWriter](mtl_writer.md)
