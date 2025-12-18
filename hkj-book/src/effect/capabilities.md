# Capability Interfaces

> *"Caress the detail, the divine detail."*
>
> — Vladimir Nabokov

Nabokov was speaking of prose, but the principle applies to API design. The
Effect Path API doesn't give every Path type every operation. Instead, it
builds a hierarchy of **capabilities**, interfaces that add specific powers
to types that genuinely possess them. A `MaybePath` can recover from absence;
an `IdPath` cannot fail in the first place, so recovery would be meaningless.
The type system prevents you from reaching for tools that don't apply.

This isn't bureaucratic fastidiousness. It's how the library stays honest
about what each type can do.

~~~admonish info title="What You'll Learn"
- The capability interface hierarchy: Composable, Combinable, Chainable, Recoverable, Effectful, and Accumulating
- How each capability maps to functional programming concepts (Functor, Applicative, Monad, MonadError)
- Which operations each capability provides
- Which Path types implement which capabilities
- Why the layering matters for code that composes correctly
~~~

---

## The Hierarchy

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     CAPABILITY HIERARCHY                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                          ┌────────────────┐                                  │
│                          │   Composable   │  map(), peek()                   │
│                          │   (Functor)    │  "I can transform what's inside" │
│                          └───────┬────────┘                                  │
│                                  │                                           │
│                          ┌───────┴────────┐                                  │
│                          │   Combinable   │  zipWith(), map2()               │
│                          │ (Applicative)  │  "I can merge independent work"  │
│                          └───────┬────────┘                                  │
│                                  │                                           │
│                          ┌───────┴────────┐                                  │
│                          │   Chainable    │  via(), flatMap(), then()        │
│                          │    (Monad)     │  "I can sequence dependent work" │
│                          └───────┬────────┘                                  │
│                                  │                                           │
│      ┌───────────────────────────┼───────────────────────────┐               │
│      │                           │                           │               │
│ ┌────┴───────┐           ┌───────┴────────┐          ┌───────┴────────┐      │
│ │ Recoverable│           │   Effectful    │          │  Accumulating  │      │
│ │(MonadError)│           │     (IO)       │          │  (Validated)   │      │
│ │            │           │                │          │                │      │
│ │ "I can     │           │ "I defer until │          │ "I collect all │      │
│ │  handle    │           │  you're ready" │          │  the problems" │      │
│ │  failure"  │           │                │          │                │      │
│ └────────────┘           └────────────────┘          └────────────────┘      │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

Each level builds on the previous. A `Chainable` type can do everything a
`Combinable` can do, plus sequential chaining. The three leaf capabilities,
Recoverable, Effectful, and Accumulating, represent specialised powers that
only some types possess.

This isn't arbitrary taxonomy. It's how you avoid calling `recover()` on a
type that never fails, or `unsafeRun()` on a type that doesn't defer execution.
The compiler catches category errors before they become runtime surprises.

---

## Composable (Functor)

**What it means:** You can transform the value inside without changing the
surrounding structure.

**The analogy:** A translator. The message changes; the envelope stays sealed.

```java
public interface Composable<A> {
    <B> Composable<B> map(Function<? super A, ? extends B> f);
    Composable<A> peek(Consumer<? super A> action);
}
```

Every Path type is Composable. It's the minimum viable capability.

```java
MaybePath<String> name = Path.just("alice");
MaybePath<Integer> length = name.map(String::length);  // Just(5)

MaybePath<String> empty = Path.nothing();
MaybePath<Integer> stillEmpty = empty.map(String::length);  // Nothing
```

The function passed to `map` only runs if there's a value to transform.
Failures, absences, and pending effects pass through unchanged. This is why
you don't need defensive null checks inside `map`; the structure handles it.

### The Functor Laws

All Path types satisfy these laws, which is what makes composition predictable:

1. **Identity:** `path.map(x -> x)` equals `path`
2. **Composition:** `path.map(f).map(g)` equals `path.map(f.andThen(g))`

The first law says mapping with the identity function changes nothing. The
second says you can fuse consecutive maps into one. These aren't aspirational
guidelines; they're guarantees the implementation must honour.

---

## Combinable (Applicative)

**What it means:** You can merge the results of independent computations.

**The analogy:** A meeting coordinator. Everyone works separately, then results
are combined at the end. If someone fails to deliver, there's nothing to combine.

```java
public interface Combinable<A> extends Composable<A> {
    <B, C> Combinable<C> zipWith(
        Combinable<B> other,
        BiFunction<? super A, ? super B, ? extends C> f
    );

    <B, C, D> Combinable<D> zipWith3(
        Combinable<B> second,
        Combinable<C> third,
        TriFunction<? super A, ? super B, ? super C, ? extends D> f
    );
}
```

The key property is **independence**. Neither computation depends on the
other's result:

```java
// These validations don't affect each other
EitherPath<String, String> name = validateName(input.name());
EitherPath<String, String> email = validateEmail(input.email());
EitherPath<String, Integer> age = validateAge(input.age());

// Combine all three
EitherPath<String, User> user = name.zipWith3(email, age, User::new);
```

If all three succeed, `User::new` receives the three values. If any fails,
the first failure propagates. (For collecting *all* failures, you need
`Accumulating`; patience.)

### `zipWith` vs `via`

A common source of confusion, worth addressing directly:

| Operation | Relationship Between Computations |
|-----------|-----------------------------------|
| `zipWith` | Independent: neither needs the other's result |
| `via` | Dependent: the second needs the first's result |

If you're validating a form, the fields are independent: use `zipWith`. If
you're fetching a user then loading their preferences, the second needs the
first: use `via`.

---

## Chainable (Monad)

**What it means:** You can sequence computations where each step depends on
the previous result.

**The analogy:** A relay race. Each runner receives the baton from the previous
and decides what to do next. If someone drops the baton, the race ends there.

```java
public interface Chainable<A> extends Combinable<A> {
    <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);
    <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> f);
    <B> Chainable<B> then(Supplier<? extends Chainable<B>> next);
}
```

The `via` method is the workhorse:

```java
EitherPath<Error, Invoice> invoice =
    Path.either(findUser(userId))
        .via(user -> Path.either(getCart(user)))      // needs user
        .via(cart -> Path.either(calculateTotal(cart))) // needs cart
        .via(total -> Path.either(createInvoice(total))); // needs total
```

Each step receives the previous result and returns a new Path. The railway
metaphor applies: success continues forward, failure short-circuits to the end.

`flatMap` is an alias for `via`, the same operation with the traditional name. Use
whichever reads better in context.

`then` is for sequencing when you don't need the previous value:

```java
IOPath<Unit> workflow =
    Path.io(() -> log.info("Starting"))
        .then(() -> Path.io(() -> initialise()))
        .then(() -> Path.io(() -> process()));
```

### The Monad Laws

1. **Left Identity:** `Path.just(a).via(f)` equals `f.apply(a)`
2. **Right Identity:** `path.via(Path::just)` equals `path`
3. **Associativity:** `path.via(f).via(g)` equals `path.via(x -> f.apply(x).via(g))`

These ensure that chaining behaves predictably regardless of how you group
operations. Refactoring a chain into helper methods won't change its behaviour.

---

## Recoverable (MonadError)

**What it means:** You can handle failures and potentially continue on the
success track.

**The analogy:** A safety net. If you fall, something catches you. You might
climb back up, or you might stay down, but the fall doesn't have to be fatal.

```java
public interface Recoverable<E, A> extends Chainable<A> {
    Recoverable<E, A> recover(Function<? super E, ? extends A> handler);
    Recoverable<E, A> recoverWith(
        Function<? super E, ? extends Recoverable<E, A>> handler
    );
    Recoverable<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative);
    <F> Recoverable<F, A> mapError(Function<? super E, ? extends F> f);
}
```

Different Path types have different notions of "error":

```java
// MaybePath: "error" is absence
MaybePath<User> user = Path.maybe(findUser(id))
    .orElse(() -> Path.just(User.guest()));

// EitherPath: "error" is a typed value
EitherPath<Error, Config> config = Path.either(loadConfig())
    .recover(error -> Config.defaults())
    .mapError(e -> new ConfigError("Load failed", e));

// TryPath: "error" is an exception
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input))
    .recover(ex -> 0);
```

`Recoverable` is perhaps the most frequently used capability after `Chainable`,
which tells you something about the general state of affairs in software.

---

## Effectful (IO)

**What it means:** The computation is deferred until you explicitly run it.

**The analogy:** A written contract. It describes what will happen, but nothing
happens until someone signs and executes it.

```java
public interface Effectful<A> extends Chainable<A> {
    A unsafeRun();
    Try<A> runSafe();
    Effectful<A> handleError(Function<? super Throwable, ? extends A> handler);
    Effectful<A> handleErrorWith(
        Function<? super Throwable, ? extends Effectful<A>> handler
    );
    Effectful<A> ensuring(Runnable cleanup);
}
```

Only `IOPath` implements `Effectful`. All other Path types evaluate immediately
when you call `map` or `via`. With `IOPath`, nothing happens until you call
`unsafeRun()` or `runSafe()`:

```java
IOPath<String> readFile = Path.io(() -> {
    System.out.println("Reading file...");  // Not printed yet
    return Files.readString(path);
});

// Still nothing happens
IOPath<Integer> lineCount = readFile.map(s -> s.split("\n").length);

// NOW it executes
Integer count = lineCount.unsafeRun();  // "Reading file..." printed
```

The `ensuring` method guarantees cleanup runs regardless of success or failure:

```java
IOPath<Data> withCleanup = Path.io(() -> acquireResource())
    .via(resource -> Path.io(() -> useResource(resource)))
    .ensuring(() -> releaseResource());
```

The name `unsafeRun` is deliberate. It's a warning: side effects are about to
happen, referential transparency ends here. Call it at the edge of your system,
not scattered throughout.

---

## Accumulating (Validated)

**What it means:** You can combine independent computations while collecting
*all* errors, not just the first.

**The analogy:** A code review. The reviewer notes every problem, then hands
back the full list. They don't stop at the first issue and declare the review
complete.

```java
public interface Accumulating<E, A> extends Composable<A> {
    <B, C> Accumulating<E, C> zipWithAccum(
        Accumulating<E, B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner
    );

    Accumulating<E, A> andAlso(Accumulating<E, ?> other);
}
```

Only `ValidationPath` implements `Accumulating`. The key difference from
`Combinable.zipWith`:

| Operation | On Multiple Failures |
|-----------|---------------------|
| `zipWith` | Returns first error (short-circuits) |
| `zipWithAccum` | Combines all errors using Semigroup |

```java
ValidationPath<List<String>, String> name = validateName(input);
ValidationPath<List<String>, String> email = validateEmail(input);
ValidationPath<List<String>, Integer> age = validateAge(input);

// Accumulate ALL errors
ValidationPath<List<String>, User> user = name.zipWith3Accum(
    email,
    age,
    User::new
);

// If name and email both fail: Invalid(["Name too short", "Invalid email"])
// Not just: Invalid(["Name too short"])
```

Error accumulation requires a `Semigroup` to define how errors combine. For
`List<String>`, errors concatenate. For `String`, they might join with `;`.
The Semigroup is provided when creating the `ValidationPath`.

Use `Accumulating` for user-facing validation where showing all problems at
once is kinder than making users fix them one by one.

---

## Which Capabilities Each Path Type Has

| Path Type | Composable | Combinable | Chainable | Recoverable | Effectful | Accumulating |
|-----------|:----------:|:----------:|:---------:|:-----------:|:---------:|:------------:|
| MaybePath | ✓ | ✓ | ✓ | ✓ | · | · |
| EitherPath | ✓ | ✓ | ✓ | ✓ | · | · |
| TryPath | ✓ | ✓ | ✓ | ✓ | · | · |
| IOPath | ✓ | ✓ | ✓ | · | ✓ | · |
| ValidationPath | ✓ | ✓ | ✓ | ✓ | · | ✓ |
| IdPath | ✓ | ✓ | ✓ | · | · | · |
| OptionalPath | ✓ | ✓ | ✓ | ✓ | · | · |
| GenericPath | ✓ | ✓ | ✓ | * | · | · |

\* `GenericPath` recovery depends on the underlying monad.

Note that `IdPath` lacks `Recoverable`; it cannot fail, so recovery is
meaningless. `IOPath` lacks `Recoverable` but has `Effectful`, which includes
its own error handling via `handleError`. These aren't omissions; they're
the type system being honest about what makes sense.

---

## Summary

| Capability | What It Adds | Key Operations |
|------------|--------------|----------------|
| **Composable** | Transform values | `map`, `peek` |
| **Combinable** | Merge independent work | `zipWith`, `zipWith3` |
| **Chainable** | Sequence dependent work | `via`, `flatMap`, `then` |
| **Recoverable** | Handle failure | `recover`, `recoverWith`, `mapError`, `orElse` |
| **Effectful** | Defer execution | `unsafeRun`, `runSafe`, `handleError`, `ensuring` |
| **Accumulating** | Collect all errors | `zipWithAccum`, `andAlso` |

The hierarchy is designed so you can write code at the appropriate level of
abstraction. If `map` suffices, use `map`. Reach for `via` only when you need
sequencing. The capabilities tell you what's available; the types ensure you
don't ask for more than a Path can deliver.

Continue to [Path Types](path_types.md) for detailed coverage of each type.

~~~admonish tip title="See Also"
- [Functor](../functional/functor.md) - The type class behind Composable
- [Applicative](../functional/applicative.md) - The type class behind Combinable
- [Monad](../functional/monad.md) - The type class behind Chainable
- [MonadError](../functional/monad_error.md) - The type class behind Recoverable
- [Validated](../monads/validated_monad.md) - The type behind Accumulating
~~~

~~~admonish tip title="Further Reading"
- **Mateusz Kubuszok**: [The F-words: Functors and Friends](https://kubuszok.com/2018/the-f-words-functors-and-friends/#functor) - An accessible introduction to Functor, Applicative, and Monad with practical examples
~~~

---

**Previous:** [Effect Path Overview](effect_path_overview.md)
**Next:** [Path Types](path_types.md)
