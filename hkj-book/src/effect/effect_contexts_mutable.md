# MutableContext: Stateful Computation Made Pure

> *"Its the same thing every time. The stoan you find aint the stoan you thot youd be looking for."*
>
> -- Russell Hoban, *Riddley Walker*

State transforms as you work with it. The counter you started with isn't the counter you end with. The accumulator grows. The traversal position shifts. `MutableContext` lets you write code that *feels* like mutation (get the current value, update it, continue) while remaining purely functional underneath. The state you find at the end isn't the state you started with, but the transformation is explicit and controlled.

~~~admonish info title="What You'll Learn"
- Threading state through effectful computations
- Reading state with `get()`, writing with `put()`, updating with `modify()`
- Chaining stateful operations that accumulate changes
- Running computations to get the value, the state, or both
~~~

---

## The Problem

Consider tracking statistics through a processing pipeline:

```java
// Mutable approach: threading state manually
class Stats {
    int processed = 0;
    int errors = 0;
    long totalBytes = 0;
}

void processFiles(List<Path> files, Stats stats) {
    for (Path file : files) {
        try {
            byte[] content = Files.readAllBytes(file);
            stats.totalBytes += content.length;
            process(content);
            stats.processed++;
        } catch (Exception e) {
            stats.errors++;
        }
    }
}
```

The mutation is scattered. Testing requires mutable fixtures. Parallelisation becomes dangerous. And the state threading is implicit; you have to trace through the code to understand how `stats` changes.

---

## The Solution

`MutableContext` makes state threading explicit and composable:

```java
record Stats(int processed, int errors, long totalBytes) {
    Stats incrementProcessed() { return new Stats(processed + 1, errors, totalBytes); }
    Stats incrementErrors() { return new Stats(processed, errors + 1, totalBytes); }
    Stats addBytes(long bytes) { return new Stats(processed, errors, totalBytes + bytes); }
}

MutableContext<IOKind.Witness, Stats, Unit> processFile(Path file) {
    return MutableContext.io(stats -> {
        try {
            byte[] content = Files.readAllBytes(file);
            return StateTuple.of(
                stats.addBytes(content.length).incrementProcessed(),
                Unit.INSTANCE);
        } catch (Exception e) {
            return StateTuple.of(stats.incrementErrors(), Unit.INSTANCE);
        }
    });
}

// Process all files
MutableContext<IOKind.Witness, Stats, Unit> processAll =
    files.stream()
        .map(this::processFile)
        .reduce(MutableContext.pure(Unit.INSTANCE),
            (a, b) -> a.then(() -> b));

Stats finalStats = processAll.execWith(new Stats(0, 0, 0)).unsafeRun();
```

State changes are explicit. Each operation declares how it modifies state. The flow is clear.

---

## Creating MutableContexts

### io: State Transformation with Value

The core factory creates a context from a function `S -> StateTuple<S, A>`:

```java
record Counter(int value) {
    Counter increment() { return new Counter(value + 1); }
}

MutableContext<IOKind.Witness, Counter, String> getAndIncrement =
    MutableContext.io(counter -> StateTuple.of(
        counter.increment(),           // New state
        "Was: " + counter.value()      // Produced value
    ));
```

### get: Read Current State

`get()` yields the current state as the value without modifying it:

```java
MutableContext<IOKind.Witness, Counter, Counter> current = MutableContext.get();

// Often followed by map to extract what you need
MutableContext<IOKind.Witness, Counter, Integer> currentValue =
    MutableContext.<Counter>get()
        .map(Counter::value);
```

### put: Replace State Entirely

`put()` sets a new state, returning `Unit`:

```java
MutableContext<IOKind.Witness, Counter, Unit> reset =
    MutableContext.put(new Counter(0));
```

### modify: Update State

`modify()` applies a transformation to the current state:

```java
MutableContext<IOKind.Witness, Counter, Unit> increment =
    MutableContext.modify(Counter::increment);

MutableContext<IOKind.Witness, Counter, Unit> addFive =
    MutableContext.modify(c -> new Counter(c.value() + 5));
```

### pure: Value Without State Change

For values that don't affect state:

```java
MutableContext<IOKind.Witness, AnyState, String> constant =
    MutableContext.pure("Hello");
```

---

## Transforming Values

### map: Transform the Result

```java
MutableContext<IOKind.Witness, Counter, Integer> count =
    MutableContext.<Counter>get()
        .map(Counter::value);

MutableContext<IOKind.Witness, Counter, String> countStr =
    count.map(n -> "Count: " + n);
```

`map` transforms the value; the state flows through unchanged by the transformation itself.

---

## Chaining Stateful Operations

### via / flatMap: Sequence State Changes

Each operation sees the state left by previous operations:

```java
MutableContext<IOKind.Witness, Counter, String> workflow =
    MutableContext.<Counter>get()                              // Read initial state
        .map(c -> "Started at " + c.value())
        .flatMap(msg -> MutableContext.<Counter, Unit>modify(Counter::increment)
            .map(u -> msg))                                     // State now incremented
        .flatMap(msg -> MutableContext.<Counter>get()
            .map(c -> msg + ", now at " + c.value()));          // See updated state
```

### then: Sequence Ignoring Values

When you only care about the state effects:

```java
MutableContext<IOKind.Witness, Counter, Unit> incrementThrice =
    MutableContext.<Counter, Unit>modify(Counter::increment)
        .then(() -> MutableContext.modify(Counter::increment))
        .then(() -> MutableContext.modify(Counter::increment));
```

### Pattern: Accumulator

```java
record Accumulator(List<String> items) {
    Accumulator add(String item) {
        var newItems = new ArrayList<>(items);
        newItems.add(item);
        return new Accumulator(List.copyOf(newItems));
    }
}

MutableContext<IOKind.Witness, Accumulator, Unit> collect(String item) {
    return MutableContext.modify(acc -> acc.add(item));
}

MutableContext<IOKind.Witness, Accumulator, List<String>> collectAll =
    collect("first")
        .then(() -> collect("second"))
        .then(() -> collect("third"))
        .then(() -> MutableContext.<Accumulator>get().map(Accumulator::items));

List<String> items = collectAll.evalWith(new Accumulator(List.of())).unsafeRun();
// ["first", "second", "third"]
```

---

## Execution

`MutableContext` offers three ways to run, depending on what you need:

### runWith: Get Both State and Value

Returns `IOPath<StateTuple<S, A>>`:

```java
MutableContext<IOKind.Witness, Counter, String> workflow = ...;

IOPath<StateTuple<Counter, String>> ioPath = workflow.runWith(new Counter(0));
StateTuple<Counter, String> result = ioPath.unsafeRun();

Counter finalState = result.state();   // The final state
String value = result.value();          // The produced value
```

### evalWith: Get Only the Value

When you don't need the final state:

```java
IOPath<String> valueIO = workflow.evalWith(new Counter(0));
String value = valueIO.unsafeRun();
```

### execWith: Get Only the Final State

When you only care about the accumulated state:

```java
IOPath<Counter> stateIO = workflow.execWith(new Counter(0));
Counter finalState = stateIO.unsafeRun();
```

---

## Real-World Patterns

### Request ID Generation

```java
record IdState(long nextId) {
    IdState advance() { return new IdState(nextId + 1); }
}

MutableContext<IOKind.Witness, IdState, Long> generateId() {
    return MutableContext.io(state -> StateTuple.of(
        state.advance(),
        state.nextId()
    ));
}

MutableContext<IOKind.Witness, IdState, Request> tagRequest(Request req) {
    return generateId().map(id -> req.withId(id));
}

// Process multiple requests, each getting unique ID
MutableContext<IOKind.Witness, IdState, List<Request>> tagAll(List<Request> requests) {
    return requests.stream()
        .map(this::tagRequest)
        .reduce(
            MutableContext.pure(List.<Request>of()),
            (accCtx, reqCtx) -> accCtx.flatMap(list ->
                reqCtx.map(req -> {
                    var newList = new java.util.ArrayList<>(list);
                    newList.add(req);
                    return List.copyOf(newList);
                }))
        );
}

List<Request> tagged = tagAll(requests).evalWith(new IdState(1000)).unsafeRun();
```

### Processing Statistics

```java
record ProcessingStats(int success, int failure, Duration totalTime) {
    ProcessingStats recordSuccess(Duration d) {
        return new ProcessingStats(success + 1, failure, totalTime.plus(d));
    }
    ProcessingStats recordFailure() {
        return new ProcessingStats(success, failure + 1, totalTime);
    }
}

MutableContext<IOKind.Witness, ProcessingStats, Result> processWithStats(Item item) {
    return MutableContext.io(stats -> {
        Instant start = Instant.now();
        try {
            Result result = processor.process(item);
            Duration elapsed = Duration.between(start, Instant.now());
            return StateTuple.of(stats.recordSuccess(elapsed), result);
        } catch (Exception e) {
            return StateTuple.of(stats.recordFailure(), Result.failed(e));
        }
    });
}
```

### State Machine

```java
sealed interface GameState {
    record WaitingForPlayers(int count) implements GameState {}
    record InProgress(int round) implements GameState {}
    record Finished(String winner) implements GameState {}
}

MutableContext<IOKind.Witness, GameState, Unit> addPlayer() {
    return MutableContext.modify(state -> switch (state) {
        case GameState.WaitingForPlayers(var count) ->
            new GameState.WaitingForPlayers(count + 1);
        case GameState.InProgress _, GameState.Finished _ -> state;  // No-op
    });
}

MutableContext<IOKind.Witness, GameState, Unit> startGame() {
    return MutableContext.modify(state -> switch (state) {
        case GameState.WaitingForPlayers(var count) when count >= 2 ->
            new GameState.InProgress(1);
        case GameState.WaitingForPlayers _, GameState.InProgress _,
             GameState.Finished _ -> state;
    });
}

MutableContext<IOKind.Witness, GameState, Unit> advanceRound() {
    return MutableContext.modify(state -> switch (state) {
        case GameState.InProgress(var round) when round < 10 ->
            new GameState.InProgress(round + 1);
        case GameState.InProgress(var round) ->
            new GameState.Finished("Player 1");  // End after 10 rounds
        case GameState.WaitingForPlayers _, GameState.Finished _ -> state;
    });
}
```

### Combining with Other Contexts

```java
// Stateful computation that might fail
MutableContext<IOKind.Witness, Counter, ErrorContext<IOKind.Witness, String, Data>>
    fetchWithCounter() {
    return MutableContext.<Counter, Unit>modify(Counter::increment)
        .map(u -> ErrorContext.<String, Data>io(
            () -> dataService.fetch(),
            Throwable::getMessage));
}
```

---

## Escape Hatch

When you need the raw transformer:

```java
MutableContext<IOKind.Witness, Counter, Integer> ctx =
    MutableContext.<Counter>get().map(Counter::value);

StateT<Counter, IOKind.Witness, Integer> transformer = ctx.toStateT();
```

---

## Summary

| Operation | Purpose | Returns |
|-----------|---------|---------|
| `io(s -> StateTuple.of(newS, value))` | State transformation with value | `MutableContext<F, S, A>` |
| `get()` | Read current state as value | `MutableContext<F, S, S>` |
| `put(newState)` | Replace state entirely | `MutableContext<F, S, Unit>` |
| `modify(s -> newS)` | Transform current state | `MutableContext<F, S, Unit>` |
| `pure(value)` | Value without state change | `MutableContext<F, S, A>` |
| `map(f)` | Transform the value | `MutableContext<F, S, B>` |
| `via(f)` / `flatMap(f)` | Chain with state threading | `MutableContext<F, S, B>` |
| `runWith(initialState)` | Get both state and value | `IOPath<StateTuple<S, A>>` |
| `evalWith(initialState)` | Get only the value | `IOPath<A>` |
| `execWith(initialState)` | Get only the final state | `IOPath<S>` |

`MutableContext` reconciles the intuition of stateful programming with the safety of pure functions. You write code that reads state, updates state, and produces values, but the state never mutates in place. Each step produces a new state, and the transformation is explicit. The stone you find isn't the stone you started with, but you can trace every change that got you there.

~~~admonish tip title="See Also"
- [StateT Transformer](../transformers/statet_transformer.md) - The underlying transformer
- [State Monad](../monads/state_monad.md) - The State type
- [Advanced Effects](advanced_effects.md) - StatePath for simpler State usage
~~~

---

**Previous:** [ConfigContext](effect_contexts_config.md)
**Next:** [RequestContext](context_request.md)
