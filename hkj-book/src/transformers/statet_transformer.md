# The StateT Transformer:
## _Managing State Across Effect Boundaries_

> *"You could not step twice into the same river."*
>
> -- Heraclitus

State that changes between steps is exactly the river Heraclitus described. `StateT` lets each step see the river it actually faces while keeping the same composable surface.

~~~admonish info title="What You'll Learn"
- How to add stateful computation to any existing monad
- Building stack operations that can fail (`StateT` with `Optional`)
- Understanding the relationship between `State` and `StateT<S, Identity, A>`
- Using `For` comprehensions with `get`, `put`, `modify` to keep witness types localised
- When to use the [`WithStatePath`](../effect/advanced_effects.md) Path type or the [`MonadState`](mtl_state.md) capability instead of raw `StateT`
~~~

~~~admonish example title="See Example Code"
- [StateTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTExample.java)
- [StateTStackExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTStackExample.java)
~~~

~~~admonish note title="Path First, Stack Later"
For most use cases, [`WithStatePath<S, A>`](../effect/advanced_effects.md) is the better starting point when state is the only effect. When you need polymorphic, stack-independent code, the [`MonadState<F, S>`](mtl_state.md) capability is usually a better fit than the concrete `StateT`.

Reach for raw `StateT` only when you need to combine state with a specific outer monad that Path does not wrap, or when you are constructing your own MTL instance.
~~~

---

## The Problem: Stateful Operations that Can Fail

Imagine a stack data structure where `pop` might fail on an empty stack. Without `StateT`, you end up managing both the state transitions and the optionality by hand:

```java
Optional<StateTuple<List<Integer>, Integer>> pop(List<Integer> stack) {
    if (stack.isEmpty()) return Optional.empty();
    var newStack = new LinkedList<>(stack);
    Integer value = newStack.remove(0);
    return Optional.of(StateTuple.of(newStack, value));
}

Optional<StateTuple<List<Integer>, Integer>> workflow(List<Integer> initial) {
    var afterPush1 = push(initial, 10);
    var afterPush2 = push(afterPush1.state(), 20);
    var pop1Result = pop(afterPush2.state());
    if (pop1Result.isEmpty()) return Optional.empty();
    var pop2Result = pop(pop1Result.get().state());
    if (pop2Result.isEmpty()) return Optional.empty();
    int sum = pop1Result.get().value() + pop2Result.get().value();
    return Optional.of(StateTuple.of(pop2Result.get().state(), sum));
}
```

Each operation returns both a new state and a value; the optionality adds another layer of checking. The state threading is manual and error-prone. Miss one `.get().state()` call and you use stale state.

---

## The Solution

### With the Effect Path API (single effect)

If state is the only effect, `WithStatePath` is the simplest expression:

```java
WithStatePath<List<Integer>, Integer> workflow() {
    return WithStatePath.<List<Integer>>modify(s -> prepend(s, 10))
        .then(() -> WithStatePath.modify(s -> prepend(s, 20)))
        .then(() -> WithStatePath.<List<Integer>>get())
        .map(state -> state.get(0) + state.get(1));
}
```

### With raw `StateT` (combined effect)

When state must combine with another effect (here `Optional`):

```java
var optMonad    = Instances.monadError(optional());
var stateTMonad = Instances.stateT(optMonad);

var computation = For.from(stateTMonad, push(10))
    .from(_ -> push(20))
    .from(_ -> pop())
    .from(_ -> pop())
    .yield((a, b, p1, p2) -> p1 + p2);

var result = OPTIONAL.narrow(StateTKindHelper.runStateT(computation, Collections.emptyList()));
// → Optional.of(StateTuple([], 30))
```

The state flows from one operation to the next through `flatMap`. If any operation returns `Optional.empty()` (e.g. popping an empty stack), the rest are skipped. No manual state passing, no null checks.

---

## The Railway View

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Value</b>   ═══●═══════════●═══════════●═══════════●═══▶  result A (in F)</span>
    <span style="color:#4CAF50">           push(10)    push(20)    pop         pop</span>
    <span style="color:#4CAF50">           (flatMap)   (flatMap)   (flatMap)   (flatMap)</span>
               │           │           │           │
               ▼           ▼           ▼           ▼
    <span style="color:#2196F3"><b>State</b>   ═══●═══════════●═══════════●═══════════●═══▶  final state S</span>
    <span style="color:#2196F3">           [10]        [20,10]     [10]        []</span>
</pre>

Both tracks advance in lockstep: each `flatMap` produces a new `(value, state)` pair. Calling `runStateT(initialState)` at the boundary kicks the whole computation off and yields the final state alongside the result. If the outer monad `F` short-circuits (here `Optional.empty()` on an empty pop), subsequent steps are skipped and both tracks freeze.

---

## How StateT Works

`StateT<S, F, A>` represents a computation that takes an initial state `S`, produces a result `A` and a new state `S`, all within the context of a monad `F`.

```
    ┌──────────────────────────────────────────────────────────┐
    │  StateT<List<Integer>, OptionalKind.Witness, A>          │
    │                                                          │
    │    State S ─────▶ ┌────────────────────────┐             │
    │    (initial)      │  Function:             │             │
    │                   │  S → Kind<F, (S, A)>   │             │
    │                   └────────────┬───────────┘             │
    │                                │                         │
    │                                ▼                         │
    │                   ┌─── Optional ──────────┐              │
    │                   │                       │              │
    │                   │  empty()  │  of(S, A) │              │
    │                   │           │           │              │
    │                   └───────────────────────┘              │
    │                                                          │
    │  flatMap ──▶ threads updated state to next operation     │
    │  map ──────▶ transforms value, state unchanged           │
    │  runStateT ──▶ provides initial state, returns F<(S,A)>  │
    │  evalStateT ──▶ returns F<A> (discards final state)      │
    │  execStateT ──▶ returns F<S> (discards value)            │
    └──────────────────────────────────────────────────────────┘
```

* **`S`**: The type of the state.
* **`F`**: The witness type for the underlying monad (e.g. `OptionalKind.Witness`, `IOKind.Witness`).
* **`A`**: The type of the computed value.
* **`StateTuple<S, A>`**: A container holding the pair `(state, value)`.

The fundamental structure is a function `S -> F<StateTuple<S, A>>`:

```java
StateT<Integer, OptionalKind.Witness, String> computation = StateT.create(
    currentState -> currentState < 0
        ? OPTIONAL.widen(Optional.empty())
        : OPTIONAL.widen(Optional.of(StateTuple.of(currentState + 1, "Value: " + currentState))),
    optionalMonad);
```

---

## Setting Up StateTMonad

The `StateTMonad<S, F>` class implements `Monad<StateTKind.Witness<S, F>>`. It requires a `Monad<F>` instance for the underlying monad:

```java
var optionalMonad = Instances.monadError(optional());
var stateTMonad   = Instances.stateT(optionalMonad);
```

~~~admonish note title="Working with Kind"
- **`StateT<S, F, A>`**: the primary data type holding `S -> Kind<F, StateTuple<S, A>>`.
- **`StateTKind<S, F, A>`**: the `Kind` representation for generic monadic usage.
- **`StateTKind.Witness<S, F>`**: the higher-kinded type witness. Both `S` and `F` are part of the witness.
- **`StateTMonad<S, F>`**: the `Monad` instance, providing `of`, `map`, `flatMap`, `ap`.
- **`StateTKindHelper`**: utility for `narrow`, `runStateT`, `evalStateT`, `execStateT`.
- **`StateTuple<S, A>`**: a record holding `(S state, A value)`.
~~~

---

## Running StateT Computations

```java
// Run: returns F<StateTuple<S, A>>
var result    = StateTKindHelper.runStateT(computation, 10);
// → Optional.of(StateTuple(11, "Value: 10"))

// Eval: returns F<A> (discards state)
var valueOnly = StateTKindHelper.evalStateT(computation, 10);
// → Optional.of("Value: 10")

// Exec: returns F<S> (discards value)
var stateOnly = StateTKindHelper.execStateT(computation, 10);
// → Optional.of(11)
```

---

## Key Operations

| Operation | Behaviour |
|-----------|-----------|
| `stateTMonad.of(value)`        | Wraps a pure value, leaving state unchanged |
| `stateTMonad.map(f, kind)`     | Transforms the value; state passes through |
| `stateTMonad.flatMap(f, kind)` | Sequences operations, threading the updated state |

The `MonadState` capability adds `get()`, `put(s)`, `modify(f)`, `gets(f)`, and `inspect(f)` on top.

---

## Composing StateT Actions

Like any monad, `StateT` computations compose with `map` and `flatMap`. Most pages in this chapter show this through `For` comprehensions; the explicit forms are equivalent:

~~~admonish example title="map: transforming the value"
```java
var initial = StateT.<Integer, OptionalKind.Witness, Integer>create(
    s -> OPTIONAL.widen(Optional.of(StateTuple.of(s + 1, s * 2))),
    optionalMonad);

var mapped = stateTMonad.map(val -> "Computed: " + val, initial);

// Run with state 5: initial → state=6, value=10; map → "Computed: 10"
// → Optional.of(StateTuple(6, "Computed: 10"))
```
~~~

~~~admonish example title="flatMap: sequencing state operations"
```java
var firstStep = StateT.<Integer, OptionalKind.Witness, Integer>create(
    s -> OPTIONAL.widen(Optional.of(StateTuple.of(s + 1, s * 10))),
    optionalMonad);

Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, String>> secondStepFn =
    prevValue -> StateT.create(
        s -> prevValue > 100
            ? OPTIONAL.widen(Optional.of(StateTuple.of(s + prevValue, "Large: " + prevValue)))
            : OPTIONAL.widen(Optional.empty()),
        optionalMonad);

var combined = stateTMonad.flatMap(secondStepFn, firstStep);
// state 15: firstStep → (16, 150), secondStep(150) → (166, "Large: 150")
// state 5:  firstStep → (6, 50),  secondStep(50)  → empty
```
~~~

---

## State-Specific Operations

Common state operations can be constructed using `StateT.create`:

```java
// get: retrieve the current state as the value
static <S, F> Kind<StateTKind.Witness<S, F>, S> get(Monad<F> monadF) {
    return StateT.create(s -> monadF.of(StateTuple.of(s, s)), monadF);
}

// set: replace the state, return Unit
static <S, F> Kind<StateTKind.Witness<S, F>, Unit> set(S newState, Monad<F> monadF) {
    return StateT.create(s -> monadF.of(StateTuple.of(newState, Unit.INSTANCE)), monadF);
}

// modify: update the state with a function, return Unit
static <S, F> Kind<StateTKind.Witness<S, F>, Unit> modify(Function<S, S> f, Monad<F> monadF) {
    return StateT.create(s -> monadF.of(StateTuple.of(f.apply(s), Unit.INSTANCE)), monadF);
}

// gets: extract a value derived from the state
static <S, F, A> Kind<StateTKind.Witness<S, F>, A> gets(Function<S, A> f, Monad<F> monadF) {
    return StateT.create(s -> monadF.of(StateTuple.of(s, f.apply(s))), monadF);
}
```

---

## Real-World Example: Stack with Failure

~~~admonish example title="Stack Operations with Optional Failure"

- [StateTStackExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTStackExample.java)

**The problem:** stack push/pop operations where popping an empty stack produces an absence rather than an exception. Compose them cleanly.

**The solution:**

```java
private static final MonadError<OptionalKind.Witness, Unit> OPT_MONAD = Instances.monadError(optional());
private static final StateTMonad<List<Integer>, OptionalKind.Witness> ST_OPT_MONAD =
    Instances.stateT(OPT_MONAD);

static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Unit> push(Integer value) {
  return StateTKindHelper.stateT(stack -> {
      var newStack = new LinkedList<>(stack);
      newStack.add(0, value);
      return OPTIONAL.widen(Optional.of(StateTuple.of(newStack, Unit.INSTANCE)));
  }, OPT_MONAD);
}

static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> pop() {
  return StateTKindHelper.stateT(stack -> {
      if (stack.isEmpty()) return OPTIONAL.widen(Optional.empty());
      var newStack = new LinkedList<>(stack);
      Integer popped = newStack.remove(0);
      return OPTIONAL.widen(Optional.of(StateTuple.of(newStack, popped)));
  }, OPT_MONAD);
}

// Compose with For:
var computation = For.from(ST_OPT_MONAD, push(10))
    .from(_ -> push(20))
    .from(_ -> pop())
    .from(_ -> pop())
    .yield((a, b, p1, p2) -> p1 + p2);

var result = OPTIONAL.narrow(StateTKindHelper.runStateT(computation, Collections.emptyList()));
// → Optional.of(StateTuple([], 30))

var emptyPop = OPTIONAL.narrow(StateTKindHelper.runStateT(pop(), Collections.emptyList()));
// → Optional.empty()
```

**Why this works:** the `For` comprehension sequences state operations through `flatMap`. Each `push` returns the updated stack as new state; each `pop` either returns the popped value with an updated stack or `Optional.empty()`, which short-circuits the rest. The state threading is completely automatic.
~~~

---

## Transforming the Outer Monad with `mapT`

Sometimes you need to change the *outer monad* of a `StateT` without touching the state-threading logic. Perhaps you want to switch from `Optional` to `Id` (guaranteeing a result with a default), or apply a natural transformation to move between effect types.

Because `StateT` stores its `Monad<F>` instance internally, switching from `F` to `G` requires supplying a new `Monad<G>`. This is the one transformer where `mapT` takes an extra parameter:

```
  state ──> runStateTFn() ──> Kind<F, StateTuple<S, A>> ──> f ──> Kind<G, StateTuple<S, A>>
    │                                                                        │
    └──── combined into new StateT<S, G, A> with monadG ────────────────────┘
```

```java
StateT<Integer, OptionalKind.Witness, String> optStateT = ...;
var idMonad = Instances.monad(id());

var idStateT = optStateT.mapT(idMonad, optKind -> {
  Optional<StateTuple<Integer, String>> opt = OPTIONAL.narrow(optKind);
  return ID.widen(Id.of(opt.orElse(StateTuple.of(0, "default"))));
});
```

~~~admonish note title="mapT vs map"
`map` transforms the *value* produced by the state computation (the `A` in `StateTuple<S, A>`).
`mapT` transforms the *outer monad* wrapping each state transition, the `F` in `S -> F<StateTuple<S, A>>`.
The state-threading is completely unaffected.
~~~

~~~admonish warning title="StateT requires a new Monad instance"
Unlike the other five transformers, `StateT.mapT` takes `Monad<G> monadG` as its first parameter. This is because `StateT` stores the monad instance for internal sequencing; when you switch monads, the new `StateT` needs the new monad to continue operating correctly.
~~~

---

## Relationship to State Monad

The [State Monad](../monads/state_monad.md) (`State<S, A>`) is a specialised case of `StateT`. Specifically, `State<S, A>` is equivalent to `StateT<S, IdKind.Witness, A>`, where `Id` is the Identity monad (a monad that adds no effects).

If your stateful computation does not need to combine with another effect, use `State<S, A>` directly (or `WithStatePath<S, A>`). Reach for `StateT` when you need state *and* another effect (optionality, error handling, async).

---

~~~admonish warning title="Common Mistakes"
- **Using stale state:** in manual state threading, it is easy to accidentally use the state from step 1 in step 3. `StateT.flatMap` eliminates this by threading updated state automatically.
- **Null in `ap`:** the `ap` method requires the function it extracts from the first `StateT` computation to be non-null. A `null` function will cause a `NullPointerException`.
- **Confusing `StateT` with `ReaderT`:** if your "state" never changes, you probably want [`ReaderT`](readert_transformer.md). Use `StateT` only when operations need to *modify* the state.
- **Reaching for the transformer when `WithStatePath` would do:** if state is your only effect, `WithStatePath` is shorter and reads more naturally.
~~~

---

~~~admonish tip title="See Also"
- [WithStatePath / Advanced Effects](../effect/advanced_effects.md) - The Path-API equivalent
- [MonadState](mtl_state.md) - The MTL capability for stack-independent code
- [Stack Archetypes](archetypes.md) - The Workflow Stack archetype maps to `StateT`/`WithStatePath`
- [Migration Cookbook](migration_cookbook.md) - Side-by-side translations
- [State Monad](../monads/state_monad.md) - Understand the basics of stateful computations
- [Monad Transformers](transformers.md) - General concept of monad transformers
- [ReaderT](readert_transformer.md) - When you need read-only environment, not mutable state
- [Draughts Example ("checkers")](../examples/examples_draughts.md) - See the HKJ State Monad used in a game
~~~

~~~admonish info title="Hands-On Learning"
The `MonadState` capability that wraps `StateT` is exercised in [Tutorial 04: Polymorphic Capabilities (MTL)](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial04_PolymorphicCapabilities.java) (14 exercises, ~30-40 minutes).
~~~

---

**Previous:** [ReaderT](readert_transformer.md)
**Next:** [WriterT](writert_transformer.md)
