# Migration Cookbook

Side-by-side translations from imperative Java and the Effect Path API into raw monad transformers. Use this page when you have a working solution in one form and need to express it as a transformer stack, typically because you need a different outer monad or because you are writing polymorphic library code.

~~~admonish info title="What You'll Learn"
- Recipes for moving from nested `thenCompose` chains to `EitherT`, `OptionalT`, and `MaybeT`
- How to migrate manual configuration threading to `ReaderT`
- How to migrate manual log threading to `WriterT`
- How to migrate manual state threading to `StateT`
- When the Effect Path API is the better destination and when the raw transformer is unavoidable
~~~

~~~admonish note title="Path First, Stack Later"
Most readers should migrate to the [Effect Path API](../effect/ch_intro.md) first. The Path types wrap these transformers in a fluent interface that hides the witness types and `Kind` widening. Reach for the raw transformer only when you need a different outer monad (`CompletableFuture`, `IO`, custom) or when you are writing polymorphic code that names an MTL capability.
~~~

---

## Imperative to Transformer

### Recipe 1: Nested `thenCompose` with `Either`

**The problem:** an async pipeline whose steps can fail with typed domain errors. Without a transformer, every step needs a manual `Either.fold` to propagate the error.

**Before:**

```java
CompletableFuture<Either<DomainError, Receipt>> processOrder(OrderData data) {
    return validateOrder(data).thenCompose(eitherValidated ->
        eitherValidated.fold(
            error -> CompletableFuture.completedFuture(Either.left(error)),
            validated -> checkInventory(validated).thenCompose(eitherInventory ->
                eitherInventory.fold(
                    error -> CompletableFuture.completedFuture(Either.left(error)),
                    inventory -> processPayment(inventory)))));
}
```

**After (Effect Path API):**

```java
EitherPath<DomainError, Receipt> processOrder(OrderData data) {
    return Path.either(validateOrder(data))
        .via(validated -> Path.either(checkInventory(validated)))
        .via(inventory -> Path.either(processPayment(inventory)));
}
```

**After (raw `EitherT`, when you must keep `CompletableFuture` as the outer monad):**

```java
var eitherTMonad = new EitherTMonad<CompletableFutureKind.Witness, DomainError>(futureMonad);

var workflow = For.from(eitherTMonad, EitherT.fromKind(validateOrder(data)))
    .from(validated -> EitherT.fromKind(checkInventory(validated)))
    .from(inventory -> EitherT.fromKind(processPayment(inventory)))
    .yield((v, i, r) -> r);
```

**Why this works:** `EitherT` runs each `flatMap` inside the outer monad and routes on the inner `Either`. A `Left` short-circuits the rest of the comprehension; a `Right` feeds into the next step.

---

### Recipe 2: Nested `Optional` lookups

**The problem:** chained lookups where any step might return nothing. Manual nesting forces an `orElse(CompletableFuture.completedFuture(Optional.empty()))` at every level.

**Before:**

```java
CompletableFuture<Optional<UserPreferences>> getPreferences(String userId) {
    return fetchUserAsync(userId).thenCompose(optUser ->
        optUser.map(user ->
            fetchProfileAsync(user.id()).thenCompose(optProfile ->
                optProfile.map(profile ->
                    fetchPrefsAsync(profile.userId())
                ).orElse(CompletableFuture.completedFuture(Optional.empty()))
            )
        ).orElse(CompletableFuture.completedFuture(Optional.empty())));
}
```

**After (Effect Path API):**

```java
OptionalPath<UserPreferences> getPreferences(String userId) {
    return Path.optional(fetchUserAsync(userId))
        .via(user -> Path.optional(fetchProfileAsync(user.id())))
        .via(profile -> Path.optional(fetchPrefsAsync(profile.userId())));
}
```

**After (raw `OptionalT`):**

```java
var optionalTMonad = new OptionalTMonad<CompletableFutureKind.Witness>(futureMonad);

var prefsLookup = For.from(optionalTMonad, OptionalT.fromKind(fetchUserAsync(userId)))
    .from(user    -> OptionalT.fromKind(fetchProfileAsync(user.id())))
    .from(profile -> OptionalT.fromKind(fetchPrefsAsync(profile.userId())))
    .yield((user, profile, prefs) -> prefs);
```

**Why this works:** an empty `Optional` short-circuits the rest of the chain. The transformer hides the per-step fallback wiring.

---

### Recipe 3: Threading configuration through every signature

**The problem:** every function in a chain needs the same `AppConfig`. The parameter clutters every signature and every call site.

**Before:**

```java
CompletableFuture<ServiceData> fetchData(AppConfig config, String itemId) { ... }
CompletableFuture<ProcessedData> processData(AppConfig config, ServiceData data) { ... }

CompletableFuture<ProcessedData> workflow(AppConfig config) {
    return fetchData(config, "item-123")
        .thenCompose(data -> processData(config, data));
}
```

**After (Effect Path API):**

```java
ReaderPath<AppConfig, ProcessedData> workflow() {
    return Path.<AppConfig>ask()
        .via(config -> Path.right(fetchData(config, "item-123")))
        .via(data   -> Path.<AppConfig>ask().map(config -> processData(config, data)));
}
```

**After (raw `ReaderT`, when you must combine the environment with `CompletableFuture`):**

```java
var readerT = new ReaderTMonad<CompletableFutureKind.Witness, AppConfig>(futureMonad);

ReaderT<CompletableFutureKind.Witness, AppConfig, ServiceData>
    fetchDataRT(String itemId) {
  return ReaderT.of(config -> FUTURE.widen(
      CompletableFuture.supplyAsync(() -> callApi(config.apiKey(), itemId))));
}

ReaderT<CompletableFutureKind.Witness, AppConfig, ProcessedData>
    processDataRT(ServiceData data) {
  return ReaderT.reader(futureMonad, config -> transform(data, config));
}
```

**Why this works:** `ReaderT` wraps the function `R -> Kind<F, A>`. The environment threads through `flatMap` automatically, and you supply it once when you call `.run().apply(config)`.

---

### Recipe 4: Manual log threading

**The problem:** every step in a pipeline must record an audit entry. Without a transformer the log either lives as a mutable side channel (easy to forget) or threads through every signature alongside the value.

**Before:**

```java
Pair<BigDecimal, List<String>> applyDiscount(BigDecimal price, List<String> log) {
    var newLog = new ArrayList<>(log);
    newLog.add("Applied 10% discount");
    return Pair.of(price.multiply(new BigDecimal("0.9")), newLog);
}

Pair<BigDecimal, List<String>> addShipping(BigDecimal price, List<String> log) {
    var newLog = new ArrayList<>(log);
    newLog.add("Added shipping");
    return Pair.of(price.add(new BigDecimal("5.00")), newLog);
}
```

**After (Effect Path API):**

```java
WriterPath<List<AuditEntry>, BigDecimal> workflow(BigDecimal price) {
    return WriterPath.<List<AuditEntry>, BigDecimal>writer(
            price.multiply(new BigDecimal("0.9")),
            List.of(new AuditEntry("Applied 10% discount")),
            auditMonoid)
        .via(discounted -> WriterPath.writer(
            discounted.add(new BigDecimal("5.00")),
            List.of(new AuditEntry("Added shipping")),
            auditMonoid));
}
```

**After (raw `WriterT`):**

```java
var listMonoid  = Monoids.list();
var writerMonad = new WriterTMonad<IdKind.Witness, List<String>>(IdMonad.instance(), listMonoid);

var workflow = For.from(writerMonad, writerMonad.tell(List.of("Applied 10% discount")))
    .from(_ -> writerMonad.of(new BigDecimal("90.00")))
    .from(p -> writerMonad.tell(List.of("Added shipping")))
    .yield((_, price, _) -> price.add(new BigDecimal("5.00")));
```

**Why this works:** the `Monoid` combines successive logs automatically; `flatMap` does the rest. No step can forget to thread the log forward.

---

### Recipe 5: Manual state threading

**The problem:** stateful operations where the state is also wrapped in another effect. Manual threading makes it easy to use stale state and easy to forget the optionality / error layer.

**Before:**

```java
Optional<StateTuple<List<Integer>, Integer>> workflow(List<Integer> initial) {
    var afterPush1 = push(initial, 10);
    var afterPush2 = push(afterPush1.state(), 20);
    var pop1 = pop(afterPush2.state());
    if (pop1.isEmpty()) return Optional.empty();
    var pop2 = pop(pop1.get().state());
    if (pop2.isEmpty()) return Optional.empty();
    int sum = pop1.get().value() + pop2.get().value();
    return Optional.of(StateTuple.of(pop2.get().state(), sum));
}
```

**After (Effect Path API, when no other effect is required):**

```java
WithStatePath<List<Integer>, Integer> workflow() {
    return WithStatePath.<List<Integer>>modify(s -> prepend(s, 10))
        .then(() -> WithStatePath.modify(s -> prepend(s, 20)))
        .then(() -> WithStatePath.<List<Integer>>get())
        .map(state -> state.get(0) + state.get(1));
}
```

**After (raw `StateT`, when state must combine with another effect):**

```java
var stateTMonad = StateTMonad.<List<Integer>, OptionalKind.Witness>instance(OptionalMonad.INSTANCE);

var workflow = For.from(stateTMonad, push(10))
    .from(_ -> push(20))
    .from(_ -> pop())
    .from(_ -> pop())
    .yield((_, _, p1, p2) -> p1 + p2);
```

**Why this works:** `StateT` threads the updated state into the next step automatically. The outer `Optional` short-circuits the whole computation when `pop` fails.

---

## From Effect Path API to Transformer

The Path types are the recommended starting point. You only drop down to a raw transformer when one of these signals applies:

- **The outer monad is wrong.** Path types choose their outer monad for you. If your stack needs to live inside `CompletableFuture`, `IO`, `VTask`, or a third-party monad that Path does not wrap, the transformer gives you that choice.
- **You are writing a polymorphic library.** Code that names an MTL capability (`MonadReader`, `MonadState`, `MonadWriter`) works with any stack the caller provides. Path types are concrete and do not abstract over the outer monad.
- **You need to interoperate with `Kind<F, ...>` directly.** If a downstream API or type class instance already exposes `Kind`, the transformer's `Kind` form is the natural meeting point.

If none of these apply, stay with the Path type.

### Path-to-Transformer Translation Table

| Path Operation | Transformer Equivalent |
|----------------|------------------------|
| `Path.right(value)`              | `EitherT.right(monad, value)` |
| `Path.left(error)`               | `EitherT.left(monad, error)` |
| `Path.maybe(value)` (`Just`)     | `MaybeT.just(monad, value)` |
| `Path.maybe(value)` (`Nothing`)  | `MaybeT.nothing(monad)` |
| `Path.optional(opt)`             | `OptionalT.fromOptional(monad, opt)` |
| `Path.<R>ask()`                  | `ReaderT.ask(monad)` |
| `path.via(f)`                    | `monad.flatMap(f, kind)` (typically inside `For`) |
| `path.map(f)`                    | `monad.map(f, kind)` |
| `path.recoverWith(f)`            | `monad.handleErrorWith(kind, f)` |
| `WithStatePath.modify(f)`        | `StateT.create(s -> ..., monad)` |
| `WriterPath.writer(v, w, mon)`   | `WriterT.writer(monad, v, w)` |

---

## Migration Decision Tree

```
                   ┌────────────────────────────────────┐
                   │  Is the outer monad fixed and one  │
                   │  the Path API already wraps?       │
                   └──────────┬─────────────────────────┘
                              │
                  ┌───────────┴───────────┐
                  │                       │
                 Yes                      No
                  │                       │
                  ▼                       ▼
           Use the Path type     ┌────────────────────────┐
           directly.             │  Are you writing       │
                                 │  polymorphic library   │
                                 │  code?                 │
                                 └──────────┬─────────────┘
                                            │
                                ┌───────────┴───────────┐
                                │                       │
                               Yes                      No
                                │                       │
                                ▼                       ▼
                         Use the matching        Use the raw
                         MTL capability          transformer with
                         (MonadReader, ...).     For comprehension.
```

---

~~~admonish tip title="See Also"
- [Quickstart](quickstart.md) -- three runnable examples in 150 lines
- [Stack Archetypes](archetypes.md) -- named patterns for common composition problems
- [Effect Path Migration Cookbook](../effect/migration_cookbook.md) -- imperative-to-Path migrations (read this first)
- [MTL Capabilities](mtl_capabilities.md) -- when to write polymorphic code instead of using a concrete transformer
~~~

---

**Previous:** [Transformers at a Glance](transformers_at_a_glance.md)
**Next:** [Stack Archetypes](archetypes.md)
