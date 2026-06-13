# Lifting the Hood

> _"All happy families are alike; each unhappy family is unhappy in its own way."_
>
> – Leo Tolstoy, *Anna Karenina*

---

We have been calling `.flatMap(...)` on `MaybePath`, `EitherPath`, and friends for chapters now. Time to follow one such call through every layer it touches and watch the machinery do its job.

This page is the engine-room tour. Nothing new is introduced; everything below is already documented elsewhere. What is new is the order: we trace one expression top to bottom rather than reading each layer in isolation.

~~~admonish info title="What We'll Learn"
- Exactly what runs when we call `.flatMap` on a `Kind`-shaped value
- Where `widen` and `narrow` actually fire, and what they cost
- How the right `Monad` instance is chosen at compile time
- Where a `Holder` does or does not appear in the chain
~~~

---

## The Expression We Are Tracing

```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

MonadError<EitherKind.Witness<String>, String> monad = Instances.monadError(either());

Either<String, Integer> start = Either.right(10);
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(start);

Kind<EitherKind.Witness<String>, String> result =
    monad.flatMap(i -> EITHER.widen(Either.right("v=" + i)), kind);

Either<String, String> finalEither = EITHER.narrow(result);
// Right("v=10")
```

Five lines, four method calls that matter. We will follow each in turn.

---

## Step 1: `EITHER.widen(start)`

```
   user code                              library

   Either.right(10)                       (already an Either)
        │
        ▼
   EITHER.widen(start) ──────▶  EitherKindHelper.widen
                                       │
                                       │ null check
                                       │ (Either<L,R> already implements
                                       │  EitherKind<L,R> which extends Kind)
                                       │
                                       ▼
                                  return start (typed cast)
        │
        ▼
   Kind<EitherKind.Witness<String>, Integer>
```

What actually happens at the bytecode level:

1. The helper checks the argument is non-null.
2. It returns the same reference, typed as `Kind<EitherKind.Witness<String>, Integer>` rather than `Either<String, Integer>`.

There is no allocation. The same `Right(10)` object that we created on the first line is the object that lives inside the `Kind`. If we sat at a debugger and inspected `kind.getClass()`, it would say `Either$Right`.

This is the path for *library types*, and it is now the path for **most** of them. Each HKJ-owned type declares itself as extending its own `Kind` interface — e.g. `Either<L, R> extends EitherKind<L, R>`, `Try<T> extends TryKind<T>`, `Reader<R, A> extends ReaderKind<R, A>` — so every value *is* a `Kind` already and `widen` is a cast-free, allocation-free upcast. This covers `Maybe`, `Either`, `Validated`, `Try`, `Id`, `Writer`, `Reader`, `State`, `Trampoline`, `Context`, `Free`, `IO`, `VTask`, `VStream` and more — including the `@FunctionalInterface` types `Reader` and `State`, whose lambdas are themselves `Kind` values (extending the empty `Kind` marker leaves them single-abstract-method). The relationship is compiler-checked: a new implementation that forgot it would not compile, rather than failing at runtime. The `Holder` record now survives in just two situations, both principled: JDK types like `Optional`, `List`, `Stream` and `CompletableFuture` that we cannot make implement our interfaces; and `Lazy`, whose memoising thunk is deliberately mutable and so is kept behind an immutable holder to honour the "`Kind` carriers are immutable" rule. We will see the `Optional` variant lower down.

`widen`/`narrow` remain the idiomatic boundary for *every* type — they read uniformly and validate their input, and they are the only option for the holder-backed types. The structural relationship above is an internal guarantee, not an invitation to drop `widen` for a favoured few.

---

## Step 2: `monad.flatMap(...)`

The compile-time picture:

```
   monad                                  Monad<EitherKind.Witness<String>>
        │
        │ at compile time, Java resolves this to EitherMonad.flatMap
        │ because monad is declared EitherMonad<String> (or assignable to it)
        │
        ▼
   EitherMonad.flatMap(f, kind)
```

`Monad<F>` is an interface. `EitherMonad<L>` is the concrete instance. The method dispatched here is `EitherMonad.flatMap`, and the compiler knew that the moment it saw `monad` typed as `EitherMonad<String>` (or `Monad<EitherKind.Witness<String>>`). No virtual lookup is interesting here, no reflection, no `Class` switching. The witness type acted as a key into the type-class instance, and the rest is ordinary Java method dispatch.

The runtime picture:

```
   EitherMonad.flatMap(f, kind):
       │
       │ EITHER.narrow(kind)        // back to Either<L, R>
       │     │
       │     │ instanceof Either check
       │     │ cast and return
       │     ▼
       │   Either<String, Integer>
       │
       │ either.flatMap(f)          // built-in Either.flatMap
       │     │
       │     │ if Right(v): apply f, expect Kind back
       │     │ if Left(e):  return same Left, no call
       │     ▼
       │   the new Kind that f produced (or the original Left)
       ▼
   return Kind<EitherKind.Witness<String>, String>
```

Three things worth marking on this picture:

1. **The narrow happens inside the type class, not in user code.** We never had to take the `Kind` apart manually. `EitherMonad.flatMap` does that internally so the body can call the real `Either.flatMap`.
2. **The function we passed (`i -> EITHER.widen(Either.right("v=" + i))`) is invoked once, on the right side, and returns a new `Kind`.** That new `Kind` becomes the output. If the input had been `Left("oops")`, our function would never run.
3. **No allocation for the `Kind` shell.** The return value is a `Right("v=10")`, typed as a `Kind`. Same trick as `widen`.

---

## Step 3: Inside the User Function

The lambda we wrote is `i -> EITHER.widen(Either.right("v=" + i))`. Tracing it:

```
   i = 10
        │
        ▼
   "v=" + i  →  "v=10"          // String concat, allocates one String
        │
        ▼
   Either.right("v=10")          // allocates one Right record
        │
        ▼
   EITHER.widen(...)             // null check + typed cast, no allocation
        │
        ▼
   Kind<EitherKind.Witness<String>, String>
```

Two allocations: the `String` and the `Right`. Both unavoidable; they are the *result* the caller asked for. The `Kind` part costs nothing on top.

---

## Step 4: `EITHER.narrow(result)`

The mirror of step 1:

```
   Kind<EitherKind.Witness<String>, String>
        │
        ▼
   EITHER.narrow(result) ──────▶  EitherKindHelper.narrow
                                       │
                                       │ null check
                                       │ instanceof Either check
                                       │ cast
                                       │
                                       ▼
                                  return result (as Either<String, String>)
        │
        ▼
   Either<String, String>
```

If we somehow handed `narrow` a `Kind` of the wrong shape (a `MaybeKind` masquerading, say), the `instanceof` check would fail and we would get a `KindUnwrapException`. In practice this never happens because the compiler enforces the witness types upstream; it is a defence-in-depth check for the rare case where someone bypasses the front door.

---

## Allocation Summary

For one `widen`, one `flatMap`, and one `narrow` against a library type:

| Step | Allocations |
|------|------------|
| `EITHER.widen(start)` | 0 |
| `monad.flatMap(...)` | depends entirely on `f`; the machinery itself allocates 0 |
| Inside `f`: `Right("v=" + i)` | 1 String + 1 Right record |
| `EITHER.narrow(result)` | 0 |

The library overhead, in the steady state, is essentially the `instanceof` cost of `narrow`. The JIT typically inlines through the entire chain when call sites are monomorphic, which they are when only one container type passes through them.

---

## What Changes for JDK Types?

For `Optional`, the picture differs at exactly two steps.

```
   Optional.of(10)
        │
        ▼
   OPTIONAL.widen(opt)
        │
        │ null check
        │ allocate one OptionalHolder record wrapping the Optional
        │
        ▼
   Kind<OptionalKind.Witness, Integer>      (it is a Holder)


   ...later...

   OPTIONAL.narrow(kind)
        │
        │ null check
        │ instanceof OptionalHolder
        │ extract the wrapped Optional
        │
        ▼
   Optional<Integer>
```

One extra allocation per `widen` (the `Holder`), one extra field read per `narrow`. Escape analysis often elides the `Holder` when the `Kind` does not leave the current method, but if it does, the cost is real and measurable.

That asymmetry is the tax for HKT-ifying a class we cannot modify. The fix is not technical; it is to use library types (`Maybe`, `EitherPath`) when the cost matters.

---

## What This Means in Practice

Three takeaways for everyday code:

1. **`widen`/`narrow` calls on library types are free.** Sprinkle them as required for clarity; do not contort the code to avoid them.
2. **`widen`/`narrow` calls on JDK types cost one tiny allocation each.** In a tight loop, prefer `Maybe`, `Either`, `Try`. In a Spring controller method that runs once per request, the cost is invisible.
3. **The type-class instance is selected at compile time.** There is no per-call lookup, no reflection, no megamorphic dispatch unless we are deliberately writing code that is generic over the witness type. The latter is occasionally the point, but it is not the default.

The library is built so that the *abstraction* lives in the type system and the *runtime* is essentially the same Java we would have written by hand, just with the boilerplate factored out.

---

~~~admonish tip title="See Also"
- [Core Concepts](core-concepts.md) - the components introduced here, in slower depth
- [Foundations FAQ](faq.md) - the design questions that this trace will prompt
- [Benchmarks & Performance](../benchmarks.md) - the actual numbers
~~~

---

**Previous:** [One Line, Six Layers](one_line_six_layers.md)
**Next:** [Higher-Kinded Types](ch_intro.md)
